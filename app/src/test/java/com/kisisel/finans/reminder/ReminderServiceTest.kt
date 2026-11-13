package com.kisisel.finans.reminder

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kisisel.finans.data.local.KisiselFinansDatabase
import com.kisisel.finans.data.repository.AgendaRepositoryImpl
import com.kisisel.finans.data.repository.InvoiceRepositoryImpl
import com.kisisel.finans.data.repository.LedgerRepositoryImpl
import com.kisisel.finans.data.repository.PersonRepositoryImpl
import com.kisisel.finans.domain.model.AgendaUpdate
import com.kisisel.finans.domain.model.InvoiceType
import com.kisisel.finans.domain.model.NewAgendaItem
import com.kisisel.finans.domain.model.NewClaim
import com.kisisel.finans.domain.model.NewInvoice
import com.kisisel.finans.domain.model.NewPerson
import com.kisisel.finans.domain.model.NewSettlement
import com.kisisel.finans.domain.model.Recurrence
import com.kisisel.finans.domain.model.TransactionType
import com.kisisel.finans.domain.usecase.EnsureOccurrencesUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

private class FakeLog : ReminderLog {
    var keys: MutableSet<String> = mutableSetOf()
    override suspend fun sentKeys(): Set<String> = keys.toSet()
    override suspend fun add(keys: Set<String>) {
        this.keys.addAll(keys)
    }
    override suspend fun retain(activeOwners: Set<String>) {
        keys = keys.filter { ownerOfKey(it) in activeOwners }.toMutableSet()
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

private class FakePrefs : ReminderPrefs {
    private val state = MutableStateFlow(true)
    override val claimRemindersFlow: Flow<Boolean> = state
    override suspend fun claimRemindersEnabled(): Boolean = state.value
    override suspend fun setClaimRemindersEnabled(enabled: Boolean) {
        state.value = enabled
    }
}

private class FakeScheduling : AgendaScheduling {
    var lastDelay: Long? = -1L
    var calls = 0
    override fun scheduleAt(delayMillis: Long?) {
        lastDelay = delayMillis
        calls++
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ReminderServiceTest {

    private lateinit var db: KisiselFinansDatabase
    private lateinit var invoices: InvoiceRepositoryImpl
    private lateinit var ledger: LedgerRepositoryImpl
    private lateinit var agenda: AgendaRepositoryImpl
    private lateinit var persons: PersonRepositoryImpl
    private val log = FakeLog()
    private val notifier = FakeNotifier()
    private val prefs = FakePrefs()
    private val scheduling = FakeScheduling()
    private val zone = ZoneId.of("Europe/Istanbul")

    private fun clockAtInstant(instant: String): Clock = Clock.fixed(Instant.parse(instant), zone)
    private fun clockAt(date: String): Clock = clockAtInstant("${date}T07:00:00Z")      // 10:00 yerel

    private fun serviceAtInstant(instant: String): ReminderService {
        val c = clockAtInstant(instant)
        return ReminderService(invoices, ledger, agenda, prefs, EnsureOccurrencesUseCase(invoices, c), log, notifier, scheduling, c)
    }

    private fun service(date: String): ReminderService = serviceAtInstant("${date}T07:00:00Z")

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, KisiselFinansDatabase::class.java).allowMainThreadQueries().build()
        val setup = clockAt("2026-11-02")
        invoices = InvoiceRepositoryImpl(db, setup)
        ledger = LedgerRepositoryImpl(db, setup)
        agenda = AgendaRepositoryImpl(db, setup)
        persons = PersonRepositoryImpl(db.personDao(), setup)
    }

    @After fun tearDown() {
        db.close()
    }

    private fun runDb(block: suspend () -> Unit) = runBlocking { block() }

    private suspend fun newInvoice(
        reminders: Boolean = true,
        days: Set<Int> = setOf(3, 0),
        overdueEvery: Int = 1,
        personId: Long? = null
    ): Long = invoices.createTemplate(
        NewInvoice(
            personId = personId, name = "Elektrik", type = InvoiceType.ELECTRICITY, amountKurus = 150_000,
            isAmountFixed = true, recurrence = Recurrence.MONTHLY, customIntervalDays = null,
            firstDueDate = LocalDate.of(2026, 11, 5), note = "", remindersEnabled = reminders,
            reminderDays = days, overdueReminderEveryDays = overdueEvery
        ),
        LocalDate.of(2026, 11, 2)
    )

    private suspend fun newNote(
        date: String = "2026-11-03", time: LocalTime? = LocalTime.of(14, 30), remind: Int? = 60,
        title: String = "Ahmet'i ara", personId: Long? = null
    ): Long = agenda.create(NewAgendaItem(title, "borcu hatırlat", LocalDate.parse(date), time, personId, remind))

    // ---------------- fatura ----------------
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

    // ---------------- borç / alacak vadesi ----------------
    @Test fun claimDueDateReminderIsSentOnceAndHonoursPreference() = runDb {
        val p = persons.create(NewPerson("Ayşe", null, "", 0))
        ledger.recordClaim(NewClaim(p, TransactionType.LEND, 100_000, LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 5), "harçlık", "k1"))

        assertEquals(1, service("2026-11-02").run())                      // vadeye 3 gün
        val first = notifier.shown.single()
        assertTrue(first.key.startsWith("c"))
        assertTrue(first.title.startsWith("Alacak vadesi"))
        assertEquals(p, first.personId)
        assertEquals(0, service("2026-11-02").run())                      // çift yok

        prefs.setClaimRemindersEnabled(false)
        assertEquals(0, service("2026-11-04").run())                      // 1 gün kala ama tercih kapalı
        prefs.setClaimRemindersEnabled(true)
        assertEquals(1, service("2026-11-04").run())
    }

    @Test fun settledClaimStopsReminders() = runDb {
        val p = persons.create(NewPerson("Ayşe", null, "", 0))
        ledger.recordClaim(NewClaim(p, TransactionType.LEND, 100_000, LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 5), "", "k1"))
        ledger.settle(NewSettlement(p, TransactionType.COLLECTION, null, LocalDate.of(2026, 11, 3), "", "s1"))   // tamamını kapat
        assertEquals(0, service("2026-11-04").run())
    }

