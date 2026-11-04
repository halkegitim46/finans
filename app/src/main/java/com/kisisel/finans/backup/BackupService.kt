package com.kisisel.finans.backup

import com.kisisel.finans.data.local.DatabaseSnapshotter
import com.kisisel.finans.data.local.KisiselFinansDatabase
import com.kisisel.finans.domain.FinanceException
import com.kisisel.finans.security.PasswordPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

class BackupResult(val bytes: ByteArray, val meta: BackupMeta, val suggestedName: String)

/**
 * Şifreli yedekleme: veritabanı → JSON → gzip → AES-256-GCM (yedek parolası).
 * Geri yükleme iki aşamalıdır: önce çöz + doğrula ([readBackup], veri değişmez), sonra onaylanırsa [restore].
 * Hiçbir ağ erişimi yoktur.
 */
@Singleton
class BackupService @Inject constructor(
    private val db: KisiselFinansDatabase,
    private val clock: Clock
) {
    internal var iterations: Int = BackupCrypto.DEFAULT_ITERATIONS

    suspend fun createBackup(password: String): BackupResult {
        PasswordPolicy.validatePassword(password)?.let { throw FinanceException.InvalidInput(it) }
        val snapshot = DatabaseSnapshotter.export(db)
        val now = clock.millis()
        val plain = BackupCodec.encode(snapshot, now)
        val file = withContext(Dispatchers.Default) {
            BackupCrypto.encrypt(plain, password.toCharArray(), iterations)
        }
        return BackupResult(file, BackupCodec.metaOf(snapshot, now), backupFileName(LocalDateTime.now(clock)))
    }

    /** Dosyayı çözer ve doğrular; veritabanına DOKUNMAZ. */
    suspend fun readBackup(file: ByteArray, password: String): ParsedBackup {
        val plain = withContext(Dispatchers.Default) { BackupCrypto.decrypt(file, password.toCharArray()) }
        return BackupCodec.decode(plain)
    }

    /** Mevcut tüm verileri siler ve yedeği yükler (tek transaction; hata olursa önceki veriler korunur). */
    suspend fun restore(parsed: ParsedBackup) {
        try {
            DatabaseSnapshotter.replaceAll(db, parsed.snapshot)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw FinanceException.BackupInvalid("Yedek geri yüklenemedi; mevcut verileriniz değişmedi (yedek içeriği tutarsız).")
        }
    }
}
