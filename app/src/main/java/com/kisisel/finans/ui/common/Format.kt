package com.kisisel.finans.ui.common

import com.kisisel.finans.domain.model.InvoiceType
import com.kisisel.finans.domain.model.OccurrenceStatus
import com.kisisel.finans.domain.model.Recurrence
import com.kisisel.finans.domain.model.TransactionType
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

fun LocalDate.tr(): String = format(DATE_FMT)

fun TransactionType.label(): String = when (this) {
    TransactionType.LEND -> "Borç verildi"
    TransactionType.BORROW -> "Borç alındı"
    TransactionType.PAID_ON_BEHALF -> "Adına ödeme"
    TransactionType.COLLECTION -> "Tahsilat"
    TransactionType.DEBT_PAYMENT -> "Borç ödemesi"
}

fun TransactionType.dialogTitle(): String = when (this) {
    TransactionType.LEND -> "Borç ver"
    TransactionType.BORROW -> "Borç al"
    TransactionType.PAID_ON_BEHALF -> "Kişi adına ödeme"
    TransactionType.COLLECTION -> "Tahsilat al"
    TransactionType.DEBT_PAYMENT -> "Borç öde"
}

fun InvoiceType.label(): String = when (this) {
    InvoiceType.ELECTRICITY -> "Elektrik"
    InvoiceType.WATER -> "Su"
    InvoiceType.GAS -> "Doğalgaz"
    InvoiceType.INTERNET -> "İnternet"
    InvoiceType.PHONE -> "Telefon"
    InvoiceType.RENT -> "Kira"
    InvoiceType.DUES -> "Aidat"
    InvoiceType.LOAN -> "Kredi / taksit"
    InvoiceType.OTHER -> "Diğer"
}

fun Recurrence.label(): String = when (this) {
    Recurrence.ONCE -> "Tek seferlik"
    Recurrence.MONTHLY -> "Aylık"
    Recurrence.YEARLY -> "Yıllık"
    Recurrence.CUSTOM_DAYS -> "Özel (gün aralığı)"
}

/** Vade durumu metni. daysUntilDue negatifse gecikmiştir. */
fun dueStatusText(status: OccurrenceStatus, daysUntilDue: Long): String = when {
    status == OccurrenceStatus.PAID -> "Ödendi"
    status == OccurrenceStatus.CANCELLED -> "İptal edildi"
    daysUntilDue < 0 -> "${-daysUntilDue} gün gecikti"
    daysUntilDue == 0L -> "Bugün son gün"
    else -> "$daysUntilDue gün kaldı"
}
