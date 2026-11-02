package com.kisisel.finans.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.kisisel.finans.domain.model.OccurrenceStatus

/** Faturanın bir dönemi. Ödeme yalnızca ilgili dönem kaydını kapatır; eski dönemler değişmez. */
@Entity(
    tableName = "invoice_occurrences",
    foreignKeys = [
        ForeignKey(
            entity = InvoiceTemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["templateId", "periodKey"], unique = true),
        Index(value = ["status", "dueDate"])
    ]
)
data class InvoiceOccurrenceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: Long,
    val periodKey: String,
    val dueDate: Long,
    /** Değişken tutarlı faturada fatura gelene kadar boş olabilir. */
    val amountKurus: Long? = null,
    val status: OccurrenceStatus = OccurrenceStatus.UNPAID,
    val paidAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long
)
