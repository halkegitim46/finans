package com.kisisel.finans.report

import com.kisisel.finans.domain.model.ClaimLine
import com.kisisel.finans.domain.model.InvoiceOccurrence
import com.kisisel.finans.domain.model.InvoiceType
import com.kisisel.finans.domain.model.LedgerEntry
import com.kisisel.finans.domain.model.LedgerLine
import com.kisisel.finans.domain.model.OccurrenceStatus
import com.kisisel.finans.domain.model.Person
import com.kisisel.finans.domain.model.TransactionType
import com.kisisel.finans.domain.model.TransactionType.BORROW
import com.kisisel.finans.domain.model.TransactionType.COLLECTION
import com.kisisel.finans.domain.model.TransactionType.DEBT_PAYMENT
import com.kisisel.finans.domain.model.TransactionType.LEND
import com.kisisel.finans.domain.model.TransactionType.PAID_ON_BEHALF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReportCalculatorTest {
    private fun d(s: String) = LocalDate.parse(s)
    private fun person(id: Long, name: String, opening: Long = 0, archived: Boolean = false) =
        Person(id, name, null, "", opening, archived, 0, 0)

    private fun line(
        id: Long, personId: Long, type: TransactionType, amount: Long, date: String,
        cancelled: Boolean = false, desc: String = ""
    ) = LedgerLine(
        LedgerEntry(id, personId, type, amount, d(date), null, desc, null, cancelled, 0),
        "Kişi$personId"
    )

    private val p1 = person(1, "Ayşe")
    private val p2 = person(2, "Bekir")
    private val lines = listOf(
        line(1, 1, LEND, 100_000, "2026-10-05"),
        line(2, 1, COLLECTION, 30_000, "2026-10-20"),
        line(3, 1, PAID_ON_BEHALF, 50_000, "2026-11-02"),
        line(4, 2, BORROW, 20_000, "2026-11-03"),
        line(5, 2, DEBT_PAYMENT, 5_000, "2026-11-04"),
        line(6, 1, LEND, 999, "2026-11-05", cancelled = true),
        line(7, 1, COLLECTION, 10_000, "2026-11-10")
    )
    private val nov = DateRange(d("2026-11-01"), d("2026-11-30"))
    private val oct = DateRange(d("2026-10-01"), d("2026-10-31"))

    // ---- DateRange ----
    @Test fun rangeContainsBoundaries() {
        assertTrue(d("2026-11-01") in nov)
        assertTrue(d("2026-11-30") in nov)
        assertTrue(d("2026-10-31") !in nov)
        assertEquals(30, nov.days)
    }

    @Test fun invalidRangeRejected() {
        assertThrows(IllegalArgumentException::class.java) { DateRange(d("2026-11-02"), d("2026-11-01")) }
    }

    @Test fun previousRanges() {
        assertEquals(oct, nov.previous())
        assertEquals(DateRange(d("2026-02-01"), d("2026-02-28")), DateRange(d("2026-03-01"), d("2026-03-31")).previous())
        assertEquals(
            DateRange(d("2025-01-01"), d("2025-12-31")),
            DateRange(d("2026-01-01"), d("2026-12-31")).previous()
        )
        // özel aralık: aynı uzunlukta hemen önceki
        assertEquals(
            DateRange(d("2026-11-01"), d("2026-11-10")),
            DateRange(d("2026-11-11"), d("2026-11-20")).previous()
        )
    }

    @Test fun presets() {
        val today = d("2026-11-15")
        assertEquals(nov, RangePreset.THIS_MONTH.range(today))
        assertEquals(oct, RangePreset.LAST_MONTH.range(today))
        assertEquals(DateRange(d("2026-09-01"), today), RangePreset.LAST_3_MONTHS.range(today))
        assertEquals(DateRange(d("2026-01-01"), d("2026-12-31")), RangePreset.THIS_YEAR.range(today))
        assertEquals(DateRange(d("2025-01-01"), d("2025-12-31")), RangePreset.LAST_YEAR.range(today))
        assertNull(RangePreset.CUSTOM.range(today))
    }

    // ---- özet ----
    @Test fun periodSummaryMapsTypesAndSkipsCancelled() {
        val s = ReportCalculator.periodSummary(lines, nov)
        assertEquals(0L, s.lend)
        assertEquals(50_000L, s.paidOnBehalf)
        assertEquals(20_000L, s.borrowed)
        assertEquals(10_000L, s.collected)
        assertEquals(5_000L, s.debtPaid)
        assertEquals(4, s.count)                     // iptal edilen sayılmaz
        assertEquals(50_000L, s.newReceivables)
        assertEquals(25_000L, s.netChange)           // (50-10) - (20-5)
    }

    @Test fun periodSummaryOctober() {
        val s = ReportCalculator.periodSummary(lines, oct)
        assertEquals(100_000L, s.lend)
        assertEquals(30_000L, s.collected)
        assertEquals(70_000L, s.netChange)
    }

    @Test fun emptyPeriodIsZero() {
        val s = ReportCalculator.periodSummary(lines, DateRange(d("2020-01-01"), d("2020-01-31")))
        assertEquals(0, s.count)
        assertEquals(0L, s.netChange)
    }

    @Test fun comparisonWithPreviousPeriod() {
        val rows = ReportCalculator.compare(
            ReportCalculator.periodSummary(lines, nov),
            ReportCalculator.periodSummary(lines, oct)
        )
        val receivables = rows.first { it.label == "Alacak oluşan" }
        assertEquals(50_000L, receivables.current)
        assertEquals(100_000L, receivables.previous)
        assertEquals(-50_000L, receivables.delta)
        assertEquals(-50.0, receivables.percent!!, 0.0001)

        val borrowed = rows.first { it.label == "Borç oluşan" }
        assertNull(borrowed.percent)                 // önceki 0 -> yüzde yok
        assertEquals(20_000L, borrowed.delta)
        assertEquals(5, rows.size)
    }

    // ---- seriler ----
    @Test fun monthlySeriesClipsToRange() {
        val series = ReportCalculator.monthlySeries(lines, DateRange(d("2026-10-15"), d("2026-11-20")))
        assertEquals(listOf("Eki 2026", "Kas 2026"), series.map { it.label })
        assertEquals(DateRange(d("2026-10-15"), d("2026-10-31")), series[0].range)
        assertEquals(30_000L, series[0].summary.collected)       // 10-05 borç verme aralık dışı
        assertEquals(0L, series[0].summary.lend)
        assertEquals(4, series[1].summary.count)
    }

    @Test fun monthlySeriesSingleMonthAndCap() {
        assertEquals(1, ReportCalculator.monthlySeries(lines, nov).size)
        val long = ReportCalculator.monthlySeries(lines, DateRange(d("2020-01-01"), d("2026-12-31")))
        assertEquals(36, long.size)
        assertEquals("Ara 2026", long.last().label)
    }

    @Test fun yearlySeries() {
        val series = ReportCalculator.yearlySeries(lines, DateRange(d("2025-12-01"), d("2026-02-28")))
        assertEquals(listOf("2025", "2026"), series.map { it.label })
        assertEquals(DateRange(d("2025-12-01"), d("2025-12-31")), series[0].range)
        assertEquals(DateRange(d("2026-01-01"), d("2026-02-28")), series[1].range)
        val full = ReportCalculator.yearlySeries(lines, DateRange(d("2026-01-01"), d("2026-12-31")))
        assertEquals(100_000L, full.single().summary.lend)
    }

    // ---- kişi dökümü ----
    @Test fun statementOpeningRunningAndClosing() {
        val st = ReportCalculator.personStatements(listOf(p1, p2), lines, nov)
        val a = st.first { it.person.id == 1L }
        assertEquals(70_000L, a.openingAtStart)               // +100.000 -30.000
        assertEquals(listOf(120_000L, 110_000L), a.lines.map { it.balanceAfter })
        assertEquals(listOf(50_000L, -10_000L), a.lines.map { it.delta })
        assertEquals(110_000L, a.closing)
        assertEquals(50_000L, a.periodReceivables)
        assertEquals(10_000L, a.periodCollected)

        val b = st.first { it.person.id == 2L }
        assertEquals(0L, b.openingAtStart)
        assertEquals(listOf(-20_000L, -15_000L), b.lines.map { it.balanceAfter })
        assertEquals(-15_000L, b.closing)
    }

    @Test fun statementUsesPersonOpeningBalanceAndSkipsInactiveZeroPersons() {
        val withOpening = person(3, "Cem", opening = 25_000)
        val idle = person(4, "Deniz")
        val st = ReportCalculator.personStatements(listOf(withOpening, idle), lines, nov)
        assertEquals(listOf("Cem"), st.map { it.person.fullName })
        assertEquals(25_000L, st.single().openingAtStart)
        assertEquals(25_000L, st.single().closing)
    }

    @Test fun statementIncludesArchivedPersonsWithHistory() {
        val archived = person(1, "Ayşe", archived = true)
        assertEquals(1, ReportCalculator.personStatements(listOf(archived), lines, nov).size)
    }

    @Test fun statementOrderedByNameTurkish() {
        val st = ReportCalculator.personStatements(
            listOf(person(2, "Zeynep", opening = 1), person(1, "Ahmet", opening = 1), person(3, "Çağla", opening = 1)),
            emptyList(), nov
        )
        assertEquals(listOf("Ahmet", "Çağla", "Zeynep"), st.map { it.person.fullName })
    }

    // ---- tahsilat ----
    @Test fun collectionsAreFilteredAndSortedNewestFirst() {
        val c = ReportCalculator.collections(lines, DateRange(d("2026-10-01"), d("2026-11-30")))
        assertEquals(listOf(7L, 2L), c.map { it.entry.id })
        assertEquals(40_000L, ReportCalculator.collectionsTotal(c))
    }

    // ---- fatura geçmişi ----
    private fun occ(
        id: Long, tpl: Long, name: String, status: OccurrenceStatus, paid: String?, amount: Long? = 100_000,
        due: String = "2026-11-05", person: String? = "Ayşe"
    ) = InvoiceOccurrence(
        id, tpl, name, InvoiceType.ELECTRICITY, null, person, "k$id", d(due), amount, status, paid?.let { d(it) }
    )

    @Test fun invoiceHistoryGroupsPaidInRange() {
        val occs = listOf(
            occ(1, 10, "Elektrik", OccurrenceStatus.PAID, "2026-11-03", 150_000),
            occ(2, 10, "Elektrik", OccurrenceStatus.PAID, "2026-10-03", 140_000),      // aralık dışı
            occ(3, 10, "Elektrik", OccurrenceStatus.UNPAID, null),
            occ(4, 11, "Su", OccurrenceStatus.PAID, "2026-11-20", 40_000),
            occ(5, 11, "Su", OccurrenceStatus.PAID, "2026-11-25", 45_000),
            occ(6, 12, "İptal", OccurrenceStatus.CANCELLED, null)
        )
        val h = ReportCalculator.invoiceHistory(occs, nov)
        assertEquals(listOf("Elektrik", "Su"), h.map { it.name })
        assertEquals(150_000L, h[0].totalKurus)
        assertEquals(85_000L, h[1].totalKurus)
        assertEquals(2, h[1].count)
        assertEquals(listOf(5L, 4L), h[1].items.map { it.id })                           // yeniden eskiye
    }

    // ---- bekleyen ----
    private fun claim(
        id: Long, type: TransactionType, amount: Long, allocated: Long, due: String?, occurrenceId: Long? = null,
        desc: String = ""
    ) = ClaimLine(id, 1, "Ayşe", type, amount, allocated, d("2026-10-01"), due?.let { d(it) }, occurrenceId, desc)

    @Test fun pendingSplitsOverdueAndUpcoming() {
        val today = d("2026-11-10")
        val occs = listOf(
            occ(1, 10, "Elektrik", OccurrenceStatus.UNPAID, null, 150_000, due = "2026-11-05"),     // 5 gün gecikti
            occ(2, 11, "Su", OccurrenceStatus.UNPAID, null, null, due = "2026-11-20"),               // tutar yok
            occ(3, 10, "Eski", OccurrenceStatus.PAID, "2026-10-01")
        )
        val claims = listOf(
            claim(1, LEND, 100_000, 30_000, "2026-11-01", desc = "Harçlık"),                         // açık 70.000, gecikmiş
            claim(2, PAID_ON_BEHALF, 150_000, 0, "2026-11-01", occurrenceId = 5),                    // faturadan doğan: hariç
            claim(3, LEND, 10_000, 0, null),                                                         // vadesiz: hariç
            claim(4, BORROW, 20_000, 0, "2026-11-30"),                                               // bekleyen borç
            claim(5, LEND, 8_000, 8_000, "2026-11-02")                                               // tamamen kapandı: hariç
        )
        val p = ReportCalculator.pending(occs, claims, today)

        assertEquals(listOf("Harçlık", "Elektrik"), p.overdue.map { it.title })   // vadeye göre sıralı
        assertEquals(listOf(9L, 5L), p.overdue.map { it.daysOverdue })
        assertEquals(70_000L, p.overdue[0].amountKurus)
        assertEquals(PendingKind.RECEIVABLE, p.overdue[0].kind)
        assertEquals(listOf("Su", "Borç"), p.upcoming.map { it.title })

        assertEquals(150_000L, p.overdueToPay)
        assertEquals(70_000L, p.overdueToCollect)
        assertEquals(20_000L, p.upcomingToPay)
        assertEquals(0L, p.upcomingToCollect)
        assertEquals(1, p.unknownAmountCount)
    }

    @Test fun dueTodayIsNotOverdue() {
        val today = d("2026-11-10")
        val p = ReportCalculator.pending(listOf(occ(1, 10, "Elektrik", OccurrenceStatus.UNPAID, null, due = "2026-11-10")), emptyList(), today)
        assertEquals(0, p.overdue.size)
        assertEquals(1, p.upcoming.size)
        assertEquals(0L, p.upcoming[0].daysOverdue)
    }

    @Test fun balanceDeltaSigns() {
        assertEquals(5L, ReportCalculator.balanceDelta(LEND, 5))
        assertEquals(5L, ReportCalculator.balanceDelta(PAID_ON_BEHALF, 5))
        assertEquals(5L, ReportCalculator.balanceDelta(DEBT_PAYMENT, 5))
        assertEquals(-5L, ReportCalculator.balanceDelta(BORROW, 5))
        assertEquals(-5L, ReportCalculator.balanceDelta(COLLECTION, 5))
    }
}
