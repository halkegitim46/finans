package com.kisisel.finans.ui.settings

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kisisel.finans.backup.BackupInfo
import com.kisisel.finans.backup.BackupInfoStore
import com.kisisel.finans.backup.BackupMeta
import com.kisisel.finans.backup.BackupService
import com.kisisel.finans.backup.MAX_BACKUP_FILE_BYTES
import com.kisisel.finans.backup.ParsedBackup
import com.kisisel.finans.backup.readLimited
import com.kisisel.finans.domain.FinanceException
import com.kisisel.finans.security.AppLockManager
import com.kisisel.finans.ui.common.launchCatching
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class BackupUi(
    val info: BackupInfo? = null,
    val busy: Boolean = false,
    /** Dolu ise yedek hazır; kullanıcıdan kaydedileceği dosya seçtirilir. */
    val exportName: String? = null,
    val askRestorePassword: Boolean = false,
    /** Dolu ise yedek çözüldü ve doğrulandı; kullanıcı onayı bekleniyor. */
    val preview: BackupMeta? = null
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val service: BackupService,
    private val infoStore: BackupInfoStore,
    private val lockManager: AppLockManager
) : ViewModel() {

    private val busy = MutableStateFlow(false)
    private val exportName = MutableStateFlow<String?>(null)
    private val askPassword = MutableStateFlow(false)
    private val preview = MutableStateFlow<BackupMeta?>(null)

    // Şifreli yedek baytları ve çözülmüş içerik yalnızca bellekte, işlem bitince temizlenir.
    private var pendingBytes: ByteArray? = null
    private var pendingMeta: BackupMeta? = null
    private var pendingName: String? = null
    private var pendingRestoreUri: Uri? = null
    private var pendingParsed: ParsedBackup? = null

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    val ui: StateFlow<BackupUi> = combine(infoStore.info, busy, exportName, askPassword, preview) { i, b, e, a, p ->
        BackupUi(i, b, e, a, p)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BackupUi())

    /** Sistem dosya seçicisine gitmeden önce: dönüşte uygulama kilitlenmesin. */
    fun allowExternalUi() = lockManager.allowExternalUi(SystemClock.elapsedRealtime())

    // ---------------- yedek al ----------------

    fun prepareExport(password: String, confirm: String, onResult: (String?) -> Unit) {
        if (busy.value) return
        if (password != confirm) {
            onResult("Parolalar eşleşmiyor.")
            return
        }
        busy.value = true
        launchCatching(
            onResult = { err ->
                busy.value = false
                if (err == null) exportName.value = pendingName
                onResult(err)
            }
        ) {
            val result = service.createBackup(password)
            pendingBytes = result.bytes
            pendingMeta = result.meta
            pendingName = result.suggestedName
        }
    }

    /** Seçici açıldı; tekrar tetiklenmesin. */
    fun exportLaunched() {
        exportName.value = null
    }

    fun onExportUri(uri: Uri?) {
        lockManager.endExternalUi()
        val bytes = pendingBytes
        val meta = pendingMeta
        if (uri == null || bytes == null || meta == null) {
            clearPendingExport()
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
                    ?: throw FinanceException.BackupInvalid("Dosya yazılamadı.")
                infoStore.record(
                    BackupInfo(
                        createdAt = meta.createdAt,
                        fileName = displayName(uri) ?: pendingName.orEmpty(),
                        sizeBytes = bytes.size.toLong(),
                        persons = meta.persons,
                        transactions = meta.transactions,
                        invoices = meta.invoiceOccurrences
                    )
                )
                _messages.tryEmit("Yedek kaydedildi.")
            } catch (e: Exception) {
                _messages.tryEmit("Yedek dosyası yazılamadı.")
            } finally {
                clearPendingExport()
            }
        }
    }

    // ---------------- geri yükle ----------------

    fun onRestoreUri(uri: Uri?) {
        lockManager.endExternalUi()
        pendingRestoreUri = uri
        askPassword.value = uri != null
    }

    /** Dosyayı okur, çözer ve doğrular; veritabanı henüz DEĞİŞMEZ. */
    fun readRestore(password: String, onResult: (String?) -> Unit) {
        val uri = pendingRestoreUri ?: return
        if (busy.value) return
        busy.value = true
        launchCatching(
            onResult = { err ->
                busy.value = false
                if (err == null) {
                    askPassword.value = false
                    preview.value = pendingParsed?.meta
                }
                onResult(err)
            }
        ) {
            val bytes = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { readLimited(it, MAX_BACKUP_FILE_BYTES) }
                    ?: throw FinanceException.BackupInvalid("Dosya açılamadı.")
            }
            pendingParsed = service.readBackup(bytes, password)
        }
    }

    fun confirmRestore() {
        val parsed = pendingParsed ?: return
        if (busy.value) return
        busy.value = true
        launchCatching(
            onResult = { err ->
                busy.value = false
                clearPendingRestore()
                _messages.tryEmit(err ?: "Yedek geri yüklendi.")
            }
        ) { service.restore(parsed) }
    }

    fun cancelRestore() = clearPendingRestore()

    // ---------------- yardımcılar ----------------

    private fun clearPendingExport() {
        pendingBytes?.fill(0)
        pendingBytes = null
        pendingMeta = null
        pendingName = null
        exportName.value = null
    }

    private fun clearPendingRestore() {
        pendingParsed = null
        pendingRestoreUri = null
        askPassword.value = false
        preview.value = null
    }

    private fun displayName(uri: Uri): String? = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (e: Exception) {
        null
    }
}
