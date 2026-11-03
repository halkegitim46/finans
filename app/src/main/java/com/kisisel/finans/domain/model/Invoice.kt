package com.kisisel.finans.domain.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class InvoiceTemplate(
    val id: Long,
    val personId: Long?,
    val personName: String?,
    val name: String,
    val type: InvoiceType,
    val amountKurus: Long?,
    val isAmountFixed: Boolean,
    val recurrence: Recurrence,
    val customIntervalDays: Int?,
    val firstDueDate: LocalDate,
    val note: String,
    val remindersEnabled: Boolean,
    val reminderDays: Set<Int>,
    val overdueReminderEveryDays: Int,
    val isActive: Boolean,
    val isArchived: Boolean
)

/** Faturanın tek bir dönemi. Ödeme yalnızca bu dönemi kapatır. */
data class InvoiceOccurrence(
    val id: Long,
    val templateId: Long,
    val name: String,
    val type: InvoiceType,
    val personId: Long?,
    val personName: String?,
    val periodKey: String,
    val dueDate: LocalDate,
    val amountKurus: Long?,
    val status: OccurrenceStatus,
    val paidDate: LocalDate?
) {
    fun daysUntilDue(today: LocalDate): Long = ChronoUnit.DAYS.between(today, dueDate)
    fun isOverdue(today: LocalDate): Boolean = status == OccurrenceStatus.UNPAID && dueDate.isBefore(today)
}

data class NewInvoice(
    /** null = kendi faturam (kişi alacağı oluşmaz). */
    val personId: Long?,
    val name: String,
    val type: InvoiceType,
    val amountKurus: Long?,
    val isAmountFixed: Boolean,
    val recurrence: Recurrence,
    val customIntervalDays: Int?,
    val firstDueDate: LocalDate,
    val note: String,
    val remindersEnabled: Boolean,
    val reminderDays: Set<Int>,
    val overdueReminderEveryDays: Int
)

/** Tekrar sıklığı ve ilk vade değiştirilemez (dönem anahtarları bozulmasın). Yalnızca sonraki dönemleri etkiler. */
data class InvoiceUpdate(
    val id: Long,
    val personId: Long?,
    val name: String,
    val type: InvoiceType,
    val amountKurus: Long?,
    val isAmountFixed: Boolean,
    val note: String,
    val remindersEnabled: Boolean,
    val reminderDays: Set<Int>,
    val overdueReminderEveryDays: Int
)

/** Hatırlatma planlaması için: ödenmemiş dönem + şablonun hatırlatma ayarları. */
data class ReminderCandidate(
    val occurrence: InvoiceOccurrence,
    val reminderDays: Set<Int>,
    val overdueEveryDays: Int
)
