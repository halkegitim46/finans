package com.kisisel.finans.report

import com.kisisel.finans.domain.model.InvoiceType
import com.kisisel.finans.domain.model.LedgerLine
import com.kisisel.finans.domain.model.TransactionType
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Excel (Türkçe) uyumlu CSV: ayraç ";", ondalık ",", binlik ayracı yok, UTF-8 BOM, CRLF.
 * Metinler formül enjeksiyonuna karşı korunur (=, +, -, @ ile başlayan metnin başına ' eklenir).
 */
object CsvWriter {
    private const val SEP = ";"
    private val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    private val BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

    fun text(value: String): String {
        var v = value
        if (v.isNotEmpty() && v[0] in "=+-@\t\r") v = "'$v"
        return if (v.any { it == ';' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + v.replace("\"", "\"\"") + "\""
        } else {
            v
        }
    }

    /** 150050 -> "1500,50"; negatif: "-1500,50". */
    fun amount(kurus: Long): String {
        val neg = kurus < 0
        val abs = if (kurus == Long.MIN_VALUE) Long.MAX_VALUE else kotlin.math.abs(kurus)
        return (if (neg) "-" else "") + "${abs / 100},${(abs % 100).toString().padStart(2, '0')}"
    }

    fun date(d: LocalDate): String = d.format(DATE_FMT)

    fun build(rows: List<List<String>>): String = rows.joinToString("") { it.joinToString(SEP) + "\r\n" }

    fun bytes(csv: String): ByteArray = BOM + csv.toByteArray(Charsets.UTF_8)
}

object ReportCsv {
    private fun t(v: String) = CsvWriter.text(v)
    private fun m(v: Long) = CsvWriter.amount(v)
    private fun d(v: LocalDate) = CsvWriter.date(v)
    private fun rangeText(r: DateRange) = "${d(r.start)} - ${d(r.end)}"
    private val blank = listOf("")

    fun summary(
        range: DateRange,
        previousRange: DateRange,
        comparison: List<ComparisonRow>,
        monthly: List<SeriesPoint>,
        yearly: List<SeriesPoint>
    ): String {
        val rows = mutableListOf<List<String>>()
        rows += listOf(t("Rapor"), t("Özet"))
        rows += listOf(t("Dönem"), t(rangeText(range)))
        rows += listOf(t("Önceki dönem"), t(rangeText(previousRange)))
        rows += blank
        rows += listOf(t("Kalem"), t("Bu dönem"), t("Önceki dönem"), t("Fark"))
        comparison.forEach { rows += listOf(t(it.label), m(it.current), m(it.previous), m(it.delta)) }

        fun table(title: String, first: String, points: List<SeriesPoint>) {
            rows += blank
            rows += listOf(t(title))
            rows += listOf(t(first), t("Alacak oluşan"), t("Tahsilat"), t("Borç oluşan"), t("Borç ödemesi"), t("Net değişim"))
            points.forEach {
                val s = it.summary
                rows += listOf(t(it.label), m(s.newReceivables), m(s.collected), m(s.borrowed), m(s.debtPaid), m(s.netChange))
            }
        }
        table("Aylık özet", "Ay", monthly)
        table("Yıllık özet", "Yıl", yearly)
        return CsvWriter.build(rows)
    }

    /** Pozitif tutar = kişi bana daha borçlu; negatif = azalır/borç artar. Bakiye: + alacak, − borç. */
    fun persons(range: DateRange, statements: List<PersonStatement>): String {
        val rows = mutableListOf<List<String>>()
        rows += listOf(t("Kişi"), t("Tarih"), t("Tür"), t("Açıklama"), t("Tutar (bakiyeye etki)"), t("Bakiye"))
        statements.forEach { s ->
            val name = t(s.person.fullName)
            rows += listOf(name, d(range.start), t("Dönem başı bakiye"), "", "", m(s.openingAtStart))
            s.lines.forEach { l ->
                rows += listOf(name, d(l.entry.date), t(typeLabel(l.entry.type)), t(l.entry.description), m(l.delta), m(l.balanceAfter))
            }
            rows += listOf(name, d(range.end), t("Dönem sonu bakiye"), "", "", m(s.closing))
        }
        return CsvWriter.build(rows)
    }

    fun invoices(history: List<InvoiceHistory>): String {
        val rows = mutableListOf<List<String>>()
        rows += listOf(t("Fatura"), t("Tür"), t("Kişi"), t("Dönem"), t("Son ödeme tarihi"), t("Ödeme tarihi"), t("Tutar"))
        history.forEach { h ->
            h.items.forEach { o ->
                rows += listOf(
                    t(h.name), t(invoiceTypeLabel(o.type)), t(h.personName ?: "Kendim"), t(o.periodKey),
                    d(o.dueDate), o.paidDate?.let { d(it) } ?: "", m(o.amountKurus ?: 0L)
                )
            }
        }
        return CsvWriter.build(rows)
    }

    fun collections(lines: List<LedgerLine>): String {
        val rows = mutableListOf<List<String>>()
        rows += listOf(t("Tarih"), t("Kişi"), t("Tutar"), t("Açıklama"))
        lines.forEach { rows += listOf(d(it.entry.date), t(it.personName), m(it.entry.amountKurus), t(it.entry.description)) }
        return CsvWriter.build(rows)
    }

    fun pending(report: PendingReport): String {
        val rows = mutableListOf<List<String>>()
        rows += listOf(t("Durum"), t("Tür"), t("Ad"), t("Kişi"), t("Vade"), t("Gecikme (gün)"), t("Tutar"))
        fun add(status: String, items: List<PendingItem>) = items.forEach { i ->
            rows += listOf(
                t(status), t(kindLabel(i.kind)), t(i.title), t(i.personName ?: ""), d(i.dueDate),
                i.daysOverdue.toString(), i.amountKurus?.let { m(it) } ?: ""
            )
        }
        add("Gecikmiş", report.overdue)
        add("Bekliyor", report.upcoming)
        return CsvWriter.build(rows)
    }

    private fun typeLabel(t: TransactionType) = when (t) {
        TransactionType.LEND -> "Borç verildi"
        TransactionType.BORROW -> "Borç alındı"
        TransactionType.PAID_ON_BEHALF -> "Adına ödeme"
        TransactionType.COLLECTION -> "Tahsilat"
        TransactionType.DEBT_PAYMENT -> "Borç ödemesi"
    }

    private fun invoiceTypeLabel(t: InvoiceType) = when (t) {
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

    private fun kindLabel(k: PendingKind) = when (k) {
        PendingKind.INVOICE -> "Fatura"
        PendingKind.RECEIVABLE -> "Alacak"
        PendingKind.PAYABLE -> "Borç"
    }
}
