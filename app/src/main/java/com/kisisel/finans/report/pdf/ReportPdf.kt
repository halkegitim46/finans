package com.kisisel.finans.report.pdf

import com.kisisel.finans.core.Money
import com.kisisel.finans.report.ComparisonRow
import com.kisisel.finans.report.CsvWriter
import com.kisisel.finans.report.DateRange
import com.kisisel.finans.report.InvoiceHistory
import com.kisisel.finans.report.PendingItem
import com.kisisel.finans.report.PendingReport
import com.kisisel.finans.report.PersonStatement
import com.kisisel.finans.report.ReportCsv
import com.kisisel.finans.report.SeriesPoint
import com.kisisel.finans.domain.model.LedgerLine
import java.time.LocalDate
import java.util.Locale

/** Rapor verisini PDF bloklarına çevirir (saf; çizim PdfReportRenderer'dadır). */
object ReportPdf {
    private fun m(v: Long) = Money.format(v)
    private fun d(v: LocalDate) = CsvWriter.date(v)
    private fun rangeText(r: DateRange) = "${d(r.start)} - ${d(r.end)}"
    private fun signed(v: Long) = (if (v > 0) "+" else "") + m(v)
    private fun percent(r: ComparisonRow) = r.percent?.let { String.format(Locale("tr", "TR"), "%+.0f%%", it) } ?: "-"

    private fun empty(text: String) = PdfBlock.Paragraph(text)

    fun summary(
        range: DateRange,
        previousRange: DateRange,
        transactionCount: Int,
        comparison: List<ComparisonRow>,
        monthly: List<SeriesPoint>,
        yearly: List<SeriesPoint>
    ): PdfReport {
        val blocks = mutableListOf<PdfBlock>()
        blocks += PdfBlock.Title("Özet Raporu")
        blocks += PdfBlock.Subtitle("Dönem: ${rangeText(range)}  •  Karşılaştırma: ${rangeText(previousRange)}")

        blocks += PdfBlock.Heading("Dönem özeti ($transactionCount işlem)")
        comparison.forEach { blocks += PdfBlock.KeyValue(it.label, m(it.current), bold = it.label == "Net değişim") }

        blocks += PdfBlock.Heading("Önceki dönemle karşılaştırma")
        blocks += PdfBlock.Table(
            headers = listOf("Kalem", "Bu dönem", "Önceki dönem", "Fark", "%"),
            weights = listOf(1.6f, 1.3f, 1.3f, 1.3f, 0.7f),
            rightAligned = setOf(1, 2, 3, 4),
            rows = comparison.map {
                PdfBlock.TableRow(listOf(it.label, m(it.current), m(it.previous), signed(it.delta), percent(it)))
            }
        )

        blocks += PdfBlock.Heading("Grafikler")
        val labels = monthly.map { it.label }
        blocks += PdfBlock.BarChart(
            "Aylık: alacak ve tahsilat", labels,
            listOf(
                PdfBlock.ChartSeries("Alacak oluşan", PdfColors.GREEN, monthly.map { it.summary.newReceivables }),
                PdfBlock.ChartSeries("Tahsilat", PdfColors.AMBER, monthly.map { it.summary.collected })
            )
        )
        blocks += PdfBlock.BarChart(
            "Aylık: borç ve ödeme", labels,
            listOf(
                PdfBlock.ChartSeries("Borç oluşan", PdfColors.RED, monthly.map { it.summary.borrowed }),
                PdfBlock.ChartSeries("Borç ödemesi", PdfColors.BLUE, monthly.map { it.summary.debtPaid })
            )
        )

        fun seriesTable(title: String, first: String, points: List<SeriesPoint>) {
            blocks += PdfBlock.Heading(title)
            blocks += PdfBlock.Table(
                headers = listOf(first, "Alacak oluşan", "Tahsilat", "Borç oluşan", "Borç ödemesi", "Net değişim"),
                weights = listOf(1.1f, 1.3f, 1.2f, 1.3f, 1.3f, 1.3f),
                rightAligned = setOf(1, 2, 3, 4, 5),
                rows = points.map {
                    val s = it.summary
                    PdfBlock.TableRow(listOf(it.label, m(s.newReceivables), m(s.collected), m(s.borrowed), m(s.debtPaid), m(s.netChange)))
                }
            )
        }
        seriesTable("Aylık özet", "Ay", monthly)
        seriesTable("Yıllık özet", "Yıl", yearly)
        return PdfReport("Özet Raporu", blocks)
    }

