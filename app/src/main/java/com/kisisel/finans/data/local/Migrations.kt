package com.kisisel.finans.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Şema değiştiğinde buraya açık Migration eklenir. Yıkıcı migrasyon (verileri silme) KULLANILMAZ.
 * Şema dosyaları app/schemas altında sürüm kontrolüne alınır.
 *
 * SQL, Room'un entity'den ürettiğiyle BİREBİR aynı olmalıdır (Room yükseltme sonrası şemayı doğrular);
 * `MigrationV2Test` bunu gerçek bir yükseltmeyle sınar.
 */
object Migrations {

    /** Sürüm 2: ajanda notları / özel hatırlatıcılar (`agenda_items`). Mevcut tablolara dokunmaz. */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `agenda_items` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`title` TEXT NOT NULL, " +
                    "`note` TEXT NOT NULL, " +
                    "`date` INTEGER NOT NULL, " +
                    "`timeMinutes` INTEGER, " +
                    "`personId` INTEGER, " +
                    "`remindMinutesBefore` INTEGER, " +
                    "`isDone` INTEGER NOT NULL, " +
                    "`doneAt` INTEGER, " +
                    "`isArchived` INTEGER NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL, " +
                    "`updatedAt` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`personId`) REFERENCES `persons`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_agenda_items_personId` ON `agenda_items` (`personId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_agenda_items_date` ON `agenda_items` (`date`)")
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}
