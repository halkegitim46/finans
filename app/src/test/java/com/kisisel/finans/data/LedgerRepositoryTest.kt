package com.kisisel.finans.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kisisel.finans.data.local.KisiselFinansDatabase
import com.kisisel.finans.data.repository.LedgerRepositoryImpl
import com.kisisel.finans.data.repository.PersonRepositoryImpl
import com.kisisel.finans.domain.FinanceException
import com.kisisel.finans.domain.model.NewClaim
import com.kisisel.finans.domain.model.NewPerson
import com.kisisel.finans.domain.model.NewSettlement
import com.kisisel.finans.domain.model.PersonBalance
import com.kisisel.finans.domain.model.TransactionType.BORROW
import com.kisisel.finans.domain.model.TransactionType.COLLECTION
import com.kisisel.finans.domain.model.TransactionType.DEBT_PAYMENT
import com.kisisel.finans.domain.model.TransactionType
import com.kisisel.finans.domain.model.TransactionType.LEND
import com.kisisel.finans.domain.model.TransactionType.PAID_ON_BEHALF
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class LedgerRepositoryTest {

    private lateinit var db: KisiselFinansDatabase
    private lateinit var persons: PersonRepositoryImpl
    private lateinit var ledger: LedgerRepositoryImpl

    private val clock: Clock = Clock.fixed(Instant.parse("2026-11-02T09:00:00Z"), ZoneId.of("Europe/Istanbul"))
    private val day: LocalDate = LocalDate.of(2026, 11, 2)
    private var keySeq = 0

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, KisiselFinansDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        persons = PersonRepositoryImpl(db.personDao(), clock)
        ledger = LedgerRepositoryImpl(db, clock)
    }

    @After fun tearDown() {
        db.close()
    }

    // ---- yardımcılar ----
    private fun runDb(block: suspend () -> Unit) = runBlocking { block() }

    private fun key() = "k${keySeq++}"

    private suspend fun newPerson(name: String = "Dayım", opening: Long = 0) =
        persons.create(NewPerson(name, null, "", opening))

    private suspend fun claim(pid: Long, type: TransactionType, amount: Long, date: LocalDate = day) =
        ledger.recordClaim(NewClaim(pid, type, amount, date, null, "", key()))

    private suspend fun settle(pid: Long, type: TransactionType, amount: Long?) =
        ledger.settle(NewSettlement(pid, type, amount, day, "", key()))

    private suspend fun balance(pid: Long): PersonBalance = ledger.observePersonSummary(pid).first()!!.balance

    private suspend inline fun <reified E : Throwable> assertFails(noinline block: suspend () -> Unit): E {
        try {
            block()
        } catch (e: Throwable) {
            if (e is E) return e
            throw e
        }
        throw AssertionError("${E::class.java.simpleName} bekleniyordu")
    }

    // ---- testler ----
    @Test fun dayiElectricityFlow() = runDb {
        val p = newPerson()
        claim(p, PAID_ON_BEHALF, 150_000)
        val r = settle(p, COLLECTION, 50_000)

        assertEquals(50_000, r.allocatedKurus)
        val b = balance(p)
        assertEquals(150_000, b.totalReceivable)
        assertEquals(50_000, b.collected)
        assertEquals(100_000, b.remainingReceivable)
        assertEquals(50_000, db.transactionDao().getReceivableClaims(p).single().allocatedKurus)
    }

    @Test fun closeAccountSettlesWholeRemaining() = runDb {
        val p = newPerson()
        claim(p, PAID_ON_BEHALF, 150_000)
        settle(p, COLLECTION, 50_000)

        val r = settle(p, COLLECTION, null)
        assertEquals(100_000, r.amountKurus)
        assertEquals(0, balance(p).remainingReceivable)
        assertFails<FinanceException.NothingToSettle> { settle(p, COLLECTION, null) }
    }

    @Test fun cannotCollectMoreThanRemaining() = runDb {
        val p = newPerson()
        claim(p, LEND, 10_000)
        val e = assertFails<FinanceException.ExceedsRemaining> { settle(p, COLLECTION, 10_001) }
        assertEquals(10_000, e.remainingKurus)
        assertEquals(0, balance(p).collected)
    }

    @Test fun duplicateClaimWithSameKeyIsIgnored() = runDb {
        val p = newPerson()
        val cmd = NewClaim(p, LEND, 10_000, day, null, "", "same-key")
        val id1 = ledger.recordClaim(cmd)
        val id2 = ledger.recordClaim(cmd)
        assertEquals(id1, id2)
        assertEquals(10_000, balance(p).totalReceivable)
        assertEquals(1, ledger.observeEntries(p).first().size)
    }

    @Test fun duplicateSettlementWithSameKeyIsIgnored() = runDb {
        val p = newPerson()
        claim(p, LEND, 10_000)
        val cmd = NewSettlement(p, COLLECTION, 4_000, day, "", "settle-key")
        val r1 = ledger.settle(cmd)
        val r2 = ledger.settle(cmd)
        assertEquals(r1.transactionId, r2.transactionId)
        assertEquals(4_000, balance(p).collected)
    }

    @Test fun collectionsAreAllocatedOldestFirst() = runDb {
        val p = newPerson()
        claim(p, LEND, 10_000, LocalDate.of(2026, 10, 1))
        claim(p, LEND, 20_000, LocalDate.of(2026, 10, 15))
        val r = settle(p, COLLECTION, 15_000)

        assertEquals(15_000, r.allocatedKurus)
        val claims = db.transactionDao().getReceivableClaims(p)
        assertEquals(listOf(10_000L, 5_000L), claims.map { it.allocatedKurus })
    }

    @Test fun cancelRules() = runDb {
        val p = newPerson()
        val c = claim(p, LEND, 10_000)
        val s = settle(p, COLLECTION, 4_000)

        assertFails<FinanceException.HasActiveAllocations> { ledger.cancel(c) }

        ledger.cancel(s.transactionId)               // tahsilat iptal -> kalan geri gelir
        assertEquals(10_000, balance(p).remainingReceivable)
        assertEquals(0, db.transactionDao().getReceivableClaims(p).single().allocatedKurus)

        ledger.cancel(c)                              // artık iptal edilebilir
        assertEquals(0, balance(p).totalReceivable)
        assertFails<FinanceException.AlreadyCancelled> { ledger.cancel(c) }
        assertEquals(2, ledger.observeEntries(p).first().size) // kayıtlar silinmedi
    }

    @Test fun archivedPersonKeepsHistoryAndRejectsNewEntries() = runDb {
        val p = newPerson()
        claim(p, LEND, 10_000)
        persons.setArchived(p, true)

        assertFails<FinanceException.PersonArchived> { claim(p, LEND, 500) }
        assertEquals(1, ledger.observeEntries(p).first().size)
        assertEquals(10_000, balance(p).remainingReceivable)

        persons.setArchived(p, false)
        claim(p, LEND, 500)
        assertEquals(10_500, balance(p).remainingReceivable)
    }

    @Test fun personCanBeDebtorAndCreditor() = runDb {
        val p = newPerson()
        claim(p, LEND, 100_000)
        settle(p, COLLECTION, 20_000)
        claim(p, BORROW, 50_000)
        settle(p, DEBT_PAYMENT, 10_000)

        val b = balance(p)
        assertEquals(80_000, b.remainingReceivable)
        assertEquals(40_000, b.remainingPayable)
        assertEquals(40_000, b.net)
    }

    @Test fun openingBalanceIsSettledWithoutClaims() = runDb {
        val p = newPerson(opening = -25_000)
        val r = settle(p, DEBT_PAYMENT, 5_000)
        assertEquals(0, r.allocatedKurus)
        assertEquals(20_000, balance(p).remainingPayable)
        assertFails<FinanceException.ExceedsRemaining> { settle(p, DEBT_PAYMENT, 20_001) }
    }

    @Test fun updateDetailsKeepsAmount() = runDb {
        val p = newPerson()
        val c = claim(p, LEND, 10_000)
        ledger.updateDetails(c, "Yeni açıklama", day.plusDays(1), day.plusDays(10))

        val e = ledger.observeEntries(p).first().single()
        assertEquals("Yeni açıklama", e.description)
        assertEquals(day.plusDays(10), e.dueDate)
        assertEquals(10_000, e.amountKurus)

        ledger.cancel(c)
        assertFails<FinanceException.AlreadyCancelled> { ledger.updateDetails(c, "x", day, null) }
    }

    @Test fun dashboardTotals() = runDb {
        val a = newPerson("A")
        val b = newPerson("B")
        claim(a, PAID_ON_BEHALF, 150_000)                           // bu ay
        claim(a, PAID_ON_BEHALF, 7_000, LocalDate.of(2026, 10, 15)) // geçen ay
        settle(a, COLLECTION, 50_000)
        claim(b, BORROW, 30_000)
        settle(b, DEBT_PAYMENT, 10_000)

        val d = ledger.observeDashboard(day).first()
        assertEquals(107_000, d.remainingReceivable)
        assertEquals(20_000, d.remainingPayable)
        assertEquals(87_000, d.net)
        assertEquals(160_000, d.monthPaid)       // 150.000 + 10.000, geçen ay hariç
        assertEquals(50_000, d.monthCollected)
    }

    @Test fun personSummariesListActivePersons() = runDb {
        val a = newPerson("A")
        val b = newPerson("B")
        claim(a, LEND, 1_000)
        persons.setArchived(b, true)

        val list = ledger.observePersonSummaries().first()
        assertEquals(listOf("A"), list.map { it.person.fullName })
        assertTrue(list.single().balance.remainingReceivable == 1_000L)
    }

    @Test fun linesCarryPersonNamesAndRecentIsLimited() = runDb {
        val a = newPerson("Ayşe")
        val b = newPerson("Bekir")
        claim(a, LEND, 1_000)
        claim(b, LEND, 2_000)
        claim(a, LEND, 3_000)

        val all = ledger.observeAllLines().first()
        assertEquals(3, all.size)
        assertEquals(setOf("Ayşe", "Bekir"), all.map { it.personName }.toSet())
        assertEquals(2, ledger.observeRecentLines(2).first().size)
    }

    @Test fun allLinesIncludeCancelledEntries() = runDb {
        val p = newPerson()
        val c = claim(p, LEND, 1_000)
        ledger.cancel(c)
        val lines = ledger.observeAllLines().first()
        assertEquals(1, lines.size)
        assertTrue(lines.single().entry.isCancelled)
    }
}
