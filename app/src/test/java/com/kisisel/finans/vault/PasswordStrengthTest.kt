package com.kisisel.finans.vault

import com.kisisel.finans.vault.PasswordGenerator.Options
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordStrengthTest {

    @Test fun emptyAndCommonPasswordsAreVeryWeak() {
        assertEquals(Strength.VERY_WEAK, PasswordStrength.rate(""))
        assertEquals(Strength.VERY_WEAK, PasswordStrength.rate("password"))
        assertEquals(Strength.VERY_WEAK, PasswordStrength.rate("Password"))
        assertEquals(Strength.VERY_WEAK, PasswordStrength.rate("12345678"))
        assertEquals(Strength.VERY_WEAK, PasswordStrength.rate("Sifre"))
    }

    @Test fun repetitionAndShortLengthAreVeryWeak() {
        assertEquals(Strength.VERY_WEAK, PasswordStrength.rate("aaaaaaaaaaaa"))
        assertEquals(Strength.VERY_WEAK, PasswordStrength.rate("kitap"))
    }

    @Test fun mixedLongPasswordsRateHigher() {
        assertTrue(PasswordStrength.rate("Tr0ub4dor&3").ordinal >= Strength.FAIR.ordinal)
        assertTrue(PasswordStrength.bits("abcd1234") < PasswordStrength.bits("abcd1234XYZ!"))
        assertTrue(PasswordStrength.bits("a1b2c3d4e5f6") > PasswordStrength.bits("a1b2c3"))
    }

    @Test fun generatedPasswordsAreVeryStrong() {
        repeat(50) {
            assertEquals(Strength.VERY_STRONG, PasswordStrength.rate(PasswordGenerator.generate(Options(length = 16))))
        }
    }

    @Test fun thresholdsAreOrdered() {
        val order = listOf("a", "kedi2024", "Kedi2024!", "Tr0ub4dor&3xZ", "q7#Lm2\$9Wp!zXv4Nb8")
            .map { PasswordStrength.rate(it).ordinal }
        assertEquals(order.sorted(), order)
    }
}
