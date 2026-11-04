package com.kisisel.finans.report

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kisisel.finans.data.local.KisiselFinansDatabase
import com.kisisel.finans.data.repository.LedgerRepositoryImpl
import com.kisisel.finans.data.repository.PersonRepositoryImpl
import com.kisisel.finans.domain.model.NewClaim
import com.kisisel.finans.domain.model.NewPerson
import com.kisisel.finans.domain.model.NewSettlement
import com.kisisel.finans.domain.model.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
class ClaimsFlowTest {
    private lateinit var db: KisiselFinansDatabase
    private lateinit var persons: PersonRepositoryImpl
    private lateinit var ledger: LedgerRepositoryImpl
    private val clock: Clock = Clock.fixed(Instant.parse("2026-11-04T09:00:00Z"), ZoneId.of("Europe/Istanbul"))
    private val day: LocalDate = LocalDate.of(2026, 11, 4)

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, KisiselFinansDatabase::class.java).allowMainThreadQueries().build()
        persons = PersonRepositoryImpl(db.personDao(), clock)
        ledger = LedgerRepositoryImpl(db, clock)
    }

    @After fun tearDown() {
        db.close()
    }

    private fun runDb(block: suspend () -> Unit) = runBlocking { block() }

    @Test fun claimsShowOpenAmountsAfterPartialCollection() = runDb {
        val p = persons.create(NewPerson("Ayşe", null, "", 0))
        ledger.recordClaim(NewClaim(p, TransactionType.LEND, 100_000, day, day.plusDays(10), "Harçlık", "k1"))
        ledger.recordClaim(NewClaim(p, TransactionType.BORROW, 20_000, day, null, "", "k2"))
        ledger.settle(NewSettlement(p, TransactionType.COLLECTION, 30_000, day, "", "k3"))

        val claims = ledger.observeClaims().first()
        assertEquals(2, claims.size)                         // tahsilat satırı yok, yalnızca alacak/borç doğuranlar
        val lend = claims.first { it.type == TransactionType.LEND }
        assertEquals(70_000L, lend.openKurus)
        assertEquals("Harçlık", lend.description)
        assertEquals(day.plusDays(10), lend.dueDate)
        assertEquals("Ayşe", lend.personName)
        assertEquals(20_000L, claims.first { it.type == TransactionType.BORROW }.openKurus)
    }

    @Test fun cancelledClaimsAndCancelledSettlementsAreHandled() = runDb {
        val p = persons.create(NewPerson("Ayşe", null, "", 0))
        val c = ledger.recordClaim(NewClaim(p, TransactionType.LEND, 10_000, day, null, "", "k1"))
        val s = ledger.settle(NewSettlement(p, TransactionType.COLLECTION, 4_000, day, "", "k2"))
        assertEquals(6_000L, ledger.observeClaims().first().single().openKurus)

        ledger.cancel(s.transactionId)                       // tahsilat iptal: alacak tekrar tamamen açık
        assertEquals(10_000L, ledger.observeClaims().first().single().openKurus)

        ledger.cancel(c)                                     // alacak iptal: listeden düşer
        assertEquals(0, ledger.observeClaims().first().size)
    }

    @Test fun personsObserveAllIncludesArchived() = runDb {
        val a = persons.create(NewPerson("A", null, "", 0))
        persons.create(NewPerson("B", null, "", 0))
        persons.setArchived(a, true)
        assertEquals(2, persons.observeAll().first().size)
    }
}
