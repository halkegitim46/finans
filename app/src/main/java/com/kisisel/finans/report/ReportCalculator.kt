package com.kisisel.finans.report

import com.kisisel.finans.core.Money
import com.kisisel.finans.domain.model.ClaimLine
import com.kisisel.finans.domain.model.InvoiceOccurrence
import com.kisisel.finans.domain.model.LedgerEntry
import com.kisisel.finans.domain.model.LedgerLine
import com.kisisel.finans.domain.model.OccurrenceStatus
import com.kisisel.finans.domain.model.Person
import com.kisisel.finans.domain.model.TransactionType
import java.text.Collator
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Rapor hesapları (saf fonksiyonlar). Tüm tutarlar kuruş (Long); iptal edilmiş işlemler hiçbir rapora girmez.
 */
object ReportCalculator {
    private val MONTHS = listOf("Oca", "Şub", "Mar", "Nis", "May", "Haz", "Tem", "Ağu", "Eyl", "Eki", "Kas", "Ara")
    private const val MAX_MONTHS = 36
    private val TR = Locale("tr", "TR")

    /** Türkçe sıralama (Ç, Ö, Ş, Ü, Ğ, İ doğru yerde). Collator iş parçacığı güvenli olmadığından her seferinde üretilir. */
    private fun collator(): Collator = Collator.getInstance(TR)

    /** Kişinin net bakiyesine (alacak − borç) etkisi. */
    fun balanceDelta(type: TransactionType, amountKurus: Long): Long = when (type) {
        TransactionType.LEND, TransactionType.PAID_ON_BEHALF, TransactionType.DEBT_PAYMENT -> amountKurus
        TransactionType.BORROW, TransactionType.COLLECTION -> -amountKurus
    }

    private fun sum(entries: List<LedgerEntry>, vararg types: TransactionType): Long =
        entries.filter { it.type in types }.fold(0L) { acc, e -> Money.add(acc, e.amountKurus) }

    fun periodSummary(lines: List<LedgerLine>, range: DateRange): PeriodSummary {
        val e = lines.map { it.entry }.filter { !it.isCancelled && it.date in range }
        return PeriodSummary(
            lend = sum(e, TransactionType.LEND),
            paidOnBehalf = sum(e, TransactionType.PAID_ON_BEHALF),
            borrowed = sum(e, TransactionType.BORROW),
            collected = sum(e, TransactionType.COLLECTION),
            debtPaid = sum(e, TransactionType.DEBT_PAYMENT),
            count = e.size
        )
    }

    fun compare(current: PeriodSummary, previous: PeriodSummary): List<ComparisonRow> = listOf(
        ComparisonRow("Alacak oluşan", current.newReceivables, previous.newReceivables),
        ComparisonRow("Tahsilat", current.collected, previous.collected),
        ComparisonRow("Borç oluşan", current.borrowed, previous.borrowed),
        ComparisonRow("Borç ödemesi", current.debtPaid, previous.debtPaid),
        ComparisonRow("Net değişim", current.netChange, previous.netChange)
    )

    /** Aralıkla kesişen her ay için özet (en çok son 36 ay). */
    fun monthlySeries(lines: List<LedgerLine>, range: DateRange): List<SeriesPoint> {
        val first = YearMonth.from(range.start)
        val last = YearMonth.from(range.end)
        val months = generateSequence(first) { if (it < last) it.plusMonths(1) else null }.toList().takeLast(MAX_MONTHS)
        return months.map { ym ->
            val clipped = DateRange(
                maxOf(ym.atDay(1), range.start),
                minOf(ym.atEndOfMonth(), range.end)
            )
            SeriesPoint("${MONTHS[ym.monthValue - 1]} ${ym.year}", clipped, periodSummary(lines, clipped))
        }
    }

    /** Aralıkla kesişen her takvim yılı için özet. */
    fun yearlySeries(lines: List<LedgerLine>, range: DateRange): List<SeriesPoint> =
        (range.start.year..range.end.year).map { year ->
            val clipped = DateRange(
                maxOf(LocalDate.of(year, 1, 1), range.start),
                minOf(LocalDate.of(year, 12, 31), range.end)
            )
            SeriesPoint(year.toString(), clipped, periodSummary(lines, clipped))
        }

