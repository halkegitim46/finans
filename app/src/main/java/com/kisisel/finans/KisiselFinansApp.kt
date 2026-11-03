package com.kisisel.finans

import android.app.Application
import com.kisisel.finans.reminder.ReminderChannels
import com.kisisel.finans.reminder.ReminderScheduler
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class KisiselFinansApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ReminderChannels.create(this)
        ReminderScheduler.schedule(this)
    }
}
