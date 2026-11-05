package com.kisisel.finans.report.pdf

enum class Align { LEFT, RIGHT, CENTER }

enum class Emphasis { NORMAL, BOLD, ALERT, MUTED }

/** Raporun düzenden bağımsız içerik tanımı. */
sealed interface PdfBlock {
    data class Title(val text: String) : PdfBlock
    data class Subtitle(val text: String) : PdfBlock
    data class Heading(val text: String) : PdfBlock
    data class Paragraph(val text: String) : PdfBlock
    data class KeyValue(val label: String, val value: String, val bold: Boolean = false) : PdfBlock
    data class Spacer(val height: Float) : PdfBlock

    data class TableRow(val cells: List<String>, val emphasis: Emphasis = Emphasis.NORMAL)

    /** weights: sütun genişlik oranları; rightAligned: sağa yaslanacak sütun indeksleri (tutarlar). */
    data class Table(
        val headers: List<String>,
        val weights: List<Float>,
        val rightAligned: Set<Int>,
        val rows: List<TableRow>
    ) : PdfBlock

    data class ChartSeries(val name: String, val color: Int, val values: List<Long>)

    /** Gruplanmış sütun grafiği: her etiket (ay) için seri başına bir çubuk. Değerler kuruş. */
    data class BarChart(val title: String, val labels: List<String>, val series: List<ChartSeries>) : PdfBlock
}

data class PdfReport(val title: String, val blocks: List<PdfBlock>)

/** Sayfa ölçüleri (PDF noktası, 1/72 inç). Varsayılan A4. */
data class PageSpec(
    val width: Float = 595f,
    val height: Float = 842f,
    val margin: Float = 40f,
    val headerHeight: Float = 24f,
    val footerHeight: Float = 24f
) {
    val contentLeft: Float get() = margin
    val contentRight: Float get() = width - margin
    val contentTop: Float get() = margin + headerHeight
    val contentBottom: Float get() = height - margin - footerHeight
    val contentWidth: Float get() = width - 2 * margin
}

/** Metin genişliği ölçer (gerçekte Paint; testlerde sabit genişlikli sahte). */
interface TextMeasurer {
    fun width(text: String, size: Float, bold: Boolean): Float
}

object PdfColors {
    val TEXT: Int = 0xFF212121.toInt()
    val MUTED: Int = 0xFF666666.toInt()
    val ALERT: Int = 0xFFB3261E.toInt()
    val GRID: Int = 0xFFCCCCCC.toInt()
    val HEADER_BG: Int = 0xFFEDEDED.toInt()
    val GREEN: Int = 0xFF0B6E4F.toInt()
    val AMBER: Int = 0xFF8A5A00.toInt()
    val RED: Int = 0xFFB3261E.toInt()
    val BLUE: Int = 0xFF3D5A80.toInt()
}

/** Sayfaya yerleşmiş çizim öğeleri (koordinatlar sayfa sol-üst köşesinden; Text.y satırın üst kenarı). */
sealed interface PlacedItem {
    data class Text(
        val x: Float,
        val y: Float,
        val text: String,
        val size: Float,
        val bold: Boolean,
        val align: Align,
        val color: Int
    ) : PlacedItem

    data class Line(val x1: Float, val y: Float, val x2: Float, val color: Int, val width: Float) : PlacedItem

    data class Rect(val x: Float, val y: Float, val w: Float, val h: Float, val color: Int) : PlacedItem
}

data class PdfPage(val items: List<PlacedItem>)
