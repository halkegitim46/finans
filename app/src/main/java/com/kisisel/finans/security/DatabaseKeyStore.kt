package com.kisisel.finans.security

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Veritabanı şifreleme anahtarı: rastgele 256 bit, Keystore anahtarıyla sarmalanmış olarak dosyada saklanır.
 * Parolaya bağlı DEĞİLDİR; parola yalnızca uygulama kilididir. (Unutulan parola kurtarılamaz;
 * kurtarma yolu şifreli yedek veya verileri sıfırlamadır.)
 */
@Singleton
class DatabaseKeyStore(
    private val file: File,
    private val wrapper: KeyWrapper
) {
    @Inject
    constructor(@ApplicationContext context: Context) :
        this(File(context.filesDir, FILE_NAME), AndroidKeystoreKeyWrapper())

    /** 64 karakterlik onaltılık anahtar (ilk çağrıda üretilir). */
    @Synchronized
    fun getOrCreateKeyHex(): String {
        if (file.exists()) {
            val raw = wrapper.unwrap(file.readBytes())
            check(raw.size == KEY_BYTES) { "Anahtar dosyası geçersiz" }
            return raw.toHex()
        }
        val key = ByteArray(KEY_BYTES).also { SecureRandom().nextBytes(it) }
        val tmp = File(file.parentFile, "$FILE_NAME.tmp")
        tmp.writeBytes(wrapper.wrap(key))
        check(tmp.renameTo(file)) { "Anahtar dosyası yazılamadı" }
        return key.toHex()
    }

    fun exists(): Boolean = file.exists()

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    companion object {
        const val FILE_NAME = "db_key.bin"
        private const val KEY_BYTES = 32
    }
}
