package com.kisisel.finans.report

import com.kisisel.finans.domain.model.InvoiceOccurrence
import com.kisisel.finans.domain.model.LedgerEntry
import com.kisisel.finans.domain.model.Person
import java.time.LocalDate
import kotlin.math.abs

/**
 * Bir dönemin özeti (kuruş). Alacak oluşan = borç verme + kişi adına ödeme; borç oluşan = borç alma.
 * Net değişim: kişilerle net bakiyenin (alacak − borç) dönem içindeki değişimi.
 */
data class PeriodSummary(
    val lend: Long,
    val paidOnBehalf: Long,
    val borrowed: Long,
    val collected: Long,
    val debtPaid: Long,
    val count: Int
) {
    val newReceivables: Long get() = lend + paidOnBehalf
    val netChange: Long get() = (newReceivables - collected) - (borrowed - debtPaid)
}

data class SeriesPoint(val label: String, val range: DateRange, val summary: PeriodSummary)

data class ComparisonRow(val label: String, val current: Long, val previous: Long) {
    val delta: Long get() = current - previous

    /** Yalnızca gösterim için; önceki 0 ise null. */
    val percent: Double? get() = if (previous == 0L) null else delta.toDouble() / abs(previous.toDouble()) * 100.0
}

/** delta: kişinin net bakiyesine etkisi (+ = kişi bana daha borçlu). */
data class StatementLine(val entry: LedgerEntry, val delta: Long, val balanceAfter: Long)

data class PersonStatement(
    val person: Person,
    val openingAtStart: Long,
    val lines: List<StatementLine>,
    val closing: Long,
    val periodReceivables: Long,
    val periodCollected: Long,
    val periodPayables: Long,
    val periodDebtPaid: Long
)

data class InvoiceHistory(
    val templateId: Long,
    val name: String,
    val personName: String?,
    val items: List<InvoiceOccurrence>,
    val totalKurus: Long
) {
    val count: Int get() = items.size
}

enum class PendingKind { INVOICE, RECEIVABLE, PAYABLE }

data class PendingItem(
    val kind: PendingKind,
    val title: String,
    val personName: String?,
    val dueDate: LocalDate,
    /** Fatura tutarı henüz girilmediyse null. */
    val amountKurus: Long?,
    val daysOverdue: Long,
    val refId: Long
) {
    /** Ödeme yapılacak mı (fatura, borç) yoksa tahsil edilecek mi (alacak)? */
    val isOutgoing: Boolean get() = kind != PendingKind.RECEIVABLE
}

data class PendingReport(
    val overdue: List<PendingItem>,
    val upcoming: List<PendingItem>,
    val overdueToPay: Long,
    val overdueToCollect: Long,
    val upcomingToPay: Long,
    val upcomingToCollect: Long,
    val unknownAmountCount: Int
)
