package com.kisisel.finans.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.kisisel.finans.data.local.entity.AppSettingEntity
import com.kisisel.finans.data.local.entity.InvoiceOccurrenceEntity
import com.kisisel.finans.data.local.entity.InvoiceTemplateEntity
import com.kisisel.finans.data.local.entity.PaymentEntity
import com.kisisel.finans.data.local.entity.PersonEntity
import com.kisisel.finans.data.local.entity.TransactionEntity

/** Tüm tabloları olduğu gibi okuma/yazma (şifreli veritabanına geçiş ve yedekleme için). */
@Dao
interface SnapshotDao {
    @Query("SELECT * FROM persons ORDER BY id") suspend fun persons(): List<PersonEntity>
    @Query("SELECT * FROM invoice_templates ORDER BY id") suspend fun invoiceTemplates(): List<InvoiceTemplateEntity>
    @Query("SELECT * FROM invoice_occurrences ORDER BY id") suspend fun invoiceOccurrences(): List<InvoiceOccurrenceEntity>
    @Query("SELECT * FROM transactions ORDER BY id") suspend fun transactions(): List<TransactionEntity>
    @Query("SELECT * FROM payments ORDER BY id") suspend fun payments(): List<PaymentEntity>
    @Query("SELECT * FROM app_settings ORDER BY settingKey") suspend fun settings(): List<AppSettingEntity>

    @Insert suspend fun insertPersons(items: List<PersonEntity>)
    @Insert suspend fun insertInvoiceTemplates(items: List<InvoiceTemplateEntity>)
    @Insert suspend fun insertInvoiceOccurrences(items: List<InvoiceOccurrenceEntity>)
    @Insert suspend fun insertTransactions(items: List<TransactionEntity>)
    @Insert suspend fun insertPayments(items: List<PaymentEntity>)
    @Insert suspend fun insertSettings(items: List<AppSettingEntity>)

    @Query(
        "SELECT (SELECT COUNT(*) FROM persons) + (SELECT COUNT(*) FROM invoice_templates) + " +
            "(SELECT COUNT(*) FROM invoice_occurrences) + (SELECT COUNT(*) FROM transactions) + " +
            "(SELECT COUNT(*) FROM payments) + (SELECT COUNT(*) FROM app_settings)"
    )
    suspend fun totalRows(): Int
}
