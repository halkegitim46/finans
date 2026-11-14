package com.kisisel.finans.vault

import com.kisisel.finans.vault.PasswordGenerator.DIGITS
import com.kisisel.finans.vault.PasswordGenerator.LOWER
import com.kisisel.finans.vault.PasswordGenerator.Options
import com.kisisel.finans.vault.PasswordGenerator.SYMBOLS
import com.kisisel.finans.vault.PasswordGenerator.UPPER
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordGeneratorTest {

    @Test fun lengthAndEverySelectedClassAreGuaranteed() {
        repeat(300) {
            val pw = PasswordGenerator.generate(Options(length = 8))
            assertEquals(8, pw.length)
            assertTrue(pw, pw.any { it in LOWER })
            assertTrue(pw, pw.any { it in UPPER })
            assertTrue(pw, pw.any { it in DIGITS })
            assertTrue(pw, pw.any { it in SYMBOLS })
            assertTrue(pw, pw.all { it in LOWER + UPPER + DIGITS + SYMBOLS })
        }
    }

    @Test fun onlySelectedClassesAreUsed() {
        repeat(100) {
            val digitsOnly = PasswordGenerator.generate(Options(length = 20, lower = false, upper = false, symbols = false))
            assertTrue(digitsOnly, digitsOnly.all { it in DIGITS })
            val noSymbols = PasswordGenerator.generate(Options(length = 20, symbols = false))
            assertTrue(noSymbols, noSymbols.none { it in SYMBOLS })
        }
    }

    @Test fun ambiguousCharactersAreExcluded() {
        repeat(200) {
            val pw = PasswordGenerator.generate(Options(length = 64))
            assertTrue(pw, pw.none { it in "lIO01" })
        }
    }

    @Test fun invalidOptionsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            PasswordGenerator.generate(Options(lower = false, upper = false, digits = false, symbols = false))
        }
        assertThrows(IllegalArgumentException::class.java) { PasswordGenerator.generate(Options(length = 7)) }
        assertThrows(IllegalArgumentException::class.java) { PasswordGenerator.generate(Options(length = 65)) }
    }

    @Test fun outputsDifferBetweenCalls() {
        val set = (1..50).map { PasswordGenerator.generate(Options(length = 16)) }.toSet()
        assertEquals(50, set.size)
    }

    @Test fun allPoolCharactersAppearOverManyDraws() {
        val seen = HashSet<Char>()
        repeat(200) { PasswordGenerator.generate(Options(length = 64, lower = false, upper = false, symbols = false)).forEach(seen::add) }
        assertEquals(DIGITS.toSet(), seen)
    }
}
