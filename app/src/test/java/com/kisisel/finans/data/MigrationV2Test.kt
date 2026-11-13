package com.kisisel.finans.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kisisel.finans.data.local.KisiselFinansDatabase
import com.kisisel.finans.data.local.Migrations
import com.kisisel.finans.data.local.entity.AgendaItemEntity
import com.kisisel.finans.data.local.entity.PersonEntity
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
 * Sürüm 1 → 2 yükseltmesinin gerçek sınaması. Sürüm 1 görünümü, güncel Room'un kendi ürettiği tablolardan
 * `agenda_items` silinip `user_version = 1` yapılarak elde edilir; yükseltmeden sonra Room TÜM şemayı entity
 * tanımlarına göre doğrular (migration SQL'inde en küçük fark istisna fırlatır).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class MigrationV2Test {
    private lateinit var context: Context
    private val name = "migration-test.db"

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(name)
    }

    @After fun tearDown() {
        context.deleteDatabase(name)
    }

    /** Veri içeren sürüm 1 görünümlü dosya oluşturur ve kişi kimliğini döner. */
    private fun createVersion1File(): Long {
        val db = Room.databaseBuilder(context, KisiselFinansDatabase::class.java, name).allowMainThreadQueries().build()
        val personId = runBlocking { db.personDao().insert(PersonEntity(fullName = "Eski Kişi", createdAt = 1, updatedAt = 1)) }
        db.close()

        val raw = SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE)
        raw.execSQL("DROP TABLE agenda_items")
        raw.execSQL("PRAGMA user_version = 1")
        raw.close()
        return personId
    }

    @Test fun upgradeCreatesAgendaTableMatchingEntityAndKeepsExistingData() {
        val personId = createVersion1File()

        val upgraded = Room.databaseBuilder(context, KisiselFinansDatabase::class.java, name)
            .addMigrations(*Migrations.ALL)
            .allowMainThreadQueries()
            .build()
        try {
            assertEquals("Eski Kişi", runBlocking { upgraded.personDao().getById(personId)?.fullName })

            val id = runBlocking {
                upgraded.agendaDao().insert(
                    AgendaItemEntity(title = "Yükseltme sonrası", date = 20_000, timeMinutes = 870, personId = personId,
                        remindMinutesBefore = 60, createdAt = 1, updatedAt = 1)
                )
            }
            val item = runBlocking { upgraded.agendaDao().getById(id) }
            assertNotNull(item)
            assertEquals(870, item!!.timeMinutes)
            assertEquals(60, item.remindMinutesBefore)
        } finally {
            upgraded.close()
        }
    }

    @Test fun foreignKeyAndIndicesCreatedByMigrationWork() {
        createVersion1File()
        val upgraded = Room.databaseBuilder(context, KisiselFinansDatabase::class.java, name)
            .addMigrations(*Migrations.ALL)
            .allowMainThreadQueries()
            .build()
        try {
            val failed = runCatching {
                runBlocking { upgraded.agendaDao().insert(AgendaItemEntity(title = "x", date = 20_000, personId = 9_999, createdAt = 1, updatedAt = 1)) }
            }.isFailure
            assertTrue("Olmayan kişiye bağlı ajanda kaydı yabancı anahtarla reddedilmeli", failed)

            val names = mutableListOf<String>()
            upgraded.openHelper.readableDatabase.query("PRAGMA index_list(agenda_items)").use { c ->
                val col = c.getColumnIndexOrThrow("name")
                while (c.moveToNext()) names += c.getString(col)
            }
            assertTrue(names.toString(), names.containsAll(listOf("index_agenda_items_personId", "index_agenda_items_date")))
        } finally {
            upgraded.close()
        }
    }

    @Test fun missingMigrationIsNeverRepairedByDestroyingData() {
        createVersion1File()
        val db = Room.databaseBuilder(context, KisiselFinansDatabase::class.java, name).allowMainThreadQueries().build()
        val failed = runCatching { db.openHelper.writableDatabase }.isFailure
        runCatching { db.close() }
        assertTrue("Migration yoksa yıkıcı geçiş yapılmamalı, hata verilmeli", failed)

        // Veri hâlâ yerinde: doğru migration ile açılınca kişi okunabilir.
        val ok = Room.databaseBuilder(context, KisiselFinansDatabase::class.java, name)
            .addMigrations(*Migrations.ALL).allowMainThreadQueries().build()
        try {
            assertEquals(1, runBlocking { ok.snapshotDao().persons().size })
        } finally {
            ok.close()
        }
    }
}
