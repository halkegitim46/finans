package com.kisisel.finans

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kisisel.finans.data.local.SecureDatabase
import com.kisisel.finans.data.local.entity.PersonEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Gerçek SQLCipher: anahtarla açma, yanlış anahtar reddi ve dosyanın düz SQLite olmaması. Emülatör/cihaz gerektirir. */
@RunWith(AndroidJUnit4::class)
class SecureDatabaseInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val keyA = "a1".repeat(32)   // 64 onaltılık karakter = 256 bit
    private val keyB = "b2".repeat(32)

    @Before fun clean() {
        context.deleteDatabase(SecureDatabase.ENC_NAME)
    }

    @After fun cleanup() {
        context.deleteDatabase(SecureDatabase.ENC_NAME)
    }

    private fun createWithOnePerson(): Long {
        val db = SecureDatabase.open(context, keyA)
        val id = runBlocking { db.personDao().insert(PersonEntity(fullName = "Test Kişi", createdAt = 1, updatedAt = 1)) }
        db.close()
        return id
    }

    @Test fun dataSurvivesReopenWithSameKey() {
        val id = createWithOnePerson()
        val again = SecureDatabase.open(context, keyA)
        val name = runBlocking { again.personDao().getById(id)?.fullName }
        again.close()
        assertEquals("Test Kişi", name)
    }

    @Test fun wrongKeyCannotOpenDatabase() {
        createWithOnePerson()
        val wrong = SecureDatabase.open(context, keyB)
        val failed = runCatching { wrong.openHelper.writableDatabase }.isFailure
        runCatching { wrong.close() }
        assertTrue("Yanlış anahtarla açılmamalı", failed)
    }

    @Test fun databaseFileIsNotPlainSqlite() {
        createWithOnePerson()
        val header = ByteArray(16)
        context.getDatabasePath(SecureDatabase.ENC_NAME).inputStream().use { it.read(header) }
        assertNotEquals("SQLite format 3\u0000", String(header, Charsets.ISO_8859_1))
    }
}
