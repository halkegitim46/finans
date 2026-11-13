package com.kisisel.finans.reminder

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.kisisel.finans.MainActivity
import com.kisisel.finans.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

object ReminderChannels {
    const val INVOICE_CHANNEL_ID = "invoice_reminders"

    fun create(context: Context) {
        val channel = NotificationChannel(
            INVOICE_CHANNEL_ID,
            "Fatura hatırlatmaları",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Yaklaşan ve geciken faturalar için hatırlatmalar"
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}

@Singleton
class AndroidReminderNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) : ReminderNotifier {

    override fun show(reminder: PlannedReminder): Boolean {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return false

        // Dokununca ilgili fatura dönemi açılır.
        val intent = Intent(context, MainActivity::class.java).apply {
            when {
                reminder.agendaId != null -> putExtra(EXTRA_AGENDA_ID, reminder.agendaId)
                reminder.personId != null -> putExtra(EXTRA_PERSON_ID, reminder.personId)
                else -> putExtra(EXTRA_OCCURRENCE_ID, reminder.occurrenceId)
            }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            reminder.key.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Kilit ekranında tutar/ad görünmesin: genel bir sürüm göster.
        val publicVersion = NotificationCompat.Builder(context, ReminderChannels.INVOICE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Fatura hatırlatması")
            .build()

        val notification = NotificationCompat.Builder(context, ReminderChannels.INVOICE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(reminder.title)
            .setContentText(reminder.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(reminder.text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .build()

        return try {
            manager.notify(reminder.key.hashCode(), notification)
            true
        } catch (e: SecurityException) {
            false
        }
    }
}
