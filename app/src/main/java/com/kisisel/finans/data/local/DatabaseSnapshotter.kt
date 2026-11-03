package com.kisisel.finans.data.local

import androidx.room.withTransaction
import com.kisisel.finans.data.local.entity.AppSettingEntity
import com.kisisel.finans.data.local.entity.InvoiceOccurrenceEntity
import com.kisisel.finans.data.local.entity.InvoiceTemplateEntity
import com.kisisel.finans.data.local.entity.PaymentEntity
import com.kisisel.finans.data.local.entity.PersonEntity
import com.kisisel.finans.data.local.entity.TransactionEntity

data class DatabaseSnapshot(
    val persons: List<PersonEntity>,
    val invoiceTemplates: List<InvoiceTemplateEntity>,
    val invoiceOccurrences: List<InvoiceOccurrenceEntity>,
    val transactions: List<TransactionEntity>,
    val payments: List<PaymentEntity>,
    val settings: List<AppSettingEntity>
) {
    val totalRows: Int
        get() = persons.size + invoiceTemplates.size + invoiceOccurrences.size +
            transactions.size + payments.size + settings.size
}

/** Veritabanının tam, tutarlı kopyası: dışa aktarma ve (kimlikler korunarak) içe aktarma. */
object DatabaseSnapshotter {

    suspend fun export(db: KisiselFinansDatabase): DatabaseSnapshot = db.withTransaction {
        val d = db.snapshotDao()
        DatabaseSnapshot(
            persons = d.persons(),
            invoiceTemplates = d.invoiceTemplates(),
            invoiceOccurrences = d.invoiceOccurrences(),
            transactions = d.transactions(),
            payments = d.payments(),
            settings = d.settings()
        )
    }

    suspend fun isEmpty(db: KisiselFinansDatabase): Boolean = db.snapshotDao().totalRows() == 0

    /**
     * Tek transaction: herhangi bir hata (ör. çakışan kimlik) tüm içe aktarmayı geri alır.
     * Yabancı anahtar sırası: kişi → şablon → dönem → işlem → ödeme dağıtımı.
     */
    suspend fun import(db: KisiselFinansDatabase, s: DatabaseSnapshot) {
        db.withTransaction {
            val d = db.snapshotDao()
            d.insertPersons(s.persons)
            d.insertInvoiceTemplates(s.invoiceTemplates)
            d.insertInvoiceOccurrences(s.invoiceOccurrences)
            d.insertTransactions(s.transactions)
            d.insertPayments(s.payments)
            d.insertSettings(s.settings)
        }
    }
}
