package com.kisisel.finans.reminder

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ReminderModule {
    @Binds abstract fun bindReminderLog(impl: DataStoreReminderLog): ReminderLog
    @Binds abstract fun bindReminderNotifier(impl: AndroidReminderNotifier): ReminderNotifier
    @Binds abstract fun bindReminderPrefs(impl: DataStoreReminderPrefs): ReminderPrefs
    @Binds abstract fun bindAgendaScheduling(impl: WorkManagerAgendaScheduling): AgendaScheduling
}

@Singleton
class WorkManagerAgendaScheduling @Inject constructor(
    @ApplicationContext private val context: Context
) : AgendaScheduling {
    override fun scheduleAt(delayMillis: Long?) = ReminderScheduler.scheduleAgendaCheck(context, delayMillis)
}
