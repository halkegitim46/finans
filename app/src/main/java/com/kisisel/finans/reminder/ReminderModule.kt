package com.kisisel.finans.reminder

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ReminderModule {
    @Binds abstract fun bindReminderLog(impl: DataStoreReminderLog): ReminderLog
    @Binds abstract fun bindReminderNotifier(impl: AndroidReminderNotifier): ReminderNotifier
}
