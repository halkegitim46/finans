package com.kisisel.finans.report.pdf

import com.kisisel.finans.core.Money
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** Metni verilen genişliğe böler: kelime sınırında, çok uzun kelimeyi harf sınırında; "\n" satır sonudur. */
fun wrapText(text: String, maxWidth: Float, size: Float, bold: Boolean, m: TextMeasurer): List<String> {
    val result = mutableListOf<String>()
    for (paragraph in text.split("\n")) {
        var line = ""
        for (word in paragraph.split(" ")) {
            if (word.isEmpty()) continue
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (m.width(candidate, size, bold) <= maxWidth) {
                line = candidate
                continue
            }
            if (line.isNotEmpty()) {
                result += line
                line = ""
            }
            var rest = word
            while (m.width(rest, size, bold) > maxWidth && rest.length > 1) {
                var cut = rest.length - 1
                while (cut > 1 && m.width(rest.substring(0, cut), size, bold) > maxWidth) cut--
                result += rest.substring(0, cut)
                rest = rest.substring(cut)
            }
            line = rest
        }
        result += line
    }
    return result.ifEmpty { listOf("") }
}

/**
 * Raporu sayfalara yerleştirir (saf mantık; Android'e bağlı değildir).
 * Uzun metin ve tablolar sayfalara bölünür; tablo başlığı her sayfada tekrarlanır; bir tablo satırı bölünmez.
 */
class PdfPaginator(private val spec: PageSpec, private val m: TextMeasurer) {

    private val pages = mutableListOf<List<PlacedItem>>()
    private val items = mutableListOf<PlacedItem>()
    private var y = spec.contentTop

    fun layout(blocks: List<PdfBlock>): List<PdfPage> {
        blocks.forEach { place(it) }
        pages += items.toList()
        return pages.map { PdfPage(it) }
    }

    private fun lh(size: Float) = size * 1.25f

    private fun newPage() {
        pages += items.toList()
        items.clear()
        y = spec.contentTop
    }

    private fun ensureSpace(height: Float) {
        if (y + height > spec.contentBottom && y > spec.contentTop + 0.5f) newPage()
    }

    private fun place(b: PdfBlock) {
        when (b) {
            is PdfBlock.Title -> textBlock(b.text, 18f, true, PdfColors.TEXT, 6f)
            is PdfBlock.Subtitle -> textBlock(b.text, 10f, false, PdfColors.MUTED, 8f)
            is PdfBlock.Heading -> {
                ensureSpace(lh(13f) + 6f + 40f)   // başlık sayfa sonunda yalnız kalmasın
                y += 6f
                textBlock(b.text, 13f, true, PdfColors.TEXT, 4f)
            }
            is PdfBlock.Paragraph -> textBlock(b.text, 9f, false, PdfColors.MUTED, 4f)
            is PdfBlock.KeyValue -> keyValue(b)
            is PdfBlock.Spacer -> y += b.height
            is PdfBlock.Table -> table(b)
            is PdfBlock.BarChart -> chart(b)
        }
    }

    private fun textBlock(text: String, size: Float, bold: Boolean, color: Int, after: Float) {
        for (line in wrapText(text, spec.contentWidth, size, bold, m)) {
            ensureSpace(lh(size))
            items += PlacedItem.Text(spec.contentLeft, y, line, size, bold, Align.LEFT, color)
            y += lh(size)
        }
        y += after
    }

    private fun keyValue(b: PdfBlock.KeyValue) {
        val h = lh(10f) + 3f
        ensureSpace(h)
        items += PlacedItem.Text(spec.contentLeft, y, b.label, 10f, false, Align.LEFT, PdfColors.TEXT)
        items += PlacedItem.Text(spec.contentRight, y, b.value, 10f, b.bold, Align.RIGHT, PdfColors.TEXT)
        y += h
    }

    // ---------------- tablo ----------------