    @Test fun invoiceDerivedClaimIsNotRemindedAsClaim() = runDb {
        val p = persons.create(NewPerson("Dayım", null, "", 0))
        newInvoice(personId = p)
        val occ = invoices.observeOccurrences().first().minByOrNull { it.dueDate }!!
        invoices.payOccurrence(occ.id, LocalDate.of(2026, 11, 2))         // kişi adına ödeme (vade = fatura vadesi)
        assertEquals(0, service("2026-11-02").run())                      // fatura ödendi; alacak ayrıca hatırlatılmaz
        assertTrue(notifier.shown.isEmpty())
    }

    // ---------------- ajanda ----------------
    @Test fun agendaReminderFiresAtItsTimeOnceAndSchedulesNext() = runDb {
        newNote()                                                          // 3 Kas 14:30, 1 saat önce → 13:30 yerel
        assertEquals(0, serviceAtInstant("2026-11-03T10:15:00Z").runAgenda())   // 13:15 yerel: henüz erken
        assertEquals(15 * 60_000L, scheduling.lastDelay)                   // 13:30'a 15 dk
        assertEquals(1, serviceAtInstant("2026-11-03T10:31:00Z").runAgenda())   // 13:31
        assertEquals("Ajanda: Ahmet'i ara", notifier.shown.single().title)
        assertNull(scheduling.lastDelay)                                   // başka hatırlatma yok → plan iptal
        assertEquals(0, serviceAtInstant("2026-11-03T10:40:00Z").runAgenda())
    }

    @Test fun editedNoteNotifiesAgainBecauseKeyChanges() = runDb {
        val id = newNote()
        assertEquals(1, serviceAtInstant("2026-11-03T10:31:00Z").runAgenda())
        agenda.update(AgendaUpdate(id, "Ahmet'i ara", "", LocalDate.of(2026, 11, 3), LocalTime.of(16, 0), null, 0))
        assertEquals(1, serviceAtInstant("2026-11-03T13:05:00Z").runAgenda())   // 16:05 yerel
        assertEquals(2, notifier.shown.size)
    }

    @Test fun staleAgendaReminderIsSkipped() = runDb {
        newNote()
        assertEquals(0, serviceAtInstant("2026-11-05T07:00:00Z").runAgenda())
        assertTrue(notifier.shown.isEmpty())
    }

    @Test fun doneOrReminderlessNotesNeverNotify() = runDb {
        val a = newNote(title = "a")
        newNote(title = "b", remind = null)
        agenda.setDone(a, true)
        assertEquals(0, serviceAtInstant("2026-11-03T10:31:00Z").runAgenda())
    }

    @Test fun allDayNoteRemindsAtNineAm() = runDb {
        newNote(date = "2026-11-03", time = null, remind = 0)
        assertEquals(0, serviceAtInstant("2026-11-03T05:59:00Z").runAgenda())   // 08:59 yerel
        assertEquals(1, serviceAtInstant("2026-11-03T06:01:00Z").runAgenda())   // 09:01 yerel
        assertTrue(notifier.shown.single().text.contains("gün boyu"))
    }

    @Test fun dailyRunAlsoSchedulesTheNextAgendaReminder() = runDb {
        newNote(date = "2026-11-05", time = null, remind = 0)               // 5 Kas 09:00
        service("2026-11-03").run()                                          // 3 Kas 10:00 yerel
        assertEquals(Duration.ofHours(47).toMillis(), scheduling.lastDelay)
    }

    @Test fun dailyRunAlsoSendsDueAgendaNotes() = runDb {
        newNote(date = "2026-11-03", time = LocalTime.of(9, 0), remind = 0)
        assertEquals(1, serviceAtInstant("2026-11-03T07:00:00Z").run())     // 10:00 yerel, 09:00 hatırlatması geçmiş (< 24 sa)
    }

    @Test fun archivedNoteKeysArePruned() = runDb {
        val id = newNote()
        serviceAtInstant("2026-11-03T10:31:00Z").run()
        assertTrue(log.keys.any { it.startsWith("a") })
        agenda.archive(id)
        serviceAtInstant("2026-11-03T10:40:00Z").run()
        assertTrue(log.keys.none { it.startsWith("a") })
    }

    @Test fun pruningNeverRemovesOtherKindsOfKeys() = runDb {
        val p = persons.create(NewPerson("Ayşe", null, "", 0))
        ledger.recordClaim(NewClaim(p, TransactionType.LEND, 100_000, LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 5), "", "k1"))
        newInvoice()
        newNote(date = "2026-11-02", time = LocalTime.of(9, 0), remind = 0)

        assertEquals(3, service("2026-11-02").run())                         // fatura + alacak + not
        val before = log.keys.toSet()
        assertEquals(0, service("2026-11-02").run())
        assertEquals(before, log.keys.toSet())                               // üç tür de korunur
        assertTrue(log.keys.any { it.startsWith("c") } && log.keys.any { it.startsWith("a") } && log.keys.any { it.first().isDigit() })
    }
}
