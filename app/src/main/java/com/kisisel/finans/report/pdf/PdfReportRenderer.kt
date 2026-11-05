package com.kisisel.finans.report.pdf

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.ByteArrayOutputStream

private class PaintMeasurer : TextMeasurer {
    private val regular = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT }
    private val bold = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }

    override fun width(text: String, size: Float, bold: Boolean): Float {
        val p = if (bold) this.bold else regular
        p.textSize = size
        return p.measureText(text)
    }
}

/**
 * Sayfalanmış raporu Android'in yerleşik PdfDocument'ı ile PDF'e çizer (ek kütüphane yok, çevrimdışı).
 * Her sayfada üst bilgi (uygulama adı, rapor adı, oluşturulma tarihi) ve alt bilgi (sayfa numarası) vardır.
 * PDF şifrelenmez.
 */
object PdfReportRenderer {
    private val spec = PageSpec()

    fun render(report: PdfReport, generatedAt: String): ByteArray {
        val pages = PdfPaginator(spec, PaintMeasurer()).layout(report.blocks)
        val document = PdfDocument()
        try {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            pages.forEachIndexed { index, page ->
                val info = PdfDocument.PageInfo.Builder(spec.width.toInt(), spec.height.toInt(), index + 1).create()
                val pdfPage = document.startPage(info)
                drawPage(pdfPage.canvas, paint, page, report.title, generatedAt, index + 1, pages.size)
                document.finishPage(pdfPage)
            }
            val out = ByteArrayOutputStream()
            document.writeTo(out)
            return out.toByteArray()
        } finally {
            document.close()
        }
    }

    private fun drawPage(
        canvas: Canvas,
        paint: Paint,
        page: PdfPage,
        title: String,
        generatedAt: String,
        number: Int,
        total: Int
    ) {
        // Üst bilgi
        text(canvas, paint, spec.margin, spec.margin, "Kişisel Finans Takip • $title", 8f, false, Align.LEFT, PdfColors.MUTED)
        text(canvas, paint, spec.width - spec.margin, spec.margin, generatedAt, 8f, false, Align.RIGHT, PdfColors.MUTED)
        line(canvas, paint, spec.margin, spec.margin + 14f, spec.width - spec.margin, PdfColors.GRID, 0.5f)
        // Alt bilgi
        text(
            canvas, paint, spec.width / 2f, spec.height - spec.margin - 10f,
            "Sayfa $number / $total", 8f, false, Align.CENTER, PdfColors.MUTED
        )

        for (item in page.items) {
            when (item) {
                is PlacedItem.Text -> text(canvas, paint, item.x, item.y, item.text, item.size, item.bold, item.align, item.color)
                is PlacedItem.Line -> line(canvas, paint, item.x1, item.y, item.x2, item.color, item.width)
                is PlacedItem.Rect -> {
                    paint.style = Paint.Style.FILL
                    paint.color = item.color
                    canvas.drawRect(item.x, item.y, item.x + item.w, item.y + item.h, paint)
                }
            }
        }
    }

    private fun text(
        canvas: Canvas, paint: Paint, x: Float, y: Float, text: String,
        size: Float, bold: Boolean, align: Align, color: Int
    ) {
        paint.style = Paint.Style.FILL
        paint.typeface = if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
        paint.textSize = size
        paint.color = color
        val width = paint.measureText(text)
        val left = when (align) {
            Align.LEFT -> x
            Align.RIGHT -> x - width
            Align.CENTER -> x - width / 2f
        }
        // y satırın üst kenarı; taban çizgisi = y - ascent (ascent negatiftir)
        canvas.drawText(text, left, y - paint.ascent(), paint)
    }

    private fun line(canvas: Canvas, paint: Paint, x1: Float, y: Float, x2: Float, color: Int, width: Float) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = width
        paint.color = color
        canvas.drawLine(x1, y, x2, y, paint)
    }
}
