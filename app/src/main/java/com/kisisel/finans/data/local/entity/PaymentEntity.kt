package com.kisisel.finans.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Kısmi ödeme/tahsilat dağıtımı: bir tahsilat/ödeme işlemi (settlement), bir alacak/borç
 * işlemini (claim) kaç kuruş kapattı. Bir claim'in kalanı = tutarı - Σ(dağıtımlar).
 */
@Entity(
    tableName = "payments",
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["settlementTxId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["claimTxId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["settlementTxId", "claimTxId"], unique = true),
        Index(value = ["claimTxId"])
    ]
)
data class PaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val settlementTxId: Long,
    val claimTxId: Long,
    val amountKurus: Long,
    val createdAt: Long
)
