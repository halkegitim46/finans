package com.kisisel.finans.data.local

import android.content.Context
import androidx.room.Room
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/** SQLCipher (AES-256) ile şifreli Room veritabanı. */
object SecureDatabase {
    const val ENC_NAME = "kisisel_finans_enc.db"

    fun open(context: Context, keyHex: String): KisiselFinansDatabase {
        System.loadLibrary("sqlcipher")
        // Ham anahtar biçimi: KDF maliyeti olmadan doğrudan 256 bit anahtar.
        val passphrase = "x'$keyHex'".toByteArray(Charsets.US_ASCII)
        val db = Room.databaseBuilder(context, KisiselFinansDatabase::class.java, ENC_NAME)
            .openHelperFactory(SupportOpenHelperFactory(passphrase))
            .addMigrations(*Migrations.ALL)
            .build()
        LegacyDatabaseMigrator.migrateIfNeeded(context, db)
        return db
    }
}
