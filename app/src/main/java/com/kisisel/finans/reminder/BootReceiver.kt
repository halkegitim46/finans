package com.kisisel.finans.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Cihaz yeniden başlayınca veya uygulama güncellenince hatırlatma planını yeniden kurar. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            ReminderScheduler.schedule(context.applicationContext)
        }
    }
}
