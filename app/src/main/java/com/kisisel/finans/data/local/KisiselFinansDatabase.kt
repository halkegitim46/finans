package com.kisisel.finans.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.kisisel.finans.data.local.dao.InvoiceDao
import com.kisisel.finans.data.local.dao.PaymentDao
import com.kisisel.finans.data.local.dao.PersonDao
import com.kisisel.finans.data.local.dao.SettingsDao
import com.kisisel.finans.data.local.dao.SnapshotDao
import com.kisisel.finans.data.local.dao.TransactionDao
import com.kisisel.finans.data.local.entity.AppSettingEntity
import com.kisisel.finans.data.local.entity.InvoiceOccurrenceEntity
import com.kisisel.finans.data.local.entity.InvoiceTemplateEntity
import com.kisisel.finans.data.local.entity.PaymentEntity
import com.kisisel.finans.data.local.entity.PersonEntity
import com.kisisel.finans.data.local.entity.TransactionEntity

@Database(
    entities = [
        PersonEntity::class,
        TransactionEntity::class,
        InvoiceTemplateEntity::class,
        InvoiceOccurrenceEntity::class,
        PaymentEntity::class,
        AppSettingEntity::class
    ],
    version = KisiselFinansDatabase.VERSION,
    exportSchema = true
)
abstract class KisiselFinansDatabase : RoomDatabase() {
    abstract fun personDao(): PersonDao
    abstract fun transactionDao(): TransactionDao
    abstract fun invoiceDao(): InvoiceDao
    abstract fun paymentDao(): PaymentDao
    abstract fun settingsDao(): SettingsDao
    abstract fun snapshotDao(): SnapshotDao

    companion object {
        /** Eski (şifresiz) veritabanı dosya adı. Şifreli sürüm için bkz. SecureDatabase.ENC_NAME. */
        const val NAME = "kisisel_finans.db"

        /** Room şema sürümü; yedek dosyalarının uyumluluk kontrolünde de kullanılır. */
        const val VERSION = 1
    }
}
