package com.kisisel.finans.report

import com.kisisel.finans.domain.model.InvoiceOccurrence
import com.kisisel.finans.domain.model.InvoiceType
import com.kisisel.finans.domain.model.LedgerEntry
import com.kisisel.finans.domain.model.LedgerLine
import com.kisisel.finans.domain.model.OccurrenceStatus
import com.kisisel.finans.domain.model.Person
import com.kisisel.finans.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReportCsvTest {
    private fun d(s: String) = LocalDate.parse(s)
    private fun csvLines(csv: String) = csv.split("\r\n").dropLast(1)

    // ---- CsvWriter ----
    @Test fun amountFormatting() {
        assertEquals("1500,50", CsvWriter.amount(150_050))
        assertEquals("0,05", CsvWriter.amount(5))
        assertEquals("0,00", CsvWriter.amount(0))
        assertEquals("-1234,56", CsvWriter.amount(-123_456))
        assertEquals("1000000,00", CsvWriter.amount(100_000_000))
    }

    @Test fun textQuotingAndEscaping() {
        assertEquals("düz", CsvWriter.text("düz"))
        assertEquals("\"a;b\"", CsvWriter.text("a;b"))
        assertEquals("\"o \"\"tırnaklı\"\" metin\"", CsvWriter.text("o \"tırnaklı\" metin"))
        assertEquals("\"satır\nsonu\"", CsvWriter.text("satır\nsonu"))
        assertEquals("", CsvWriter.text(""))
    }

    @Test fun formulaInjectionIsNeutralized() {
        assertEquals("'=1+1", CsvWriter.text("=1+1"))
        assertEquals("'+90532", CsvWriter.text("+90532"))
        assertEquals("'-5", CsvWriter.text("-5"))
        assertEquals("'@toplam", CsvWriter.text("@toplam"))
        assertEquals("\"'=A1;B1\"", CsvWriter.text("=A1;B1"))
    }

    @Test fun datesAndRowsUseCrLf() {
        assertEquals("04.11.2026", CsvWriter.date(d("2026-11-04")))
        assertEquals("a;b\r\nc;d\r\n", CsvWriter.build(listOf(listOf("a", "b"), listOf("c", "d"))))
    }

    @Test fun bytesStartWithUtf8Bom() {
        val b = CsvWriter.bytes("ş")
        assertEquals(0xEF, b[0].toInt() and 0xff)
        assertEquals(0xBB, b[1].toInt() and 0xff)
        assertEquals(0xBF, b[2].toInt() and 0xff)
        assertEquals("ş", String(b.copyOfRange(3, b.size), Charsets.UTF_8))
    }

    // ---- rapor çıktıları ----
    private val range = DateRange(d("2026-11-01"), d("2026-11-30"))

    @Test fun collectionsCsv() {
        val l = LedgerLine(
            LedgerEntry(1, 1, TransactionType.COLLECTION, 50_000, d("2026-11-10"), null, "kısmi; \"nakit\"", null, false, 0),
            "Ayşe"
        )
        val rows = csvLines(ReportCsv.collections(listOf(l)))
        assertEquals("Tarih;Kişi;Tutar;Açıklama", rows[0])
        assertEquals("10.11.2026;Ayşe;500,00;\"kısmi; \"\"nakit\"\"\"", rows[1])
    }

    @Test fun personsCsvHasOpeningLinesAndClosing() {
        val p = Person(1, "Ayşe", null, "", 0, false, 0, 0)
        val e = LedgerEntry(1, 1, TransactionType.PAID_ON_BEHALF, 150_000, d("2026-11-02"), null, "Elektrik", null, false, 0)
        val st = PersonStatement(p, 0, listOf(StatementLine(e, 150_000, 150_000)), 150_000, 150_000, 0, 0, 0)
        val rows = csvLines(ReportCsv.persons(range, listOf(st)))
        assertEquals("Kişi;Tarih;Tür;Açıklama;Tutar (bakiyeye etki);Bakiye", rows[0])
        assertEquals("Ayşe;01.11.2026;Dönem başı bakiye;;;0,00", rows[1])
        assertEquals("Ayşe;02.11.2026;Adına ödeme;Elektrik;1500,00;1500,00", rows[2])
        assertEquals("Ayşe;30.11.2026;Dönem sonu bakiye;;;1500,00", rows[3])
    }

    @Test fun invoicesCsv() {
        val o = InvoiceOccurrence(1, 10, "Elektrik", InvoiceType.ELECTRICITY, null, null, "2026-11", d("2026-11-05"), 150_000, OccurrenceStatus.PAID, d("2026-11-03"))
        val h = InvoiceHistory(10, "Elektrik", null, listOf(o), 150_000)
        val rows = csvLines(ReportCsv.invoices(listOf(h)))
        assertEquals("Fatura;Tür;Kişi;Dönem;Son ödeme tarihi;Ödeme tarihi;Tutar", rows[0])
        assertEquals("Elektrik;Elektrik;Kendim;2026-11;05.11.2026;03.11.2026;1500,00", rows[1])
    }

    @Test fun pendingCsvMarksStatusAndUnknownAmount() {
        val overdue = PendingItem(PendingKind.INVOICE, "Su", "Ayşe", d("2026-11-05"), 40_000, 5, 1)
        val upcoming = PendingItem(PendingKind.PAYABLE, "Borç", null, d("2026-11-30"), null, 0, 2)
        val report = PendingReport(listOf(overdue), listOf(upcoming), 40_000, 0, 0, 0, 1)
        val rows = csvLines(ReportCsv.pending(report))
        assertEquals("Durum;Tür;Ad;Kişi;Vade;Gecikme (gün);Tutar", rows[0])
        assertEquals("Gecikmiş;Fatura;Su;Ayşe;05.11.2026;5;400,00", rows[1])
        assertEquals("Bekliyor;Borç;Borç;;30.11.2026;0;", rows[2])
    }

    @Test fun summaryCsvContainsSectionsAndComparison() {
        val s = PeriodSummary(0, 50_000, 20_000, 10_000, 5_000, 4)
        val p = PeriodSummary(100_000, 0, 0, 30_000, 0, 2)
        val cmp = ReportCalculator.compare(s, p)
        val point = SeriesPoint("Kas 2026", range, s)
        val csv = ReportCsv.summary(range, range.previous(), cmp, listOf(point), listOf(SeriesPoint("2026", range, s)))
        val rows = csvLines(csv)
        assertEquals("Rapor;Özet", rows[0])
        assertEquals("Dönem;01.11.2026 - 30.11.2026", rows[1])
        assertEquals("Önceki dönem;01.10.2026 - 31.10.2026", rows[2])
        assertTrue(rows.contains("Kalem;Bu dönem;Önceki dönem;Fark"))
        assertTrue(rows.contains("Alacak oluşan;500,00;1000,00;-500,00"))
        assertTrue(rows.contains("Aylık özet"))
        assertTrue(rows.contains("Yıllık özet"))
        assertTrue(rows.contains("Kas 2026;500,00;100,00;200,00;50,00;250,00"))
    }
}
