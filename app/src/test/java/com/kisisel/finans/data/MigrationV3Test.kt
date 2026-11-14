package com.kisisel.finans.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kisisel.finans.data.local.KisiselFinansDatabase
import com.kisisel.finans.data.local.Migrations
import com.kisisel.finans.data.local.entity.AgendaItemEntity
import com.kisisel.finans.data.local.entity.PersonEntity
import com.kisisel.finans.data.local.entity.VaultItemEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Sürüm 2 → 3 (şifre kasası) yükseltmesinin gerçek sınaması. Sürüm 2 görünümü: güncel Room dosyasından `vault_items`
 * silinip `user_version = 2` yapılır; yükseltmeden sonra Room tüm şemayı entity tanımlarına göre doğrular.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class MigrationV3Test {
    private lateinit var context: Context
    private val name = "migration-v3-test.db"

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(name)
    }

    @After fun tearDown() {
        context.deleteDatabase(name)
    }

    private class Seed(val personId: Long, val agendaId: Long)

    private fun createVersion2File(): Seed {
        val db = Room.databaseBuilder(context, KisiselFinansDatabase::class.java, name).allowMainThreadQueries().build()
        val personId = runBlocking { db.personDao().insert(PersonEntity(fullName = "Eski Kişi", createdAt = 1, updatedAt = 1)) }
        val agendaId = runBlocking {
            db.agendaDao().insert(AgendaItemEntity(title = "Eski not", date = 20_000, personId = personId, createdAt = 1, updatedAt = 1))
        }
        db.close()

        val raw = SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE)
        raw.execSQL("DROP TABLE vault_items")
        raw.execSQL("PRAGMA user_version = 2")
        raw.close()
        return Seed(personId, agendaId)
    }

    private fun upgraded(): KisiselFinansDatabase =
        Room.databaseBuilder(context, KisiselFinansDatabase::class.java, name)
            .addMigrations(*Migrations.ALL)
            .allowMainThreadQueries()
            .build()

    @Test fun upgradeCreatesVaultTableMatchingEntityAndKeepsExistingData() {
        val seed = createVersion2File()
        val db = upgraded()
        try {
            assertEquals("Eski Kişi", runBlocking { db.personDao().getById(seed.personId)?.fullName })
            assertEquals("Eski not", runBlocking { db.agendaDao().getById(seed.agendaId)?.title })

            val id = runBlocking {
                db.vaultDao().insert(VaultItemEntity(title = "Banka", username = "ali", secret = "s3cr3t", createdAt = 1, updatedAt = 1))
            }
            val item = runBlocking { db.vaultDao().getById(id) }
            assertNotNull(item)
            assertEquals("s3cr3t", item!!.secret)
        } finally {
            db.close()
        }
    }

    @Test fun indexCreatedByMigrationExists() {
        createVersion2File()
        val db = upgraded()
        try {
            val names = mutableListOf<String>()
            db.openHelper.readableDatabase.query("PRAGMA index_list(vault_items)").use { c ->
                val col = c.getColumnIndexOrThrow("name")
                while (c.moveToNext()) names += c.getString(col)
            }
            assertTrue(names.toString(), names.contains("index_vault_items_isArchived"))
        } finally {
            db.close()
        }
    }

    @Test fun missingMigrationNeverDestroysData() {
        createVersion2File()
        val bad = Room.databaseBuilder(context, KisiselFinansDatabase::class.java, name).allowMainThreadQueries().build()
        val failed = runCatching { bad.openHelper.writableDatabase }.isFailure
        runCatching { bad.close() }
        assertTrue("Migration yoksa yıkıcı geçiş yapılmamalı", failed)

        val ok = upgraded()
        try {
            assertEquals(1, runBlocking { ok.snapshotDao().persons().size })
        } finally {
            ok.close()
        }
    }
}
