package com.kisisel.finans.ui.common

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

/** Bildirimler açık mı? Ayarlardan dönüldüğünde (ON_RESUME) yeniden kontrol edilir. */
@Composable
fun rememberNotificationsEnabled(): Boolean {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        enabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
    return enabled
}

/** Bildirimler kapalıyken Ana Sayfa'da gösterilen uyarı kartı. */
@Composable
fun NotificationsBanner() {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Bildirimler kapalı", style = MaterialTheme.typography.titleSmall)
            Text(
                "Fatura hatırlatmalarını alabilmek için bildirimlere izin verin.",
                style = MaterialTheme.typography.bodyMedium
            )
            TextButton(onClick = { openNotificationSettings(context) }) { Text("Bildirim ayarlarını aç") }
        }
    }
}

fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

/** Android 13+: ilk açılışta bir kez bildirim iznini açıklayıp ister. */
@Composable
fun NotificationPermissionPrompt() {
    if (Build.VERSION.SDK_INT < 33) return
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE) }
    var show by remember {
        mutableStateOf(
            !prefs.getBoolean(KEY_PROMPTED, false) &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    if (show) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Bildirim izni") },
            text = { Text("Fatura son ödeme tarihlerinden önce sizi uyarabilmemiz için bildirim iznine ihtiyaç var.") },
            confirmButton = {
                TextButton(onClick = {
                    prefs.edit().putBoolean(KEY_PROMPTED, true).apply()
                    show = false
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }) { Text("İzin ver") }
            },
            dismissButton = {
                TextButton(onClick = {
                    prefs.edit().putBoolean(KEY_PROMPTED, true).apply()
                    show = false
                }) { Text("Şimdi değil") }
            }
        )
    }
}

private const val KEY_PROMPTED = "notif_prompted"
