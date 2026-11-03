package com.kisisel.finans.data.local

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.runBlocking

/**
 * Önceki sürümlerde şifresiz oluşturulmuş veritabanını şifreli veritabanına taşır.
 * Eski dosya YALNIZCA taşıma ve sayı doğrulaması başarılıysa silinir; başarısızlıkta dokunulmaz
 * (veri kaybı olmaz). Hedef boş değilse taşıma yapılmaz.
 */
object LegacyDatabaseMigrator {

    fun legacyExists(context: Context): Boolean = context.getDatabasePath(KisiselFinansDatabase.NAME).exists()

    fun deleteLegacy(context: Context) {
        context.deleteDatabase(KisiselFinansDatabase.NAME)
    }

    /** true = veri taşındı ve eski dosya silindi. */
    fun migrateIfNeeded(context: Context, target: KisiselFinansDatabase): Boolean {
        if (!legacyExists(context)) return false
        return runBlocking {
            if (!DatabaseSnapshotter.isEmpty(target)) return@runBlocking false
            val legacy = Room.databaseBuilder(context, KisiselFinansDatabase::class.java, KisiselFinansDatabase.NAME)
                .addMigrations(*Migrations.ALL)
                .build()
            val success = try {
                val snapshot = DatabaseSnapshotter.export(legacy)
                DatabaseSnapshotter.import(target, snapshot)
                DatabaseSnapshotter.export(target) == snapshot
            } catch (e: Exception) {
                false
            } finally {
                legacy.close()
            }
            if (success) deleteLegacy(context)
            success
        }
    }
}
