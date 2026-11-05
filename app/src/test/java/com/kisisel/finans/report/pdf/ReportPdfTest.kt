package com.kisisel.finans.report.pdf

import com.kisisel.finans.domain.model.InvoiceOccurrence
import com.kisisel.finans.domain.model.InvoiceType
import com.kisisel.finans.domain.model.LedgerEntry
import com.kisisel.finans.domain.model.LedgerLine
import com.kisisel.finans.domain.model.OccurrenceStatus
import com.kisisel.finans.domain.model.Person
import com.kisisel.finans.domain.model.TransactionType
import com.kisisel.finans.report.ComparisonRow
import com.kisisel.finans.report.DateRange
import com.kisisel.finans.report.InvoiceHistory
import com.kisisel.finans.report.PendingItem
import com.kisisel.finans.report.PendingKind
import com.kisisel.finans.report.PendingReport
import com.kisisel.finans.report.PeriodSummary
import com.kisisel.finans.report.PersonStatement
import com.kisisel.finans.report.ReportCalculator
import com.kisisel.finans.report.SeriesPoint
import com.kisisel.finans.report.StatementLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReportPdfTest {
    private fun d(s: String) = LocalDate.parse(s)
    private val range = DateRange(d("2026-11-01"), d("2026-11-30"))

    private inline fun <reified T : PdfBlock> List<PdfBlock>.ofType() = filterIsInstance<T>()

    private fun statement(name: String, lines: Int): PersonStatement {
        val p = Person(1, name, null, "", 0, false, 0, 0)
        var running = 0L
        val ls = (1..lines).map { i ->
            val e = LedgerEntry(i.toLong(), 1, TransactionType.LEND, 1_000L * i, d("2026-11-0$i"), null, "açıklama $i", null, false, 0)
            running += e.amountKurus
            StatementLine(e, e.amountKurus, running)
        }
        return PersonStatement(p, 0, ls, running, running, 0, 0, 0)
    }

    @Test fun summaryHasTitleKeyValuesComparisonAndTwoCharts() {
        val s = PeriodSummary(0, 50_000, 20_000, 10_000, 5_000, 4)
        val prev = PeriodSummary(100_000, 0, 0, 30_000, 0, 2)
        val point = SeriesPoint("Kas 2026", range, s)
        val r = ReportPdf.summary(range, range.previous(), 4, ReportCalculator.compare(s, prev), listOf(point), listOf(SeriesPoint("2026", range, s)))

        assertEquals("Özet Raporu", r.title)
        assertEquals(PdfBlock.Title("Özet Raporu"), r.blocks.first())
        assertEquals(5, r.blocks\.ofType<PdfBlock.KeyValue>().size)
        assertEquals(2, r.blocks\.ofType<PdfBlock.BarChart>().size)
        val cmp = r.blocks\.ofType<PdfBlock.Table>().first()
        assertEquals(5, cmp.rows.size)
        assertEquals(listOf("Alacak oluşan", "₺500,00", "₺1.000,00", "-₺500,00", "-50%"), cmp.rows[0].cells)
        assertTrue(r.blocks\.ofType<PdfBlock.Heading>().any { it.text.contains("4 işlem") })
        assertEquals(3, r.blocks\.ofType<PdfBlock.Table>().size)   // karşılaştırma + aylık + yıllık
    }

    @Test fun comparisonShowsDashWhenPreviousIsZero() {
        val rows = listOf(ComparisonRow("Borç oluşan", 20_000, 0))
        val r = ReportPdf.summary(range, range.previous(), 1, rows, emptyList(), emptyList())
        assertEquals("-", r.blocks\.ofType<PdfBlock.Table>().first().rows[0].cells[4])
    }

    @Test fun singlePersonStatementUsesPersonNameInTitle() {
        val r = ReportPdf.persons(range, listOf(statement("Çağla Öztürk", 3)))
        assertEquals("Hesap Dökümü: Çağla Öztürk", r.title)
        val table = r.blocks\.ofType<PdfBlock.Table>().single()
        assertEquals(3, table.rows.size)
        assertEquals("01.11.2026", table.rows[0].cells[0])
        assertEquals("Borç verildi", table.rows[0].cells[1])
        assertEquals("+₺10,00", table.rows[0].cells[3])
        assertEquals("₺10,00", table.rows[0].cells[4])
        assertEquals("₺60,00", table.rows[2].cells[4])         // yürüyen bakiye
        val kv = r.blocks\.ofType<PdfBlock.KeyValue>()
        assertEquals("₺0,00", kv.first { it.label == "Dönem başı bakiye" }.value)
        val closing = kv.first { it.label == "Dönem sonu bakiye" }
        assertEquals("₺60,00", closing.value)
        assertTrue(closing.bold)
    }

    @Test fun multiplePersonsGetOwnSections() {
        val r = ReportPdf.persons(range, listOf(statement("Ayşe", 1), statement("Bekir", 2)))
        assertEquals("Kişi Hesap Dökümü", r.title)
        assertEquals(listOf("Ayşe", "Bekir"), r.blocks\.ofType<PdfBlock.Heading>().map { it.text })
        assertEquals(2, r.blocks\.ofType<PdfBlock.Table>().size)
    }

    @Test fun emptyStatementsAndPersonWithoutLinesShowNotes() {
        val none = ReportPdf.persons(range, emptyList())
        assertTrue(none.blocks\.ofType<PdfBlock.Paragraph>().any { it.text.contains("yok") })
        assertEquals(0, none.blocks\.ofType<PdfBlock.Table>().size)

        val noLines = ReportPdf.persons(range, listOf(statement("Ayşe", 0)))
        assertEquals(0, noLines.blocks\.ofType<PdfBlock.Table>().size)
        assertTrue(noLines.blocks\.ofType<PdfBlock.Paragraph>().any { it.text.contains("hareket yok") })
    }

    @Test fun invoicesGroupedWithTotals() {
        val o = InvoiceOccurrence(1, 10, "Elektrik", InvoiceType.ELECTRICITY, null, null, "2026-11", d("2026-11-05"), 150_000, OccurrenceStatus.PAID, d("2026-11-03"))
        val r = ReportPdf.invoices(range, listOf(InvoiceHistory(10, "Elektrik", "Dayım", listOf(o), 150_000)))
        assertTrue(r.blocks\.ofType<PdfBlock.Heading>().single().text.contains("Dayım"))
        assertTrue(r.blocks\.ofType<PdfBlock.Heading>().single().text.contains("₺1.500,00"))
        assertEquals(listOf("2026-11", "05.11.2026", "03.11.2026", "₺1.500,00"), r.blocks\.ofType<PdfBlock.Table>().single().rows[0].cells)
        assertTrue(ReportPdf.invoices(range, emptyList()).blocks\.ofType<PdfBlock.Paragraph>().isNotEmpty())
    }

    @Test fun collectionsShowTotalAndRows() {
        val l = LedgerLine(LedgerEntry(1, 1, TransactionType.COLLECTION, 50_000, d("2026-11-10"), null, "kısmi", null, false, 0), "Ayşe")
        val r = ReportPdf.collections(range, listOf(l), 50_000)
        assertEquals("₺500,00", r.blocks\.ofType<PdfBlock.KeyValue>().first { it.label == "Toplam tahsilat" }.value)
        assertEquals(listOf("10.11.2026", "Ayşe", "kısmi", "₺500,00"), r.blocks\.ofType<PdfBlock.Table>().single().rows[0].cells)
        assertEquals(0, ReportPdf.collections(range, emptyList(), 0).blocks\.ofType<PdfBlock.Table>().size)
    }

    @Test fun pendingMarksOverdueRowsAsAlert() {
        val overdue = PendingItem(PendingKind.INVOICE, "Su", "Ayşe", d("2026-11-05"), 40_000, 5, 1)
        val upcoming = PendingItem(PendingKind.PAYABLE, "Borç", null, d("2026-11-30"), null, 0, 2)
        val r = ReportPdf.pending(d("2026-11-10"), PendingReport(listOf(overdue), listOf(upcoming), 40_000, 0, 0, 0, 1))
        val tables = r.blocks\.ofType<PdfBlock.Table>()
        assertEquals(2, tables.size)
        assertEquals(Emphasis.ALERT, tables[0].rows[0].emphasis)
        assertEquals("5 gün", tables[0].rows[0].cells[1])
        assertEquals(Emphasis.NORMAL, tables[1].rows[0].emphasis)
        assertEquals("Tutar yok", tables[1].rows[0].cells[5])
        assertTrue(r.blocks\.ofType<PdfBlock.Paragraph>().any { it.text.contains("1 kalemin tutarı") })
    }

    @Test fun pendingEmptySectionsShowNotes() {
        val r = ReportPdf.pending(d("2026-11-10"), PendingReport(emptyList(), emptyList(), 0, 0, 0, 0, 0))
        assertEquals(0, r.blocks\.ofType<PdfBlock.Table>().size)
        assertEquals(2, r.blocks\.ofType<PdfBlock.Paragraph>().size)
    }

    @Test fun fileSafeTransliteratesTurkishAndStripsSymbols() {
        assertEquals("Cagla_Ozturk", ReportPdf.fileSafe("Çağla Öztürk"))
        assertEquals("Ayse_Fatma", ReportPdf.fileSafe("Ayşe/Fatma"))
        assertEquals("Ismail_Is", ReportPdf.fileSafe("İsmail  Iş"))
        assertEquals("kisi", ReportPdf.fileSafe("  ***  "))
        assertEquals(40, ReportPdf.fileSafe("a".repeat(80)).length)
    }

    @Test fun generatedBlocksPaginateWithoutErrors() {
        // Uçtan uca: gerçek bloklar sahte ölçerle sayfalanır (çizim hariç).
        val big = ReportPdf.persons(range, (1..30).map { statement("Kişi $it", 6) })
        val pages = PdfPaginator(PageSpec(), object : TextMeasurer {
            override fun width(text: String, size: Float, bold: Boolean) = text.length * size * 0.5f
        }).layout(big.blocks)
        assertTrue(pages.size > 3)
    }
}