    private fun table(b: PdfBlock.Table) {
        val n = b.headers.size
        if (n == 0) return
        val pad = 4f
        val size = 9f
        val weights = if (b.weights.size == n) b.weights else List(n) { 1f }
        val total = weights.sum()
        val widths = weights.map { it / total * spec.contentWidth }
        val starts = widths.runningFold(spec.contentLeft) { acc, w -> acc + w }

        fun lines(text: String, col: Int, bold: Boolean) = wrapText(text, widths[col] - 2 * pad, size, bold, m)
        fun anchor(col: Int): Pair<Float, Align> =
            if (col in b.rightAligned) (starts[col] + widths[col] - pad) to Align.RIGHT else (starts[col] + pad) to Align.LEFT

        val headerLines = b.headers.mapIndexed { i, h -> lines(h, i, true) }
        val headerH = headerLines.maxOf { it.size } * lh(size) + 6f

        fun drawHeader() {
            items += PlacedItem.Rect(spec.contentLeft, y, spec.contentWidth, headerH, PdfColors.HEADER_BG)
            headerLines.forEachIndexed { col, ls ->
                val (x, align) = anchor(col)
                ls.forEachIndexed { li, line ->
                    items += PlacedItem.Text(x, y + 3f + li * lh(size), line, size, true, align, PdfColors.TEXT)
                }
            }
            y += headerH
        }

        fun cells(row: PdfBlock.TableRow) = List(n) { col -> row.cells.getOrElse(col) { "" } }
        fun layoutRow(row: PdfBlock.TableRow): Pair<List<List<String>>, Float> {
            val bold = row.emphasis == Emphasis.BOLD
            val ls = cells(row).mapIndexed { col, t -> lines(t, col, bold) }
            return ls to (ls.maxOf { it.size } * lh(size) + 6f)
        }

        val laidOut = b.rows.map { layoutRow(it) }
        ensureSpace(headerH + (laidOut.firstOrNull()?.second ?: 0f))
        drawHeader()

        b.rows.forEachIndexed { index, row ->
            val (cellLines, rowH) = laidOut[index]
            if (y + rowH > spec.contentBottom) {
                newPage()
                drawHeader()
            }
            val color = when (row.emphasis) {
                Emphasis.ALERT -> PdfColors.ALERT
                Emphasis.MUTED -> PdfColors.MUTED
                else -> PdfColors.TEXT
            }
            cellLines.forEachIndexed { col, ls ->
                val (x, align) = anchor(col)
                ls.forEachIndexed { li, line ->
                    items += PlacedItem.Text(x, y + 3f + li * lh(size), line, size, row.emphasis == Emphasis.BOLD, align, color)
                }
            }
            y += rowH
            items += PlacedItem.Line(spec.contentLeft, y, spec.contentRight, PdfColors.GRID, 0.5f)
        }
        y += 8f
    }

    // ---------------- grafik ----------------

    private fun chart(b: PdfBlock.BarChart) {
        val plotH = 110f
        val titleH = lh(11f) + 2f
        val legendH = lh(8f) + 4f
        val maxLabelH = lh(8f)
        val labelsH = lh(8f) + 2f
        val total = titleH + legendH + maxLabelH + plotH + labelsH + 10f
        ensureSpace(total)

        var cy = y
        items += PlacedItem.Text(spec.contentLeft, cy, b.title, 11f, true, Align.LEFT, PdfColors.TEXT)
        cy += titleH

        var lx = spec.contentLeft
        for (s in b.series) {
            items += PlacedItem.Rect(lx, cy + 1f, 7f, 7f, s.color)
            lx += 10f
            items += PlacedItem.Text(lx, cy, s.name, 8f, false, Align.LEFT, PdfColors.TEXT)
            lx += m.width(s.name, 8f, false) + 14f
        }
        cy += legendH

        val realMax = b.series.flatMap { it.values }.maxOrNull() ?: 0L
        items += PlacedItem.Text(
            spec.contentLeft, cy, "En yüksek değer: ${Money.format(realMax)}", 8f, false, Align.LEFT, PdfColors.MUTED
        )
        cy += maxLabelH

        val baseY = cy + plotH
        val scaleMax = realMax.coerceAtLeast(1L)
        val groups = b.labels.size
        val groupW = spec.contentWidth / max(groups, 1)
        val seriesCount = max(b.series.size, 1)
        val barW = min(16f, groupW * 0.8f / seriesCount)

        for (g in 0 until groups) {
            val startX = spec.contentLeft + g * groupW + (groupW - barW * seriesCount) / 2f
            b.series.forEachIndexed { i, s ->
                val v = s.values.getOrElse(g) { 0L }
                val h = if (v <= 0L) 0.8f else (v.toDouble() / scaleMax * plotH).toFloat().coerceAtLeast(1f)
                items += PlacedItem.Rect(startX + i * barW, baseY - h, barW, h, s.color)
            }
        }
        items += PlacedItem.Line(spec.contentLeft, baseY, spec.contentRight, PdfColors.GRID, 0.5f)

        // Etiketler çakışmasın: gerekiyorsa k'da bir yaz.
        val maxLabelW = b.labels.maxOfOrNull { m.width(it, 8f, false) } ?: 0f
        val step = ceil(((maxLabelW + 4f) / groupW).toDouble()).toInt().coerceAtLeast(1)
        for (g in 0 until groups step step) {
            items += PlacedItem.Text(
                spec.contentLeft + g * groupW + groupW / 2f, baseY + 2f, b.labels[g], 8f, false, Align.CENTER, PdfColors.TEXT
            )
        }
        y += total
    }
}
