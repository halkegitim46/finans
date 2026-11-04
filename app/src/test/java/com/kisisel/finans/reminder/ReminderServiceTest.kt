package com.kisisel.finans.reminder

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kisisel.finans.data.local.KisiselFinansDatabase
import com.kisisel.finans.data.repository.InvoiceRepositoryImpl
import com.kisisel.finans.domain.model.InvoiceType
import com.kisisel.finans.domain.model.NewInvoice
import com.kisisel.finans.domain.model.Recurrence
import com.kisisel.finans.domain.usecase.EnsureOccurrencesUseCase
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

private class FakeLog : ReminderLog {
    var keys: MutableSet<String> = mutableSetOf()
    override suspend fun sentKeys(): Set<String> = keys.toSet()
    override suspend fun record(newKeys: Set<String>, activeOccurrenceIds: Set<Long>) {
        keys = keys.filter { occurrenceIdOfKey(it) in activeOccurrenceIds }.toMutableSet().apply { addAll(newKeys) }
    }
}

private class FakeNotifier : ReminderNotifier {
    var accept = true
    val shown = mutableListOf<PlannedReminder>()
    override fun show(reminder: PlannedReminder): Boolean {
        if (!accept) return false
        shown += reminder
        return true
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ReminderServiceTest {

    private lateinit var db: KisiselFinansDatabase
    private lateinit var invoices: InvoiceRepositoryImpl
    private val log = FakeLog()
    private val notifier = FakeNotifier()

    private fun clockAt(date: String): Clock =
        Clock.fixed(Instant.parse("${date}T07:00:00Z"), ZoneId.of("Europe/Istanbul"))

    private fun service(date: String): ReminderService {
        val c = clockAt(date)
        return ReminderService(invoices, EnsureOccurrencesUseCase(invoices, c), log, notifier, c)
    }

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, KisiselFinansDatabase::class.java).allowMainThreadQueries().build()
        invoices = InvoiceRepositoryImpl(db, clockAt("2026-11-02"))
    }

    @After fun tearDown() {
        db.close()
    }

    private fun runDb(block: suspend () -> Unit) = runBlocking { block() }

    private suspend fun newInvoice(
        reminders: Boolean = true,
        days: Set<Int> = setOf(3, 0),
        overdueEvery: Int = 1
    ): Long = invoices.createTemplate(
        NewInvoice(
            personId = null, name = "Elektrik", type = InvoiceType.ELECTRICITY, amountKurus = 150_000,
            isAmountFixed = true, recurrence = Recurrence.MONTHLY, customIntervalDays = null,
            firstDueDate = LocalDate.of(2026, 11, 5), note = "", remindersEnabled = reminders,
            reminderDays = days, overdueReminderEveryDays = overdueEvery
        ),
        LocalDate.of(2026, 11, 2)
    )

    @Test fun sendsOncePerDayAndNeverDuplicates() = runDb {
        newInvoice()
        assertEquals(1, service("2026-11-02").run())          // 3 gün kala
        assertEquals(0, service("2026-11-02").run())          // aynı gün tekrar: çift yok
        assertEquals(0, service("2026-11-04").run())          // 1 gün kala: seçili değil
        assertEquals(1, service("2026-11-05").run())          // vade günü
        assertEquals(1, service("2026-11-06").run())          // 1 gün gecikti
        assertEquals(1, service("2026-11-07").run())          // 2 gün gecikti
        assertEquals(4, notifier.shown.size)
        assertTrue(notifier.shown.first().text.contains("3 gün sonra"))
    }

    @Test fun noReminderAfterPaid() = runDb {
        newInvoice()
        val occ = invoices.observeOccurrences().first().minByOrNull { it.dueDate }!!
        invoices.payOccurrence(occ.id, LocalDate.of(2026, 11, 3))
        assertEquals(0, service("2026-11-05").run())
        assertEquals(0, service("2026-11-06").run())
    }

    @Test fun refusedNotificationIsRetriedLater() = runDb {
        newInvoice()
        notifier.accept = false
        assertEquals(0, service("2026-11-02").run())
        assertTrue(log.keys.isEmpty())                         // kaydedilmedi
        notifier.accept = true
        assertEquals(1, service("2026-11-02").run())
    }

    @Test fun remindersDisabledSendsNothing() = runDb {
        newInvoice(reminders = false)
        assertEquals(0, service("2026-11-02").run())
        assertEquals(0, service("2026-11-06").run())
    }

    @Test fun overdueCanBeDisabled() = runDb {
        newInvoice(overdueEvery = 0)
        assertEquals(0, service("2026-11-06").run())
    }

    @Test fun archivedTemplateIsSkipped() = runDb {
        val tid = newInvoice()
        invoices.archiveTemplate(tid)
        assertEquals(0, service("2026-11-02").run())
    }

    @Test fun staleKeysArePrunedWhenPeriodPaid() = runDb {
        newInvoice()
        service("2026-11-02").run()
        assertEquals(1, log.keys.size)
        val occ = invoices.observeOccurrences().first().minByOrNull { it.dueDate }!!
        invoices.payOccurrence(occ.id, LocalDate.of(2026, 11, 3))
        service("2026-11-04").run()
        assertTrue(log.keys.isEmpty())
    }
}
