package com.kisisel.finans.ui.home

import com.kisisel.finans.domain.model.InvoiceOccurrence
import com.kisisel.finans.domain.model.OccurrenceStatus
import java.time.LocalDate

data class InvoiceSplit(
    val overdue: List<InvoiceOccurrence>,
    val upcoming: List<InvoiceOccurrence>
)

/**
 * Ödenmemiş dönemleri ayırır: geciken = vadesi bugünden önce; yaklaşan = bugün dahil `windowDays` gün içinde.
 * Ödenmiş ve iptal edilmiş dönemler dahil edilmez.
 */
fun splitInvoices(
    occurrences: List<InvoiceOccurrence>,
    today: LocalDate,
    windowDays: Long = 30
): InvoiceSplit {
    val unpaid = occurrences.filter { it.status == OccurrenceStatus.UNPAID }.sortedBy { it.dueDate }
    val limit = today.plusDays(windowDays)
    return InvoiceSplit(
        overdue = unpaid.filter { it.dueDate.isBefore(today) },
        upcoming = unpaid.filter { !it.dueDate.isBefore(today) && !it.dueDate.isAfter(limit) }
    )
}
