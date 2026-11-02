package com.kisisel.finans.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.kisisel.finans.domain.model.InvoiceType
import com.kisisel.finans.domain.model.Recurrence

@Entity(
    tableName = "invoice_templates",
    foreignKeys = [
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["personId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index(value = ["personId"]), Index(value = ["isArchived"])]
)
data class InvoiceTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Boş ise fatura benim kendi faturamdır (alacak kaydı oluşmaz). */
    val personId: Long? = null,
    val name: String,
    val type: InvoiceType,
    /** Sabit tutarlı faturada tutar; değişkende son bilinen/tahmini tutar (boş olabilir). */
    val amountKurus: Long? = null,
    val isAmountFixed: Boolean = true,
    val recurrence: Recurrence = Recurrence.MONTHLY,
    val customIntervalDays: Int? = null,
    val firstDueDate: Long,
    val note: String = "",
    val remindersEnabled: Boolean = true,
    /** Virgülle ayrılmış gün sayıları: "7,3,1,0" (0 = vade günü). */
    val reminderDaysBefore: String = "7,3,1,0",
    /** Gecikmiş fatura için hatırlatma aralığı (gün); 0 = kapalı. */
    val overdueReminderEveryDays: Int = 1,
    val isActive: Boolean = true,
    val isArchived: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long
)
