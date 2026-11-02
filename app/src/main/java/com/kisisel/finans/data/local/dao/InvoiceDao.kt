package com.kisisel.finans.data.local.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import com.kisisel.finans.data.local.entity.InvoiceOccurrenceEntity
import com.kisisel.finans.data.local.entity.InvoiceTemplateEntity
import kotlinx.coroutines.flow.Flow

data class OccurrenceWithTemplate(
    @Embedded val occurrence: InvoiceOccurrenceEntity,
    @Relation(parentColumn = "templateId", entityColumn = "id")
    val template: InvoiceTemplateEntity
)

@Dao
interface InvoiceDao {
    // ---- şablon ----
    @Insert
    suspend fun insertTemplate(template: InvoiceTemplateEntity): Long

    @Update
    suspend fun updateTemplate(template: InvoiceTemplateEntity)

    @Query("SELECT * FROM invoice_templates WHERE id = :id")
    suspend fun getTemplate(id: Long): InvoiceTemplateEntity?

    @Query("SELECT * FROM invoice_templates WHERE isArchived = 0 ORDER BY name COLLATE NOCASE")
    fun observeTemplates(): Flow<List<InvoiceTemplateEntity>>

    @Query("SELECT * FROM invoice_templates WHERE isArchived = 0 AND isActive = 1")
    suspend fun getActiveTemplates(): List<InvoiceTemplateEntity>

    // ---- dönem ----
    /** (templateId, periodKey) benzersiz; var olan dönem tekrar oluşturulmaz (-1). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOccurrence(occurrence: InvoiceOccurrenceEntity): Long

    @Query("SELECT * FROM invoice_occurrences WHERE id = :id")
    suspend fun getOccurrence(id: Long): InvoiceOccurrenceEntity?

    @Transaction
    @Query("SELECT * FROM invoice_occurrences WHERE id = :id")
    fun observeOccurrence(id: Long): Flow<OccurrenceWithTemplate?>

    @Transaction
    @Query("SELECT * FROM invoice_occurrences WHERE templateId = :templateId ORDER BY dueDate DESC")
    fun observeOccurrencesOfTemplate(templateId: Long): Flow<List<OccurrenceWithTemplate>>

    @Transaction
    @Query(
        "SELECT * FROM invoice_occurrences WHERE status = 'UNPAID' " +
            "AND dueDate BETWEEN :fromDay AND :toDay ORDER BY dueDate"
    )
    fun observeUnpaidBetween(fromDay: Long, toDay: Long): Flow<List<OccurrenceWithTemplate>>

    /** Gecikmiş = ödenmemiş ve vadesi bugünden önce. Gecikme durumu kayıtta tutulmaz, tarihten hesaplanır. */
    @Transaction
    @Query("SELECT * FROM invoice_occurrences WHERE status = 'UNPAID' AND dueDate < :todayDay ORDER BY dueDate")
    fun observeOverdue(todayDay: Long): Flow<List<OccurrenceWithTemplate>>

    @Transaction
    @Query("SELECT * FROM invoice_occurrences WHERE status = 'UNPAID' ORDER BY dueDate")
    suspend fun getAllUnpaid(): List<OccurrenceWithTemplate>

    /** Yalnızca ödenmemiş dönem kapatılabilir; zaten ödenmişse 0 döner (çift ödeme engeli). */
    @Query("UPDATE invoice_occurrences SET status = 'PAID', paidAt = :now, updatedAt = :now WHERE id = :id AND status = 'UNPAID'")
    suspend fun markPaid(id: Long, now: Long): Int

    @Query("UPDATE invoice_occurrences SET status = 'CANCELLED', updatedAt = :now WHERE id = :id AND status = 'UNPAID'")
    suspend fun markCancelled(id: Long, now: Long): Int

    /** Değişken tutarlı faturada gelen tutarı girmek için (yalnızca ödenmemişse). */
    @Query("UPDATE invoice_occurrences SET amountKurus = :amountKurus, updatedAt = :now WHERE id = :id AND status = 'UNPAID'")
    suspend fun updateAmount(id: Long, amountKurus: Long, now: Long): Int
}
