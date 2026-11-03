package com.kisisel.finans.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReminderEntryPoint {
    fun reminderService(): ReminderService
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        val entryPoint = EntryPointAccessors.fromApplication(applicationContext, ReminderEntryPoint::class.java)
        entryPoint.reminderService().run()
        Result.success()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // Hassas bilgi loglanmaz; sınırlı sayıda yeniden dene.
        if (runAttemptCount < 3) Result.retry() else Result.failure()
    }
}