    /** Tek kişi için statements tek elemanlı verilir. */
    fun persons(range: DateRange, statements: List<PersonStatement>): PdfReport {
        val single = statements.size == 1
        val title = if (single) "Hesap Dökümü: ${statements[0].person.fullName}" else "Kişi Hesap Dökümü"
        val blocks = mutableListOf<PdfBlock>()
        blocks += PdfBlock.Title(title)
        blocks += PdfBlock.Subtitle("Dönem: ${rangeText(range)}")
        blocks += PdfBlock.Paragraph("Bakiye: + = kişi bana borçlu, - = ben borçluyum.")
        if (statements.isEmpty()) blocks += empty("Bu dönemde hareketi veya bakiyesi olan kişi yok.")

        for (st in statements) {
            blocks += PdfBlock.Heading(st.person.fullName)
            blocks += PdfBlock.KeyValue("Dönem başı bakiye", m(st.openingAtStart))
            if (st.lines.isEmpty()) {
                blocks += empty("Bu dönemde hareket yok.")
            } else {
                blocks += PdfBlock.Table(
                    headers = listOf("Tarih", "Tür", "Açıklama", "Tutar", "Bakiye"),
                    weights = listOf(1.1f, 1.3f, 2.2f, 1.2f, 1.3f),
                    rightAligned = setOf(3, 4),
                    rows = st.lines.map {
                        PdfBlock.TableRow(
                            listOf(d(it.entry.date), ReportCsv.typeLabel(it.entry.type), it.entry.description, signed(it.delta), m(it.balanceAfter))
                        )
                    }
                )
            }
            blocks += PdfBlock.KeyValue("Dönem sonu bakiye", m(st.closing), bold = true)
            blocks += PdfBlock.Spacer(8f)
        }
        return PdfReport(title, blocks)
    }

    fun invoices(range: DateRange, history: List<InvoiceHistory>): PdfReport {
        val blocks = mutableListOf<PdfBlock>()
        blocks += PdfBlock.Title("Fatura Ödeme Geçmişi")
        blocks += PdfBlock.Subtitle("Dönem: ${rangeText(range)} (ödeme tarihine göre)")
        if (history.isEmpty()) blocks += empty("Bu dönemde ödenmiş fatura yok.")
        for (h in history) {
            blocks += PdfBlock.Heading("${h.name} • ${h.personName ?: "Kendim"} • ${h.count} ödeme • Toplam ${m(h.totalKurus)}")
            blocks += PdfBlock.Table(
                headers = listOf("Dönem", "Son ödeme", "Ödeme tarihi", "Tutar"),
                weights = listOf(1.3f, 1.3f, 1.3f, 1.2f),
                rightAligned = setOf(3),
                rows = h.items.map {
                    PdfBlock.TableRow(listOf(it.periodKey, d(it.dueDate), it.paidDate?.let { p -> d(p) } ?: "-", it.amountKurus?.let { a -> m(a) } ?: "-"))
                }
            )
        }
        return PdfReport("Fatura Ödeme Geçmişi", blocks)
    }

