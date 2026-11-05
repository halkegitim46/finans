package com.kisisel.finans.report.pdf

import com.kisisel.finans.report.pdf.PdfBlock.BarChart
import com.kisisel.finans.report.pdf.PdfBlock.ChartSeries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sabit genişlikli sahte ölçer: her karakter size * 0.5 geniş. */
private object FakeMeasurer : TextMeasurer {
    override fun width(text: String, size: Float, bold: Boolean): Float = text.length * size * 0.5f
}

class PdfPaginatorTest {
    private val spec = PageSpec()
    private fun layout(vararg blocks: PdfBlock) = PdfPaginator(spec, FakeMeasurer).layout(blocks.toList())

    private fun List<PdfPage>.texts() = flatMap { it.items }.filterIsInstance<PlacedItem.Text>()
    private fun PdfPage.texts() = items.filterIsInstance<PlacedItem.Text>()

    // ---- wrapText ----
    @Test fun wrapBreaksAtWords() {
        assertEquals(listOf("aaa bbb", "ccc"), wrapText("aaa bbb ccc", 40f, 10f, false, FakeMeasurer))
    }

    @Test fun wrapHardBreaksVeryLongWord() {
        assertEquals(listOf("abcde", "fghij", "kl"), wrapText("abcdefghijkl", 25f, 10f, false, FakeMeasurer))
    }

    @Test fun wrapKeepsNewlinesAndEmptyText() {
        assertEquals(listOf("a", "b"), wrapText("a\nb", 100f, 10f, false, FakeMeasurer))
        assertEquals(listOf(""), wrapText("", 100f, 10f, false, FakeMeasurer))
    }

    @Test fun wrapSkipsExtraSpaces() {
        assertEquals(listOf("a b"), wrapText("a   b", 100f, 10f, false, FakeMeasurer))
    }

    // ---- sayfalama ----
    @Test fun emptyDocumentStillHasOnePage() {
        assertEquals(1, layout().size)
    }

    @Test fun simpleBlocksFitOnOnePage() {
        val pages = layout(PdfBlock.Title("Başlık"), PdfBlock.Subtitle("Alt"), PdfBlock.KeyValue("Toplam", "₺1,00", true))
        assertEquals(1, pages.size)
        assertTrue(pages[0].texts().any { it.text == "Başlık" && it.bold })
        val value = pages[0].texts().first { it.text == "₺1,00" }
        assertEquals(Align.RIGHT, value.align)
        assertEquals(spec.contentRight, value.x, 0.01f)
    }

    private fun bigTable(rows: Int) = PdfBlock.Table(
        headers = listOf("No", "Tutar"),
        weights = listOf(1f, 1f),
        rightAligned = setOf(1),
        rows = (1..rows).map { PdfBlock.TableRow(listOf("R%03d".format(it), "₺$it,00")) }
    )

    @Test fun longTableSplitsAcrossPagesWithRepeatedHeaderAndNoLostRows() {
        val pages = layout(PdfBlock.Title("Rapor"), bigTable(120))
        assertTrue("Birden çok sayfa beklenir", pages.size >= 3)

        // Her satır tam bir kez
        val all = pages.texts().map { it.text }
        for (i in 1..120) assertEquals("R%03d".format(i), 1, all.count { it == "R%03d".format(i) })

        // Tablo başlığı (gri dikdörtgen) satır içeren her sayfada bir kez
        for (p in pages) {
            val hasRows = p.texts().any { it.text.startsWith("R0") || it.text.startsWith("R1") }
            val headers = p.items.filterIsInstance<PlacedItem.Rect>().count { it.color == PdfColors.HEADER_BG }
            if (hasRows) assertEquals(1, headers)
        }
    }

    @Test fun nothingIsPlacedBeyondPageContentArea() {
        val pages = layout(PdfBlock.Title("Rapor"), bigTable(200), PdfBlock.Heading("Son"))
        for (t in pages.texts()) {
            assertTrue("üst sınır", t.y >= spec.contentTop - 0.1f)
            assertTrue("alt sınır: ${t.text} y=${t.y}", t.y + t.size * 1.25f <= spec.contentBottom + 0.5f)
        }
    }

    @Test fun rightAlignedColumnAnchorsAtColumnEdge() {
        val pages = layout(
            PdfBlock.Table(listOf("A", "B"), listOf(1f, 1f), setOf(1), listOf(PdfBlock.TableRow(listOf("x", "₺5,00"))))
        )
        val amount = pages[0].texts().first { it.text == "₺5,00" }
        assertEquals(Align.RIGHT, amount.align)
        assertEquals(spec.contentRight - 4f, amount.x, 0.01f)
        val left = pages[0].texts().first { it.text == "x" }
        assertEquals(Align.LEFT, left.align)
        assertEquals(spec.contentLeft + 4f, left.x, 0.01f)
    }

    @Test fun cellTextWrapsAndRowGrowsTaller() {
        val long = "çok uzun bir açıklama metni ".repeat(12).trim()
        val pages = layout(
            PdfBlock.Table(
                listOf("Açıklama", "Tutar"), listOf(1f, 1f), setOf(1),
                listOf(PdfBlock.TableRow(listOf(long, "₺1,00")), PdfBlock.TableRow(listOf("kısa", "₺2,00")))
            )
        )
        val lines = pages[0].texts().filter { it.text.isNotEmpty() && it.x == spec.contentLeft + 4f && it.text != "Açıklama" }
        assertTrue("uzun metin birden çok satıra bölünmeli", lines.size > 2)
    }

