package com.kisisel.finans.reminder

import com.kisisel.finans.domain.repository.InvoiceRepository
import com.kisisel.finans.domain.usecase.EnsureOccurrencesUseCase
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Günlük kontrol: eksik dönemleri üretir, bugün gönderilecek hatırlatmaları belirler ve bildirir.
 * Mutex, periyodik iş ve uygulama açılışındaki iş aynı anda çalışırsa çift bildirimi önler.
 */
@Singleton
class ReminderService @Inject constructor(
    private val invoices: InvoiceRepository,
    private val ensureOccurrences: EnsureOccurrencesUseCase,
    private val log: ReminderLog,
    private val notifier: ReminderNotifier,
    private val clock: Clock
) {
    private val mutex = Mutex()

    /** Gönderilen bildirim sayısını döner. */
    suspend fun run(): Int = mutex.withLock {
        ensureOccurrences()
        val today = LocalDate.now(clock)
        val candidates = invoices.getReminderCandidates()
        val planned = ReminderPlanner.plan(candidates, today, log.sentKeys())
        val posted = planned.filter { notifier.show(it) }.map { it.key }.toSet()
        log.record(posted, candidates.map { it.occurrence.id }.toSet())
        posted.size
    }
}
