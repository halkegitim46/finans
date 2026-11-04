package com.kisisel.finans.backup

import com.kisisel.finans.domain.FinanceException
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Yedek dosyası şifreleme: PBKDF2-HMAC-SHA256 (yedek parolası) → AES-256-GCM.
 *
 * Dosya düzeni: "KFTB" | sürüm(1) | iterasyon(4) | tuz(16) | nonce(12) | anahtar kontrolü(8) | şifreli veri + GCM etiketi.
 * Başlık GCM'de ek doğrulanan veri (AAD) olarak korunur; başlık veya veri değişirse çözme başarısız olur.
 * Anahtar kontrolü yanlış parolayı "bozuk dosya"dan ayırt etmek içindir.
 */
object BackupCrypto {
    const val DEFAULT_ITERATIONS = 600_000
    const val NOT_BACKUP = "Bu dosya bir Kişisel Finans Takip yedeği değil."
    const val UNSUPPORTED = "Bu yedek desteklenmeyen bir sürüme ait."
    const val WRONG_PASSWORD = "Yedek parolası hatalı."
    const val CORRUPTED = "Yedek dosyası bozulmuş veya değiştirilmiş."

    private val MAGIC = byteArrayOf(0x4B, 0x46, 0x54, 0x42)   // "KFTB"
    private const val VERSION: Byte = 1
    private const val SALT_BYTES = 16
    private const val NONCE_BYTES = 12
    private const val CHECK_BYTES = 8
    private const val HEADER_BYTES = 4 + 1 + 4 + SALT_BYTES + NONCE_BYTES + CHECK_BYTES
    private const val TAG_BITS = 128
    private const val MAX_ITERATIONS = 5_000_000

    fun encrypt(
        plain: ByteArray,
        password: CharArray,
        iterations: Int = DEFAULT_ITERATIONS,
        random: SecureRandom = SecureRandom()
    ): ByteArray {
        val salt = ByteArray(SALT_BYTES).also { random.nextBytes(it) }
        val nonce = ByteArray(NONCE_BYTES).also { random.nextBytes(it) }
        val key = deriveKey(password, salt, iterations)
        val header = ByteBuffer.allocate(HEADER_BYTES)
            .put(MAGIC).put(VERSION).putInt(iterations).put(salt).put(nonce).put(keyCheck(key))
            .array()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(header)
        return header + cipher.doFinal(plain)
    }

    /** Başarısızlıkta kullanıcıya gösterilebilir [FinanceException.BackupInvalid] fırlatır. */
    fun decrypt(file: ByteArray, password: CharArray): ByteArray {
        if (file.size < HEADER_BYTES + TAG_BITS / 8 || !file.copyOfRange(0, 4).contentEquals(MAGIC)) {
            throw FinanceException.BackupInvalid(NOT_BACKUP)
        }
        val buf = ByteBuffer.wrap(file)
        buf.position(4)
        if (buf.get() != VERSION) throw FinanceException.BackupInvalid(UNSUPPORTED)
        val iterations = buf.int
        if (iterations !in 1..MAX_ITERATIONS) throw FinanceException.BackupInvalid(CORRUPTED)
        val salt = ByteArray(SALT_BYTES).also { buf.get(it) }
        val nonce = ByteArray(NONCE_BYTES).also { buf.get(it) }
        val check = ByteArray(CHECK_BYTES).also { buf.get(it) }

        val key = deriveKey(password, salt, iterations)
        if (!MessageDigest.isEqual(check, keyCheck(key))) throw FinanceException.BackupInvalid(WRONG_PASSWORD)

        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
            cipher.updateAAD(file, 0, HEADER_BYTES)
            cipher.doFinal(file, HEADER_BYTES, file.size - HEADER_BYTES)
        } catch (e: GeneralSecurityException) {
            throw FinanceException.BackupInvalid(CORRUPTED)
        }
    }

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun keyCheck(key: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest("kft-keycheck".toByteArray() + key).copyOf(CHECK_BYTES)
}
