package com.kisisel.finans

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kisisel.finans.security.AndroidKeystoreKeyWrapper
import com.kisisel.finans.security.DatabaseKeyStore
import com.kisisel.finans.security.DatabaseKeyUnavailableException
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Gerçek Android Keystore: sarma/açma, değiştirilmiş veri ve anahtar kaybı senaryosu. Emülatör/cihaz gerektirir. */
@RunWith(AndroidJUnit4::class)
class KeystoreInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var dir: File

    @Before fun setUp() {
        dir = File(context.cacheDir, "kt-${System.nanoTime()}").apply { mkdirs() }
    }

    @After fun tearDown() {
        dir.deleteRecursively()
        runCatching { AndroidKeystoreKeyWrapper.deleteKey() }
    }

    @Test fun wrapUnwrapRoundTrip() {
        val wrapper = AndroidKeystoreKeyWrapper()
        val data = ByteArray(32) { it.toByte() }
        assertArrayEquals(data, wrapper.unwrap(wrapper.wrap(data)))
    }

    @Test fun tamperedWrappedDataFails() {
        val wrapper = AndroidKeystoreKeyWrapper()
        val wrapped = wrapper.wrap(ByteArray(32) { it.toByte() })
        wrapped[wrapped.size - 1] = (wrapped[wrapped.size - 1].toInt() xor 1).toByte()
        assertThrows(Exception::class.java) { wrapper.unwrap(wrapped) }
    }

    @Test fun lostKeystoreKeyIsReportedAsUnreadable() {
        val file = File(dir, DatabaseKeyStore.FILE_NAME)
        DatabaseKeyStore(file, AndroidKeystoreKeyWrapper()).getOrCreateKeyHex()
        AndroidKeystoreKeyWrapper.deleteKey()                       // anahtar kaybı simülasyonu
        val error = assertThrows(DatabaseKeyUnavailableException::class.java) {
            DatabaseKeyStore(file, AndroidKeystoreKeyWrapper()).getOrCreateKeyHex()
        }
        assertEquals(DatabaseKeyUnavailableException.Reason.UNREADABLE, error.reason)
    }

    @Test fun missingKeyFileWithExistingDatabaseIsReportedAsMissing() {
        val file = File(dir, DatabaseKeyStore.FILE_NAME)
        val error = assertThrows(DatabaseKeyUnavailableException::class.java) {
            DatabaseKeyStore(file, AndroidKeystoreKeyWrapper()) { true }.getOrCreateKeyHex()
        }
        assertEquals(DatabaseKeyUnavailableException.Reason.MISSING, error.reason)
    }
}
