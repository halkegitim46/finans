package com.kisisel.finans.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.kisisel.finans.domain.model.TransactionType

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["personId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = InvoiceOccurrenceEntity::class,
            parentColumns = ["id"],
            childColumns = ["occurrenceId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["personId"]),
        Index(value = ["occurrenceId"]),
        Index(value = ["transactionDate"]),
        Index(value = ["idempotencyKey"], unique = true)
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personId: Long,
    val type: TransactionType,
    /** Her zaman > 0 (kuruş). Doğrulama use case katmanında yapılır. */
    val amountKurus: Long,
    val transactionDate: Long,
    val dueDate: Long? = null,
    val description: String = "",
    /** Faturadan doğan işlemlerde ilgili dönem kaydı. */
    val occurrenceId: Long? = null,
    /** Aynı işlemin iki kez kaydedilmesini önler (UNIQUE). */
    val idempotencyKey: String,
    /** Silme yerine iptal. İptal edilen işlemler toplamlara katılmaz. */
    val isCancelled: Boolean = false,
    val cancelledAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long
)
