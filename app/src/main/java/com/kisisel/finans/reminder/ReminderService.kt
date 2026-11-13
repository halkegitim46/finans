package com.kisisel.finans.reminder

import com.kisisel.finans.domain.model.AgendaItem
import com.kisisel.finans.domain.repository.AgendaRepository
import com.kisisel.finans.domain.repository.InvoiceRepository
import com.kisisel.finans.domain.repository.LedgerRepository
import com.kisisel.finans.domain.usecase.EnsureOccurrencesUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hatırlatma kontrolü: fatura vadeleri, borç/alacak vadeleri ve ajanda notları.
 *  - [run]: günlük kontrol (ve uygulama açılışı/yeniden başlatma): dönemleri üretir, bugünün tüm hatırlatmalarını gönderir,
 *    eski anahtarları temizler ve bir sonraki ajanda zamanını planlar.
 *  - [runAgenda]: yalnızca ajanda (planlı zamanda veya not değişince çalışır).
 * Mutex, eşzamanlı çalışmaların çift bildirim üretmesini önler.
 */
@Singleton
class ReminderService @Inject constructor(
    private val invoices: InvoiceRepository,
    private val ledger: LedgerRepository,
    private val agenda: AgendaRepository,
    private val prefs: ReminderPrefs,
    private val ensureOccurrences: EnsureOccurrencesUseCase,
    private val log: ReminderLog,
    private val notifier: ReminderNotifier,
    private val scheduling: AgendaScheduling,
    private val clock: Clock
) {
    private val mutex = Mutex()

    /** Gönderilen bildirim sayısını döner. */
    suspend fun run(): Int = mutex.withLock {
        ensureOccurrences()
        val today = LocalDate.now(clock)
        val now = LocalDateTime.now(clock)

        val invoiceCandidates = invoices.getReminderCandidates()
        val claims = if (prefs.claimRemindersEnabled()) ledger.observeClaims().first() else emptyList()
        val notes = agenda.getReminderCandidates()
        val sent = log.sentKeys()

        val planned = ReminderPlanner.plan(invoiceCandidates, today, sent) +
            ReminderPlanner.planClaims(claims, today, sent) +
            ReminderPlanner.planAgenda(notes, now, sent)
        val posted = post(planned)

        log.retain(
            invoiceCandidates.map { it.occurrence.id.toString() }.toSet() +
                claims.map { "c${it.entryId}" } +
                notes.map { "a${it.id}" }
        )
        scheduleNextAgenda(notes, now)
        posted
    }

    suspend fun runAgenda(): Int = mutex.withLock {
        val now = LocalDateTime.now(clock)
        val notes = agenda.getReminderCandidates()
        val posted = post(ReminderPlanner.planAgenda(notes, now, log.sentKeys()))
        scheduleNextAgenda(notes, now)
        posted
    }

    private suspend fun post(planned: List<PlannedReminder>): Int {
        val keys = planned.filter { notifier.show(it) }.map { it.key }.toSet()
        log.add(keys)
        return keys.size
    }

    private fun scheduleNextAgenda(notes: List<AgendaItem>, now: LocalDateTime) {
        val next = ReminderPlanner.nextAgendaReminder(notes, now)
        scheduling.scheduleAt(next?.let { maxOf(1_000L, Duration.between(now, it).toMillis()) })
    }
}