    @Test fun emphasisColorsRows() {
        val pages = layout(
            PdfBlock.Table(
                listOf("A"), listOf(1f), emptySet(),
                listOf(
                    PdfBlock.TableRow(listOf("gecikmiş"), Emphasis.ALERT),
                    PdfBlock.TableRow(listOf("normal"), Emphasis.NORMAL),
                    PdfBlock.TableRow(listOf("kalın"), Emphasis.BOLD)
                )
            )
        )
        val t = pages[0].texts()
        assertEquals(PdfColors.ALERT, t.first { it.text == "gecikmiş" }.color)
        assertEquals(PdfColors.TEXT, t.first { it.text == "normal" }.color)
        assertTrue(t.first { it.text == "kalın" }.bold)
    }

    @Test fun mismatchedRowCellsAreHandled() {
        val pages = layout(
            PdfBlock.Table(listOf("A", "B"), listOf(1f, 1f), emptySet(), listOf(PdfBlock.TableRow(listOf("tek"))))
        )
        assertTrue(pages[0].texts().any { it.text == "tek" })
        val pages2 = layout(PdfBlock.Table(listOf("A", "B"), listOf(1f), emptySet(), emptyList()))   // ağırlık sayısı uyumsuz
        assertEquals(1, pages2.size)
    }

    @Test fun headingIsNotLeftAloneAtPageBottom() {
        // Sayfayı neredeyse doldur, sonra başlık + tablo ekle: başlık yeni sayfaya geçmeli
        val filler = PdfBlock.Spacer(spec.contentBottom - spec.contentTop - 30f)
        val pages = layout(filler, PdfBlock.Heading("Bölüm"), bigTable(3))
        val headingPage = pages.indexOfFirst { p -> p.texts().any { it.text == "Bölüm" } }
        val firstRowPage = pages.indexOfFirst { p -> p.texts().any { it.text == "R001" } }
        assertEquals(headingPage, firstRowPage)
    }

    // ---- grafik ----
    private fun chart(vararg values: Pair<String, List<Long>>, seriesNames: List<String> = listOf("A", "B")): BarChart {
        val labels = values.map { it.first }
        val series = seriesNames.indices.map { i ->
            ChartSeries(seriesNames[i], if (i == 0) PdfColors.GREEN else PdfColors.AMBER, values.map { it.second.getOrElse(i) { 0L } })
        }
        return BarChart("Grafik", labels, series)
    }

    private fun rects(pages: List<PdfPage>, color: Int) =
        pages.flatMap { it.items }.filterIsInstance<PlacedItem.Rect>().filter { it.color == color && it.w > 8f && it.w < 20f }

    @Test fun barHeightsAreProportionalAndMaxFillsPlot() {
        val pages = layout(chart("Kas" to listOf(100L, 50L)))
        val a = rects(pages, PdfColors.GREEN).single()
        val b = rects(pages, PdfColors.AMBER).single()
        assertEquals(110f, a.h, 0.01f)
        assertEquals(55f, b.h, 0.01f)
        assertEquals(a.y + a.h, b.y + b.h, 0.01f)          // aynı taban çizgisi
    }

    @Test fun zeroValueIsAThinLine() {
        val pages = layout(chart("Kas" to listOf(100L, 0L)))
        assertEquals(0.8f, rects(pages, PdfColors.AMBER).single().h, 0.001f)
    }

    @Test fun allZeroChartDoesNotDivideByZero() {
        val pages = layout(chart("Kas" to listOf(0L, 0L)))
        assertEquals(0.8f, rects(pages, PdfColors.GREEN).single().h, 0.001f)
    }

    @Test fun manyGroupsSkipLabelsToAvoidOverlap() {
        val groups = (1..30).map { "Kas 2026" to listOf(it * 100L, it * 50L) }.toTypedArray()
        val pages = layout(chart(*groups))
        val labelCount = pages.texts().count { it.text == "Kas 2026" }
        assertTrue("etiketler seyreltilmeli", labelCount in 1 until 30)

        val few = layout(chart("Oca" to listOf(1L, 1L), "Şub" to listOf(2L, 1L), "Mar" to listOf(3L, 1L)))
        assertEquals(3, few.texts().count { it.text in setOf("Oca", "Şub", "Mar") })
    }

    @Test fun chartShowsMaxAndLegend() {
        val pages = layout(chart("Kas" to listOf(150_000L, 50_000L)))
        val texts = pages.texts().map { it.text }
        assertTrue(texts.contains("En yüksek değer: ₺1.500,00"))
        assertTrue(texts.contains("A") && texts.contains("B"))
    }

    @Test fun chartMovesToNextPageWhenItDoesNotFit() {
        val filler = PdfBlock.Spacer(spec.contentBottom - spec.contentTop - 50f)
        val pages = layout(filler, chart("Kas" to listOf(1L, 1L)))
        assertEquals(2, pages.size)
        assertTrue(pages[1].texts().any { it.text == "Grafik" })
    }
}