    /**
     * Kişi bazlı hesap dökümü: dönem başı bakiye (başlangıç bakiyesi + önceki işlemler), dönem içi hareketler
     * (yürüyen bakiyeyle) ve dönem sonu bakiye. Hareketi ve bakiyesi olmayan kişiler listelenmez.
     */
    fun personStatements(persons: List<Person>, lines: List<LedgerLine>, range: DateRange): List<PersonStatement> {
        val byPerson = lines.filter { !it.entry.isCancelled }.groupBy { it.entry.personId }
        return persons.mapNotNull { p ->
            val all = byPerson[p.id].orEmpty().map { it.entry }
            val opening = all.filter { it.date.isBefore(range.start) }
                .fold(p.openingBalanceKurus) { acc, e -> Money.add(acc, balanceDelta(e.type, e.amountKurus)) }
            val inRange = all.filter { it.date in range }.sortedWith(compareBy({ it.date }, { it.id }))

            var running = opening
            val stmt = inRange.map { e ->
                val d = balanceDelta(e.type, e.amountKurus)
                running = Money.add(running, d)
                StatementLine(e, d, running)
            }
            if (inRange.isEmpty() && opening == 0L && running == 0L) {
                null
            } else {
                PersonStatement(
                    person = p,
                    openingAtStart = opening,
                    lines = stmt,
                    closing = running,
                    periodReceivables = sum(inRange, TransactionType.LEND, TransactionType.PAID_ON_BEHALF),
                    periodCollected = sum(inRange, TransactionType.COLLECTION),
                    periodPayables = sum(inRange, TransactionType.BORROW),
                    periodDebtPaid = sum(inRange, TransactionType.DEBT_PAYMENT)
                )
            }
        }.sortedWith(compareBy(collator()) { it.person.fullName })
    }

    fun collections(lines: List<LedgerLine>, range: DateRange): List<LedgerLine> =
        lines.filter { !it.entry.isCancelled && it.entry.type == TransactionType.COLLECTION && it.entry.date in range }
            .sortedWith(compareByDescending<LedgerLine> { it.entry.date }.thenByDescending { it.entry.id })

    fun collectionsTotal(collections: List<LedgerLine>): Long =
        collections.fold(0L) { acc, l -> Money.add(acc, l.entry.amountKurus) }

    /** Fatura bazlı ödeme geçmişi: ödeme tarihi aralıkta olan dönemler, fatura (şablon) başına gruplanır. */
    fun invoiceHistory(occurrences: List<InvoiceOccurrence>, range: DateRange): List<InvoiceHistory> =
        occurrences
            .filter { it.status == OccurrenceStatus.PAID && it.paidDate != null && it.paidDate in range }
            .groupBy { it.templateId }
            .map { (templateId, items) ->
                val sorted = items.sortedByDescending { it.paidDate }
                InvoiceHistory(
                    templateId = templateId,
                    name = sorted.first().name,
                    personName = sorted.first().personName,
                    items = sorted,
                    totalKurus = sorted.fold(0L) { acc, o -> Money.add(acc, o.amountKurus ?: 0L) }
                )
            }
            .sortedWith(compareBy(collator()) { it.name })

    /**
     * Ödenmemiş faturalar ve vadesi olan açık alacak/borçlar. Vadesi bugünden önce olanlar gecikmiştir.
     * Bir fatura ödemesinden doğan alacaklar (occurrenceId dolu) dahil edilmez: onların vadesi fatura vadesidir.
     */
    fun pending(occurrences: List<InvoiceOccurrence>, claims: List<ClaimLine>, today: LocalDate): PendingReport {
        val invoiceItems = occurrences.filter { it.status == OccurrenceStatus.UNPAID }.map {
            PendingItem(PendingKind.INVOICE, it.name, it.personName, it.dueDate, it.amountKurus, overdueDays(it.dueDate, today), it.id)
        }
        val claimItems = claims.filter { it.occurrenceId == null && it.dueDate != null && it.openKurus > 0 }.map {
            val kind = if (it.type == TransactionType.BORROW) PendingKind.PAYABLE else PendingKind.RECEIVABLE
            PendingItem(
                kind = kind,
                title = it.description.ifBlank { if (kind == PendingKind.PAYABLE) "Borç" else "Alacak" },
                personName = it.personName,
                dueDate = it.dueDate!!,
                amountKurus = it.openKurus,
                daysOverdue = overdueDays(it.dueDate, today),
                refId = it.entryId
            )
        }
        val all = (invoiceItems + claimItems).sortedWith(compareBy({ it.dueDate }, { it.refId }))
        val overdue = all.filter { it.dueDate.isBefore(today) }
        val upcoming = all.filter { !it.dueDate.isBefore(today) }

        fun total(items: List<PendingItem>, outgoing: Boolean) =
            items.filter { it.isOutgoing == outgoing }.fold(0L) { acc, i -> Money.add(acc, i.amountKurus ?: 0L) }

        return PendingReport(
            overdue = overdue,
            upcoming = upcoming,
            overdueToPay = total(overdue, true),
            overdueToCollect = total(overdue, false),
            upcomingToPay = total(upcoming, true),
            upcomingToCollect = total(upcoming, false),
            unknownAmountCount = all.count { it.amountKurus == null }
        )
    }

    private fun overdueDays(due: LocalDate, today: LocalDate): Long =
        if (due.isBefore(today)) ChronoUnit.DAYS.between(due, today) else 0L
}
