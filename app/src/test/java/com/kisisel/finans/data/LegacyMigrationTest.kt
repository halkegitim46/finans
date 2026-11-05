package com.kisisel.finans.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kisisel.finans.data.local.DatabaseSnapshot
import com.kisisel.finans.data.local.DatabaseSnapshotter
import com.kisisel.finans.data.local.KisiselFinansDatabase
import com.kisisel.finans.data.local.LegacyDatabaseMigrator
import com.kisisel.finans.data.repository.LedgerRepositoryImpl
import com.kisisel.finans.data.repository.PersonRepositoryImpl
import com.kisisel.finans.domain.model.NewClaim
import com.kisisel.finans.domain.model.NewPerson
import com.kisisel.finans.domain.model.NewSettlement
import com.kisisel.finans.domain.model.TransactionType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Şifresiz eski veritabanı dosyasının şifreli veritabanına taşınması (hedef olarak bellek içi Room kullanılır;
 * SQLCipher yerel kütüphanesi JVM'de yüklenemez, ama taşıma mantığı hedef sürücüden bağımsızdır).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class LegacyMigrationTest {

    private lateinit var context: Context
    private lateinit var target: KisiselFinansDatabase
    private val clock: Clock = Clock.fixed(Instant.parse("2026-11-04T09:00:00Z"), ZoneId.of("Europe/Istanbul"))
    private val day: LocalDate = LocalDate.of(2026, 11, 4)

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(KisiselFinansDatabase.NAME)
        target = Room.inMemoryDatabaseBuilder(context, KisiselFinansDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After fun tearDown() {
        target.close()
        context.deleteDatabase(KisiselFinansDatabase.NAME)
    }

    /** Eski sürümün oluşturacağı şifresiz dosya veritabanını örnek verilerle doldurur ve kapatır. */
    private fun createLegacyFile(): DatabaseSnapshot {
        val legacy = Room.databaseBuilder(context, KisiselFinansDatabase::class.java, KisiselFinansDatabase.NAME)
            .allowMainThreadQueries().build()
        return try {
            runBlocking {
                val persons = PersonRepositoryImpl(legacy.personDao(), clock)
                val ledger = LedgerRepositoryImpl(legacy, clock)
                val a = persons.create(NewPerson("Dayım", "0532", "not", 25_000))
                persons.create(NewPerson("Ayşe", null, "", -5_000))
                ledger.recordClaim(NewClaim(a, TransactionType.PAID_ON_BEHALF, 150_000, day, day.plusDays(5), "Elektrik", "k1"))
                ledger.settle(NewSettlement(a, TransactionType.COLLECTION, 50_000, day, "kısmi", "k2"))
                DatabaseSnapshotter.export(legacy)
            }
        } finally {
            legacy.close()
        }
    }

    @Test fun movesAllDataAndDeletesLegacyFileAfterVerification() {
        val expected = createLegacyFile()
        assertTrue(LegacyDatabaseMigrator.legacyExists(context))

        val migrated = LegacyDatabaseMigrator.migrateIfNeeded(context, target)

        assertTrue(migrated)
        assertFalse("Doğrulama sonrası eski dosya silinmeli", LegacyDatabaseMigrator.legacyExists(context))
        assertEquals(expected, runBlocking { DatabaseSnapshotter.export(target) })
        assertTrue(expected.totalRows > 0)
    }

    @Test fun nothingToDoWithoutLegacyFile() {
        assertFalse(LegacyDatabaseMigrator.legacyExists(context))
        assertFalse(LegacyDatabaseMigrator.migrateIfNeeded(context, target))
        assertTrue(runBlocking { DatabaseSnapshotter.isEmpty(target) })
    }

    @Test fun nonEmptyTargetIsNeverOverwrittenAndLegacyFileIsKept() {
        createLegacyFile()
        runBlocking { PersonRepositoryImpl(target.personDao(), clock).create(NewPerson("Mevcut", null, "", 0)) }
        val before = runBlocking { DatabaseSnapshotter.export(target) }

        assertFalse(LegacyDatabaseMigrator.migrateIfNeeded(context, target))

        assertTrue("Veri kaybı olmasın: eski dosya durmalı", LegacyDatabaseMigrator.legacyExists(context))
        assertEquals(before, runBlocking { DatabaseSnapshotter.export(target) })
    }

    @Test fun secondRunAfterSuccessDoesNothing() {
        createLegacyFile()
        assertTrue(LegacyDatabaseMigrator.migrateIfNeeded(context, target))
        assertFalse(LegacyDatabaseMigrator.migrateIfNeeded(context, target))
    }
}
