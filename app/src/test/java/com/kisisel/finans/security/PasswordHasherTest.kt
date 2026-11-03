package com.kisisel.finans.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordHasherTest {
    private val iter = 1_000   // test hızı için düşük

    @Test fun verifiesCorrectSecretOnly() {
        val record = PasswordHasher.encode("doğru-parola-1".toCharArray(), iter)
        assertTrue(PasswordHasher.verify("doğru-parola-1".toCharArray(), record))
        assertFalse(PasswordHasher.verify("yanlış-parola".toCharArray(), record))
        assertFalse(PasswordHasher.verify("".toCharArray(), record))
    }

    @Test fun recordIsNotPlaintextAndHasExpectedShape() {
        val record = PasswordHasher.encode("gizli-parola".toCharArray(), iter)
        assertFalse(record.contains("gizli-parola"))
        val parts = record.split(':')
        assertEquals(4, parts.size)
        assertEquals("v1", parts[0])
        assertEquals("1000", parts[1])
    }

    @Test fun saltMakesRecordsDifferent() {
        val a = PasswordHasher.encode("aynı-parola".toCharArray(), iter)
        val b = PasswordHasher.encode("aynı-parola".toCharArray(), iter)
        assertNotEquals(a, b)
        assertTrue(PasswordHasher.verify("aynı-parola".toCharArray(), a))
        assertTrue(PasswordHasher.verify("aynı-parola".toCharArray(), b))
    }

    @Test fun malformedRecordsNeverVerify() {
        for (bad in listOf("", "v1", "v1:1:2", "v2:1000:AA:BB", "v1:x:AA:BB", "v1:0:AA:BB", "v1:1000:!!:!!")) {
            assertFalse(bad, PasswordHasher.verify("x".toCharArray(), bad))
        }
    }

    @Test fun passwordPolicy() {
        assertNull(PasswordPolicy.validatePassword("güçlü-parola-9"))
        assertEquals("Parola boş olamaz.", PasswordPolicy.validatePassword("   "))
        assertTrue(PasswordPolicy.validatePassword("kısa")!!.contains("en az"))
        assertTrue(PasswordPolicy.validatePassword("aaaaaaaa")!!.contains("tek karakter"))
        assertTrue(PasswordPolicy.validatePassword("a".repeat(129) + "b")!!.contains("en fazla"))
    }

    @Test fun pinPolicy() {
        assertNull(PasswordPolicy.validatePin("2468"))
        assertNull(PasswordPolicy.validatePin("12345678"))
        assertTrue(PasswordPolicy.validatePin("123")!!.contains("4-8"))
        assertTrue(PasswordPolicy.validatePin("123456789")!!.contains("4-8"))
        assertTrue(PasswordPolicy.validatePin("12a4")!!.contains("rakam"))
        assertTrue(PasswordPolicy.validatePin("1111")!!.contains("tek rakam"))
        assertTrue(PasswordPolicy.validatePin("")!!.contains("rakam"))
    }
}
