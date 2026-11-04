package com.kisisel.finans.backup

import com.kisisel.finans.domain.FinanceException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupCryptoTest {
    private val iter = 1_000
    private val plain = "kuruş:150050 ödeme gizli-veri".toByteArray()

    private fun enc(password: String = "yedek-parola-1") =
        BackupCrypto.encrypt(plain, password.toCharArray(), iter)

    private fun failure(block: () -> Unit): String =
        assertThrows(FinanceException.BackupInvalid::class.java, block).message!!

    @Test fun roundTrip() {
        assertArrayEquals(plain, BackupCrypto.decrypt(enc(), "yedek-parola-1".toCharArray()))
    }

    @Test fun ciphertextDoesNotLeakPlaintextAndIsRandomized() {
        val a = enc()
        val b = enc()
        assertNotEquals(a.toList(), b.toList())                       // tuz + nonce farklı
        assertFalse(String(a, Charsets.ISO_8859_1).contains("gizli-veri"))
        assertEquals("KFTB", String(a.copyOfRange(0, 4)))
    }

    @Test fun wrongPasswordIsReportedAsSuch() {
        val msg = failure { BackupCrypto.decrypt(enc(), "yanlış-parola".toCharArray()) }
        assertEquals(BackupCrypto.WRONG_PASSWORD, msg)
    }

    @Test fun tamperedCiphertextIsDetected() {
        val f = enc()
        f[f.size - 1] = (f[f.size - 1].toInt() xor 0x01).toByte()
        assertEquals(BackupCrypto.CORRUPTED, failure { BackupCrypto.decrypt(f, "yedek-parola-1".toCharArray()) })
    }

    @Test fun tamperedMiddleOfCiphertextIsDetected() {
        val f = enc()
        val i = f.size - 20
        f[i] = (f[i].toInt() xor 0x40).toByte()
        assertEquals(BackupCrypto.CORRUPTED, failure { BackupCrypto.decrypt(f, "yedek-parola-1".toCharArray()) })
    }

    @Test fun tamperedNonceInHeaderIsDetected() {
        val f = enc()
        f[4 + 1 + 4 + 16] = (f[4 + 1 + 4 + 16].toInt() xor 0x01).toByte()   // nonce'un ilk baytı
        assertEquals(BackupCrypto.CORRUPTED, failure { BackupCrypto.decrypt(f, "yedek-parola-1".toCharArray()) })
    }

    @Test fun tamperedSaltLooksLikeWrongPassword() {
        val f = enc()
        f[4 + 1 + 4] = (f[4 + 1 + 4].toInt() xor 0x01).toByte()              // tuzun ilk baytı
        assertEquals(BackupCrypto.WRONG_PASSWORD, failure { BackupCrypto.decrypt(f, "yedek-parola-1".toCharArray()) })
    }

    @Test fun notABackupFile() {
        assertEquals(BackupCrypto.NOT_BACKUP, failure { BackupCrypto.decrypt("merhaba dünya".toByteArray(), "x".toCharArray()) })
        assertEquals(BackupCrypto.NOT_BACKUP, failure { BackupCrypto.decrypt(ByteArray(0), "x".toCharArray()) })
        val f = enc()
        f[0] = 'X'.code.toByte()
        assertEquals(BackupCrypto.NOT_BACKUP, failure { BackupCrypto.decrypt(f, "yedek-parola-1".toCharArray()) })
    }

    @Test fun truncatedFileIsRejected() {
        val f = enc()
        val msg = failure { BackupCrypto.decrypt(f.copyOf(f.size - 5), "yedek-parola-1".toCharArray()) }
        assertEquals(BackupCrypto.CORRUPTED, msg)
        failure { BackupCrypto.decrypt(f.copyOf(30), "yedek-parola-1".toCharArray()) }
    }

    @Test fun unsupportedVersion() {
        val f = enc()
        f[4] = 2
        assertEquals(BackupCrypto.UNSUPPORTED, failure { BackupCrypto.decrypt(f, "yedek-parola-1".toCharArray()) })
    }

    @Test fun absurdIterationCountRejected() {
        val f = enc()
        f[5] = 0x7F   // iterasyon alanının en anlamlı baytı: çok büyük
        assertEquals(BackupCrypto.CORRUPTED, failure { BackupCrypto.decrypt(f, "yedek-parola-1".toCharArray()) })
    }
}
