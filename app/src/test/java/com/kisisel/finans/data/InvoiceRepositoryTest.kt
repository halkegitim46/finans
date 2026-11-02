package com.kisisel.finans.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kisisel.finans.data.local.KisiselFinansDatabase
import com.kisisel.finans.data.repository.InvoiceRepositoryImpl
import com.kisisel.finans.data.repository.LedgerRepositoryImpl
import com.kisisel.finans.data.repository.PersonRepositoryImpl
import com.kisisel.finans.domain.FinanceException
import com.kisisel.finans.domain.model.InvoiceOccurrence
import com.kisisel.finans.domain.model.InvoiceType
import com.kisisel.finans.domain.model.InvoiceUpdate
import com.kisisel.finans.domain.model.NewInvoice
import com.kisisel.finans.domain.model.NewPerson
import com.kisisel.finans.domain.model.NewSettlement
import com.kisisel.finans.domain.model.OccurrenceStatus
import com.kisisel.finans.domain.model.PersonBalance
import com.kisisel.finans.domain.model.Recurrence
import com.kisisel.finans.domain.model.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
@Config(sdk = [34])
class InvoiceRepositoryTest {

    private lateinit var db: KisiselFinansDatabase
    private lateinit var persons: PersonRepositoryImpl
    private lateinit var ledger: LedgerRepositoryImpl
    private lateinit var invoices: InvoiceRepositoryImpl

