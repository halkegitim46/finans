package com.kisisel.finans.security

import android.content.Context
import com.kisisel.finans.data.local.SecureDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/** Veritabanı anahtarı kullanılamıyor: kullanıcıya gösterilecek kurtarma ekranının nedeni. */
class DatabaseKeyUnavailableException(val reason: Reason, cause: Throwable? = null) :
    Exception(reason.message, cause) {
    enum class Reason(val message: String) {
        /** Şifreli veritabanı var ama anahtar dosyası yok: yeni anahtar üretmek veriyi kalıcı kaybettirirdi. */
        MISSING("Şifreleme anahtarı dosyası bulunamadı."),
        UNREADABLE("Şifreleme anahtarı okunamadı (Android Keystore anahtarı geçersiz olabilir)."),
        WRITE_FAILED("Şifreleme anahtarı dosyası yazılamadı (depolama alanı dolu olabilir).")
    }
}

/**
 * Veritabanı şifreleme anahtarı: rastgele 256 bit, Keystore anahtarıyla sarmalanmış olarak dosyada saklanır.
 * Parolaya bağlı DEĞİLDİR; parola yalnızca uygulama kilididir. (Unutulan parola kurtarılamaz;
 * kurtarma yolu şifreli yedek veya verileri sıfırlamadır.)
 *
 * Güvenlik kuralı: şifreli veritabanı zaten varken anahtar dosyası eksikse ASLA yeni anahtar üretilmez
 * (üretilirse eski veri sessizce erişilemez olurdu); [DatabaseKeyUnavailableException] fırlatılır.
 */
@Singleton
class DatabaseKeyStore(
    private val file: File,
    private val wrapper: KeyWrapper,
    private val databaseExists: () -> Boolean = { false }
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(
        File(context.filesDir, FILE_NAME),
        AndroidKeystoreKeyWrapper(),
        { context.getDatabasePath(SecureDatabase.ENC_NAME).exists() }
    )

    /** 64 karakterlik onaltılık anahtar (ilk çağrıda üretilir). */
    @Synchronized
    fun getOrCreateKeyHex(): String {
        if (file.exists()) return readKey()
        if (databaseExists()) throw DatabaseKeyUnavailableException(DatabaseKeyUnavailableException.Reason.MISSING)
        return createKey()
    }

    fun exists(): Boolean = file.exists()

    private fun readKey(): String {
        val raw = try {
            wrapper.unwrap(file.readBytes())
        } catch (e: Exception) {
            throw DatabaseKeyUnavailableException(DatabaseKeyUnavailableException.Reason.UNREADABLE, e)
        }
        if (raw.size != KEY_BYTES) throw DatabaseKeyUnavailableException(DatabaseKeyUnavailableException.Reason.UNREADABLE)
        return raw.toHex()
    }

    private fun createKey(): String {
        val key = ByteArray(KEY_BYTES).also { SecureRandom().nextBytes(it) }

        // Keystore gerçekten çalışıyor mu? Sarıp açarak doğrula (dosya yazmadan önce).
        val wrapped = try {
            wrapper.wrap(key).also { check(wrapper.unwrap(it).contentEquals(key)) { "Keystore doğrulaması başarısız" } }
        } catch (e: Exception) {
            throw DatabaseKeyUnavailableException(DatabaseKeyUnavailableException.Reason.UNREADABLE, e)
        }

        // Dayanıklı yazım: geçici dosyaya yaz + diske eşitle + geri okuyup doğrula + atomik taşı.
        val tmp = File(file.parentFile, "$FILE_NAME.tmp")
        try {
            FileOutputStream(tmp).use { out ->
                out.write(wrapped)
                out.flush()
                out.fd.sync()
            }
            check(wrapper.unwrap(tmp.readBytes()).contentEquals(key)) { "Anahtar dosyası doğrulanamadı" }
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (e: Exception) {
            tmp.delete()
            val reason = if (e is IOException) {
                DatabaseKeyUnavailableException.Reason.WRITE_FAILED
            } else {
                DatabaseKeyUnavailableException.Reason.UNREADABLE
            }
            throw DatabaseKeyUnavailableException(reason, e)
        }
        return key.toHex()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    companion object {
        const val FILE_NAME = "db_key.bin"
        private const val KEY_BYTES = 32
    }
}
