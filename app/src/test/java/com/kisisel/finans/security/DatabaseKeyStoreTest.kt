package com.kisisel.finans.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Gerçek Keystore yerine tersinir sahte sarmalayıcı (JVM testi). */
private class XorWrapper : KeyWrapper {
    override fun wrap(plain: ByteArray) = byteArrayOf(0x7F) + plain.map { (it.toInt() xor 0x5A).toByte() }
    override fun unwrap(wrapped: ByteArray): ByteArray {
        require(wrapped.isNotEmpty() && wrapped[0] == 0x7F.toByte()) { "Bozuk" }
        return wrapped.drop(1).map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
    }
}

class DatabaseKeyStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun store(dir: File = tmp.root) = DatabaseKeyStore(File(dir, DatabaseKeyStore.FILE_NAME), XorWrapper())

    @Test fun createsOnceAndIsStable() {
        val s = store()
        assertFalse(s.exists())
        val first = s.getOrCreateKeyHex()
        assertTrue(s.exists())
        assertEquals(first, s.getOrCreateKeyHex())
        assertEquals(first, store().getOrCreateKeyHex())   // yeni örnek aynı dosyadan okur
    }

    @Test fun keyIsFullLengthHex() {
        val key = store().getOrCreateKeyHex()
        assertEquals(64, key.length)
        assertTrue(key.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test fun fileDoesNotContainRawKey() {
        val s = store()
        val hex = s.getOrCreateKeyHex()
        val raw = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val fileBytes = File(tmp.root, DatabaseKeyStore.FILE_NAME).readBytes()
        assertFalse(fileBytes.toList().windowed(raw.size).any { it == raw.toList() })
    }

    @Test fun differentInstallsGetDifferentKeys() {
        val a = store(tmp.newFolder()).getOrCreateKeyHex()
        val b = store(tmp.newFolder()).getOrCreateKeyHex()
        assertNotEquals(a, b)
    }

    @Test fun corruptedFileFailsLoudly() {
        val s = store()
        s.getOrCreateKeyHex()
        File(tmp.root, DatabaseKeyStore.FILE_NAME).writeBytes(byteArrayOf(1, 2, 3))
        assertThrows(Exception::class.java) { store().getOrCreateKeyHex() }
    }

    // ---- anahtar kaybı / dayanıklılık ----
    private fun reasonOf(block: () -> Unit): DatabaseKeyUnavailableException.Reason =
        assertThrows(DatabaseKeyUnavailableException::class.java, block).reason

    @Test fun existingDatabaseWithoutKeyFileNeverGetsNewKey() {
        val s = DatabaseKeyStore(File(tmp.root, DatabaseKeyStore.FILE_NAME), XorWrapper()) { true }
        assertEquals(DatabaseKeyUnavailableException.Reason.MISSING, reasonOf { s.getOrCreateKeyHex() })
        assertFalse("yeni anahtar dosyası üretilmemeli", File(tmp.root, DatabaseKeyStore.FILE_NAME).exists())
    }

    @Test fun firstRunWithDatabaseAbsentCreatesKey() {
        val s = DatabaseKeyStore(File(tmp.root, DatabaseKeyStore.FILE_NAME), XorWrapper()) { false }
        assertEquals(64, s.getOrCreateKeyHex().length)
    }

    @Test fun existingKeyStillWorksWhenDatabaseExists() {
        val file = File(tmp.root, DatabaseKeyStore.FILE_NAME)
        val first = DatabaseKeyStore(file, XorWrapper()) { false }.getOrCreateKeyHex()
        assertEquals(first, DatabaseKeyStore(file, XorWrapper()) { true }.getOrCreateKeyHex())
    }

    @Test fun unreadableKeyFileIsReportedAsUnreadable() {
        val file = File(tmp.root, DatabaseKeyStore.FILE_NAME)
        DatabaseKeyStore(file, XorWrapper()).getOrCreateKeyHex()
        val failing = object : KeyWrapper {
            override fun wrap(plain: ByteArray) = plain
            override fun unwrap(wrapped: ByteArray): ByteArray = throw java.security.GeneralSecurityException("anahtar geçersiz")
        }
        assertEquals(DatabaseKeyUnavailableException.Reason.UNREADABLE, reasonOf { DatabaseKeyStore(file, failing).getOrCreateKeyHex() })
    }

    @Test fun wrongSizedKeyFileIsUnreadable() {
        val file = File(tmp.root, DatabaseKeyStore.FILE_NAME)
        file.writeBytes(XorWrapper().wrap(ByteArray(10)))
        assertEquals(DatabaseKeyUnavailableException.Reason.UNREADABLE, reasonOf { DatabaseKeyStore(file, XorWrapper()).getOrCreateKeyHex() })
    }

    @Test fun brokenKeystoreOnFirstRunWritesNothing() {
        val file = File(tmp.root, DatabaseKeyStore.FILE_NAME)
        val lying = object : KeyWrapper {
            override fun wrap(plain: ByteArray) = plain
            override fun unwrap(wrapped: ByteArray) = ByteArray(32)   // sarılan anahtarı geri veremiyor
        }
        assertEquals(DatabaseKeyUnavailableException.Reason.UNREADABLE, reasonOf { DatabaseKeyStore(file, lying).getOrCreateKeyHex() })
        assertFalse(file.exists())
        assertFalse(File(tmp.root, DatabaseKeyStore.FILE_NAME + ".tmp").exists())
    }

    @Test fun noLeftoverTempFileAfterSuccess() {
        DatabaseKeyStore(File(tmp.root, DatabaseKeyStore.FILE_NAME), XorWrapper()).getOrCreateKeyHex()
        assertFalse(File(tmp.root, DatabaseKeyStore.FILE_NAME + ".tmp").exists())
    }
}
