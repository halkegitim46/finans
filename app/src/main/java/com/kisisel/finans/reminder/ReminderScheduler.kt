package com.kisisel.finans.reminder

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Günlük kontrolü WorkManager ile planlar. WorkManager Doze/pil tasarrufuna uyumludur: bildirim
 * dakikalar-saatler gecikebilir ama güvenilir çalışır. Tam zamanlı alarm ve pil muafiyeti istenmez.
 */
object ReminderScheduler {
    const val PERIODIC_WORK = "invoice_reminders_daily"
    const val ONCE_WORK = "invoice_reminders_now"
    const val DAILY_HOUR = 9

    /** Uygulama açılışında ve yeniden başlatma/güncelleme sonrasında çağrılır (tekrar çağrı güvenlidir). */
    fun schedule(context: Context, clock: Clock = Clock.systemDefaultZone()) {
        val workManager = WorkManager.getInstance(context)

        val delay = initialDelayMillis(LocalDateTime.now(clock), DAILY_HOUR)
        val periodic = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        workManager.enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, periodic)

        // Açılışta bir kez de kontrol et (kaçan günleri yakalamak için; çift bildirim kaydı ile engellenir).
        workManager.enqueueUniqueWork(ONCE_WORK, ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<ReminderWorker>().build())
    }

    /** Bir sonraki hour:00'a kadar milisaniye (tam o saatteyse ertesi gün). */
    fun initialDelayMillis(now: LocalDateTime, hour: Int): Long {
        var next = now.toLocalDate().atTime(hour, 0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next).toMillis()
    }
}