    fun collections(range: DateRange, lines: List<LedgerLine>, total: Long): PdfReport {
        val blocks = mutableListOf<PdfBlock>()
        blocks += PdfBlock.Title("Tahsilat Geçmişi")
        blocks += PdfBlock.Subtitle("Dönem: ${rangeText(range)}")
        blocks += PdfBlock.KeyValue("Toplam tahsilat", m(total), bold = true)
        blocks += PdfBlock.KeyValue("İşlem sayısı", lines.size.toString())
        if (lines.isEmpty()) {
            blocks += empty("Bu dönemde tahsilat yok.")
        } else {
            blocks += PdfBlock.Spacer(6f)
            blocks += PdfBlock.Table(
                headers = listOf("Tarih", "Kişi", "Açıklama", "Tutar"),
                weights = listOf(1.1f, 1.6f, 2.2f, 1.2f),
                rightAligned = setOf(3),
                rows = lines.map { PdfBlock.TableRow(listOf(d(it.entry.date), it.personName, it.entry.description, m(it.entry.amountKurus))) }
            )
        }
        return PdfReport("Tahsilat Geçmişi", blocks)
    }

    fun pending(today: LocalDate, report: PendingReport): PdfReport {
        val blocks = mutableListOf<PdfBlock>()
        blocks += PdfBlock.Title("Bekleyen ve Geciken Ödemeler")
        blocks += PdfBlock.Subtitle("Durum: ${d(today)} itibarıyla")
        blocks += PdfBlock.KeyValue("Geciken: ödenecek", m(report.overdueToPay))
        blocks += PdfBlock.KeyValue("Geciken: tahsil edilecek", m(report.overdueToCollect))
        blocks += PdfBlock.KeyValue("Bekleyen: ödenecek", m(report.upcomingToPay))
        blocks += PdfBlock.KeyValue("Bekleyen: tahsil edilecek", m(report.upcomingToCollect))
        if (report.unknownAmountCount > 0) {
            blocks += PdfBlock.Paragraph("${report.unknownAmountCount} kalemin tutarı henüz girilmediği için toplamlara dahil değil.")
        }

        fun section(title: String, items: List<PendingItem>, overdue: Boolean, emptyText: String) {
            blocks += PdfBlock.Heading("$title (${items.size})")
            if (items.isEmpty()) {
                blocks += empty(emptyText)
                return
            }
            blocks += PdfBlock.Table(
                headers = listOf("Vade", "Gecikme", "Tür", "Ad", "Kişi", "Tutar"),
                weights = listOf(1.1f, 0.9f, 0.9f, 1.8f, 1.4f, 1.2f),
                rightAligned = setOf(5),
                rows = items.map {
                    PdfBlock.TableRow(
                        listOf(
                            d(it.dueDate),
                            if (overdue) "${it.daysOverdue} gün" else "-",
                            ReportCsv.kindLabel(it.kind),
                            it.title,
                            it.personName ?: "",
                            it.amountKurus?.let { a -> m(a) } ?: "Tutar yok"
                        ),
                        if (overdue) Emphasis.ALERT else Emphasis.NORMAL
                    )
                }
            )
        }
        section("Geciken ödemeler", report.overdue, true, "Geciken ödeme yok.")
        section("Ödenmemiş / bekleyen", report.upcoming, false, "Bekleyen kalem yok.")
        return PdfReport("Bekleyen ve Geciken Ödemeler", blocks)
    }

    /** Dosya adı için güvenli (ASCII) kısa ad: "Çağla Öztürk" -> "Cagla_Ozturk". */
    fun fileSafe(name: String): String {
        val map = mapOf(
            'ç' to 'c', 'ğ' to 'g', 'ı' to 'i', 'ö' to 'o', 'ş' to 's', 'ü' to 'u',
            'Ç' to 'C', 'Ğ' to 'G', 'İ' to 'I', 'Ö' to 'O', 'Ş' to 'S', 'Ü' to 'U'
        )
        val ascii = name.map { map[it] ?: it }.joinToString("")
        val cleaned = ascii.replace(Regex("[^A-Za-z0-9]+"), "_").trim('_').take(40)
        return cleaned.ifEmpty { "kisi" }
    }
}
