package com.kisisel.finans.security

import android.content.Context
import android.content.Intent
import android.os.Process
import androidx.core.app.NotificationManagerCompat
import androidx.work.WorkManager
import com.kisisel.finans.data.local.KisiselFinansDatabase
import com.kisisel.finans.data.local.SecureDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * "Parolamı unuttum" ve "Tüm verileri sil" için: şifreli veritabanını, anahtarı, ayarları ve
 * Keystore anahtarını kalıcı olarak siler, uygulamayı baştan başlatır. Geri alınamaz.
 * Veritabanı Provider ile tembel alınır: anahtar bozuksa bile sıfırlama çalışabilsin.
 */
@Singleton
class AppResetter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: Provider<KisiselFinansDatabase>
) {
    fun wipeAndRestart() {
        runCatching { database.get().close() }
        context.deleteDatabase(SecureDatabase.ENC_NAME)
        context.deleteDatabase(KisiselFinansDatabase.NAME)
        File(context.filesDir, DatabaseKeyStore.FILE_NAME).delete()
        File(context.filesDir, "datastore").deleteRecursively()
        context.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        runCatching { AndroidKeystoreKeyWrapper.deleteKey() }
        runCatching { WorkManager.getInstance(context).cancelAllWork() }
        runCatching { NotificationManagerCompat.from(context).cancelAll() }

        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            context.startActivity(it)
        }
        Process.killProcess(Process.myPid())
    }
}
