package com.kisisel.finans.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * PBKDF2-HMAC-SHA256 ile parola/PIN doğrulama kaydı. Açık metin saklanmaz.
 * Kayıt biçimi: "v1:<iterasyon>:<tuz base64>:<özet base64>".
 */
object PasswordHasher {
    const val DEFAULT_ITERATIONS = 600_000
    private const val SALT_BYTES = 16
    private const val HASH_BITS = 256
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"

    fun encode(
        secret: CharArray,
        iterations: Int = DEFAULT_ITERATIONS,
        random: SecureRandom = SecureRandom()
    ): String {
        val salt = ByteArray(SALT_BYTES).also { random.nextBytes(it) }
        val hash = pbkdf2(secret, salt, iterations)
        val b64 = Base64.getEncoder()
        return "v1:$iterations:${b64.encodeToString(salt)}:${b64.encodeToString(hash)}"
    }

    /** Sabit zamanlı karşılaştırma. Bozuk kayıt için false. */
    fun verify(secret: CharArray, record: String): Boolean {
        val parts = record.split(':')
        if (parts.size != 4 || parts[0] != "v1") return false
        return try {
            val iterations = parts[1].toInt()
            if (iterations < 1) return false
            val salt = Base64.getDecoder().decode(parts[2])
            val expected = Base64.getDecoder().decode(parts[3])
            MessageDigest.isEqual(expected, pbkdf2(secret, salt, iterations))
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    private fun pbkdf2(secret: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(secret, salt, iterations, HASH_BITS)
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}

object PasswordPolicy {
    const val MIN_PASSWORD = 8
    const val MAX_PASSWORD = 128

    /** Geçerliyse null, değilse kullanıcıya gösterilecek mesaj. */
    fun validatePassword(pw: String): String? = when {
        pw.isBlank() -> "Parola boş olamaz."
        pw.length < MIN_PASSWORD -> "Parola en az $MIN_PASSWORD karakter olmalı."
        pw.length > MAX_PASSWORD -> "Parola en fazla $MAX_PASSWORD karakter olabilir."
        pw.all { it == pw[0] } -> "Parola tek karakterden oluşamaz."
        else -> null
    }

    fun validatePin(pin: String): String? = when {
        pin.isEmpty() || !pin.all { it.isDigit() } -> "PIN yalnızca rakamlardan oluşmalı."
        pin.length !in 4..8 -> "PIN 4-8 haneli olmalı."
        pin.all { it == pin[0] } -> "PIN tek rakamdan oluşamaz."
        else -> null
    }
}
