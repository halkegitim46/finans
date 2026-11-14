package com.kisisel.finans.vault

import kotlin.math.ln

enum class Strength(val label: String) {
    VERY_WEAK("Çok zayıf"), WEAK("Zayıf"), FAIR("Orta"), STRONG("Güçlü"), VERY_STRONG("Çok güçlü")
}

/**
 * Basit güç tahmini (entropi + yaygın şifre/tekrar cezası). Bir GÜVENCE değildir: yalnızca kullanıcıyı yönlendirir.
 */
object PasswordStrength {
    private val COMMON = setOf(
        "password", "passw0rd", "123456", "1234567", "12345678", "123456789", "1234567890", "qwerty", "qwerty123",
        "111111", "000000", "abc123", "iloveyou", "admin", "letmein", "welcome", "parola", "sifre", "şifre", "parola123", "sifre123"
    )

    fun bits(pw: String): Double {
        if (pw.isEmpty()) return 0.0
        var pool = 0
        if (pw.any { it.isLowerCase() }) pool += 26
        if (pw.any { it.isUpperCase() }) pool += 26
        if (pw.any { it.isDigit() }) pool += 10
        if (pw.any { !it.isLetterOrDigit() }) pool += 33
        if (pool == 0) pool = 26
        // Tekrar eden karakterler az bilgi taşır: etkin uzunluk, farklı karakter sayısının 2 katıyla sınırlı.
        val effective = minOf(pw.length, pw.toSet().size * 2)
        return effective * ln(pool.toDouble()) / ln(2.0)
    }

    fun rate(pw: String): Strength {
        if (pw.isEmpty() || pw.lowercase() in COMMON) return Strength.VERY_WEAK
        val b = bits(pw)
        return when {
            b < 28 -> Strength.VERY_WEAK
            b < 36 -> Strength.WEAK
            b < 60 -> Strength.FAIR
            b < 80 -> Strength.STRONG
            else -> Strength.VERY_STRONG
        }
    }
}