    private val clock: Clock = Clock.fixed(Instant.parse("2026-11-02T09:00:00Z"), ZoneId.of("Europe/Istanbul"))
    private val today: LocalDate = LocalDate.of(2026, 11, 2)
    private fun d(s: String) = LocalDate.parse(s)
    private var keySeq = 0

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, KisiselFinansDatabase::class.java).allowMainThreadQueries().build()
        persons = PersonRepositoryImpl(db.personDao(), clock)
        ledger = LedgerRepositoryImpl(db, clock)
        invoices = InvoiceRepositoryImpl(db, clock)
    }

    @After fun tearDown() {
        db.close()
    }

    private fun runDb(block: suspend () -> Unit) = runBlocking { block() }

    private suspend inline fun <reified E : Throwable> assertFails(noinline block: suspend () -> Unit): E {
        try {
            block()
        } catch (e: Throwable) {
            if (e is E) return e
            throw e
        }
        throw AssertionError("${E::class.java.simpleName} bekleniyordu")
    }

    private suspend fun newPerson(name: String = "Dayım") = persons.create(NewPerson(name, null, "", 0))

    private suspend fun newInvoice(
        personId: Long?,
        first: LocalDate = d("2026-11-05"),
        amount: Long? = 150_000,
        fixed: Boolean = true,
        recurrence: Recurrence = Recurrence.MONTHLY
    ) = invoices.createTemplate(
        NewInvoice(
            personId = personId, name = "Elektrik", type = InvoiceType.ELECTRICITY, amountKurus = amount,
            isAmountFixed = fixed, recurrence = recurrence, customIntervalDays = null, firstDueDate = first,
            note = "", remindersEnabled = true, reminderDays = setOf(7, 3, 1, 0), overdueReminderEveryDays = 1
        ),
        today
    )

    private suspend fun occurrences(): List<InvoiceOccurrence> =
        invoices.observeOccurrences().first().sortedBy { it.dueDate }

    private suspend fun balance(pid: Long): PersonBalance = ledger.observePersonSummary(pid).first()!!.balance

    @Test fun createGeneratesFirstAndHorizonOccurrences() = runDb {
        newInvoice(newPerson())
        assertEquals(listOf(d("2026-11-05"), d("2026-12-05")), occurrences().map { it.dueDate })
        assertEquals("2026-11", occurrences().first().periodKey)
    }

    @Test fun generationIsIdempotent() = runDb {
        newInvoice(newPerson())
        assertEquals(2, invoices.generateOccurrences(d("2027-02-28")))   // Ocak, Şubat
        assertEquals(0, invoices.generateOccurrences(d("2027-02-28")))
        assertEquals(4, occurrences().size)
    }

    @Test fun pastFirstDueCreatesFirstThenSkipsOlderPeriods() = runDb {
        newInvoice(newPerson(), first = d("2026-08-05"))
        assertEquals(
            listOf(d("2026-08-05"), d("2026-11-05"), d("2026-12-05")),
            occurrences().map { it.dueDate }
        )
    }

    @Test fun payingCreatesReceivableAndClosesOnlyThatPeriod() = runDb {
        val p = newPerson()
        newInvoice(p)
        val nov = occurrences().first()
        val txId = invoices.payOccurrence(nov.id, today)

        val after = occurrences()
        assertEquals(OccurrenceStatus.PAID, after[0].status)
        assertEquals(OccurrenceStatus.UNPAID, after[1].status)   // diğer dönem etkilenmez
        assertEquals(today, after[0].paidDate)

        val entries = ledger.observeEntries(p).first()
        assertEquals(1, entries.size)
        assertEquals(txId, entries.single().id)
        assertEquals(TransactionType.PAID_ON_BEHALF, entries.single().type)
        assertEquals(150_000, entries.single().amountKurus)
        assertEquals(nov.id, entries.single().occurrenceId)

        // Dayı örneği: 500 TL tahsilat -> kalan 1.000 TL
        ledger.settle(NewSettlement(p, TransactionType.COLLECTION, 50_000, today, "", "c1"))
        assertEquals(100_000, balance(p).remainingReceivable)
    }

    @Test fun cannotPayTwice() = runDb {
        val p = newPerson()
        newInvoice(p)
        val occ = occurrences().first()
        invoices.payOccurrence(occ.id, today)
        assertFails<FinanceException.OccurrenceAlreadyPaid> { invoices.payOccurrence(occ.id, today) }
        assertEquals(1, ledger.observeEntries(p).first().size)
        assertEquals(150_000, balance(p).totalReceivable)
    }

    @Test fun ownBillCreatesNoReceivable() = runDb {
        newInvoice(personId = null)
        val occ = occurrences().first()
        assertNull(invoices.payOccurrence(occ.id, today))
        assertEquals(OccurrenceStatus.PAID, occurrences().first().status)
        assertEquals(0, db.transactionDao().countByOccurrence(occ.id))
    }

    @Test fun variableAmountMustBeEnteredBeforePaying() = runDb {
        val p = newPerson()
        newInvoice(p, amount = null, fixed = false)
        val occ = occurrences().first()
        assertNull(occ.amountKurus)

        assertFails<FinanceException.AmountRequired> { invoices.payOccurrence(occ.id, today) }
        assertEquals(OccurrenceStatus.UNPAID, occurrences().first().status)   // geri alındı

        invoices.setOccurrenceAmount(occ.id, 90_000)
        invoices.payOccurrence(occ.id, today)
        assertEquals(90_000, balance(p).remainingReceivable)
    }

    @Test fun undoReopensAndCancelsReceivable() = runDb {
        val p = newPerson()
        newInvoice(p)
        val occ = occurrences().first()
        invoices.payOccurrence(occ.id, today)

        invoices.undoPayment(occ.id)
        assertEquals(OccurrenceStatus.UNPAID, occurrences().first().status)
        assertNull(occurrences().first().paidDate)
        assertEquals(0, balance(p).totalReceivable)

        invoices.payOccurrence(occ.id, today)                       // tekrar ödenebilir, yeni alacak kaydı
        assertEquals(150_000, balance(p).totalReceivable)
        assertEquals(2, ledger.observeEntries(p).first().size)      // eski kayıt iptal olarak korunur
    }

    @Test fun undoBlockedWhenCollectionAllocated() = runDb {
        val p = newPerson()
        newInvoice(p)
        val occ = occurrences().first()
        invoices.payOccurrence(occ.id, today)
        ledger.settle(NewSettlement(p, TransactionType.COLLECTION, 50_000, today, "", "c1"))

        assertFails<FinanceException.HasActiveAllocations> { invoices.undoPayment(occ.id) }
        assertEquals(OccurrenceStatus.PAID, occurrences().first().status)
        assertEquals(100_000, balance(p).remainingReceivable)
    }

    @Test fun invoiceLinkedTransactionCannotBeCancelledFromLedger() = runDb {
        val p = newPerson()
        newInvoice(p)
        val txId = invoices.payOccurrence(occurrences().first().id, today)!!
        assertFails<FinanceException.LinkedToInvoice> { ledger.cancel(txId) }
    }

    @Test fun updateAffectsOnlyFutureOccurrences() = runDb {
        val p = newPerson()
        val tid = newInvoice(p)
        val t = invoices.getTemplate(tid)!!
        invoices.updateTemplate(
            InvoiceUpdate(
                id = tid, personId = p, name = "Elektrik (yeni)", type = t.type, amountKurus = 200_000,
                isAmountFixed = true, note = "", remindersEnabled = true,
                reminderDays = setOf(3), overdueReminderEveryDays = 0
            )
        )
        invoices.generateOccurrences(d("2027-01-31"))

        val list = occurrences()
        assertEquals(150_000L, list[0].amountKurus)   // Kasım değişmedi
        assertEquals(150_000L, list[1].amountKurus)   // Aralık değişmedi
        assertEquals(200_000L, list[2].amountKurus)   // Ocak yeni tutar
        assertEquals(setOf(3), invoices.getTemplate(tid)!!.reminderDays)
    }

    @Test fun archivedTemplateStopsGenerationButKeepsHistory() = runDb {
        val tid = newInvoice(newPerson())
        invoices.archiveTemplate(tid)
        assertEquals(0, invoices.generateOccurrences(d("2027-12-31")))
        assertEquals(2, occurrences().size)
    }

    @Test fun cancelledOccurrenceCannotBePaid() = runDb {
        newInvoice(newPerson())
        val occ = occurrences().first()
        invoices.cancelOccurrence(occ.id)
        assertFails<FinanceException.OccurrenceNotPayable> { invoices.payOccurrence(occ.id, today) }
    }
}
