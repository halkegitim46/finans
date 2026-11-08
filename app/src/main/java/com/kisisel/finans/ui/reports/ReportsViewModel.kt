package com.kisisel.finans.ui.reports

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kisisel.finans.core.DayTicker
import com.kisisel.finans.domain.model.LedgerLine
import com.kisisel.finans.domain.repository.InvoiceRepository
import com.kisisel.finans.domain.repository.LedgerRepository
import com.kisisel.finans.domain.repository.PersonRepository
import com.kisisel.finans.report.ComparisonRow
import com.kisisel.finans.report.CsvWriter
import com.kisisel.finans.report.DateRange
import com.kisisel.finans.report.InvoiceHistory
import com.kisisel.finans.report.PendingReport
import com.kisisel.finans.report.PeriodSummary
import com.kisisel.finans.report.PersonStatement
import com.kisisel.finans.report.RangePreset
import com.kisisel.finans.report.ReportCalculator
import com.kisisel.finans.report.ReportCsv
import com.kisisel.finans.report.SeriesPoint
import com.kisisel.finans.report.pdf.PdfReport
import com.kisisel.finans.report.pdf.PdfReportRenderer
import com.kisisel.finans.report.pdf.ReportPdf
import com.kisisel.finans.security.AppLockManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject

enum class ReportTab(val id: String, val label: String) {
    SUMMARY("ozet", "Özet"),
    PERSONS("kisiler", "Kişiler"),
    INVOICES("faturalar", "Faturalar"),
    COLLECTIONS("tahsilat", "Tahsilat"),
    PENDING("bekleyen", "Bekleyen")
}

enum class ExportKind { CSV, PDF }

/** Dosya seçiciye verilecek ad ve tür; hazır olan baytlar ViewModel'de bekler. */
data class ExportRequest(val name: String, val kind: ExportKind)

data class ReportsState(
    val today: LocalDate,
    val preset: RangePreset,
    val range: DateRange,
    val previousRange: DateRange,
    val summary: PeriodSummary,
    val previousSummary: PeriodSummary,
    val comparison: List<ComparisonRow>,
    val monthly: List<SeriesPoint>,
    val yearly: List<SeriesPoint>,
    val statements: List<PersonStatement>,
    val collections: List<LedgerLine>,
    val collectionsTotal: Long,
    val invoiceHistory: List<InvoiceHistory>,
    val pending: PendingReport,
    val loaded: Boolean
)

@HiltViewModel
class ReportsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    persons: PersonRepository,
    ledger: LedgerRepository,
    invoices: InvoiceRepository,
    private val lockManager: AppLockManager,
    private val dayTicker: DayTicker
) : ViewModel() {

    private val preset = MutableStateFlow(RangePreset.THIS_MONTH)
    private val custom = MutableStateFlow<DateRange?>(null)

    private data class Selection(val preset: RangePreset, val range: DateRange, val today: LocalDate)

    /** Hazır dönemler (bu ay, bu yıl…) gün/ay değişince kendiliğinden kayar; özel aralık sabit kalır. */
    private val selection = combine(preset, custom, dayTicker.today) { p, c, t ->
        Selection(p, p.range(t) ?: c ?: DateRange(t, t), t)
    }

    val state: StateFlow<ReportsState> = combine(
        persons.observeAll(),
        ledger.observeAllLines(),
        invoices.observeOccurrences(),
        ledger.observeClaims(),
        selection
    ) { people, lines, occurrences, claims, sel ->
        val range = sel.range
        val previous = range.previous()
        val summary = ReportCalculator.periodSummary(lines, range)
        val previousSummary = ReportCalculator.periodSummary(lines, previous)
        val collections = ReportCalculator.collections(lines, range)
        ReportsState(
            today = sel.today,
            preset = sel.preset,
            range = range,
            previousRange = previous,
            summary = summary,
            previousSummary = previousSummary,
            comparison = ReportCalculator.compare(summary, previousSummary),
            monthly = ReportCalculator.monthlySeries(lines, range),
            yearly = ReportCalculator.yearlySeries(lines, range),
            statements = ReportCalculator.personStatements(people, lines, range),
            collections = collections,
            collectionsTotal = ReportCalculator.collectionsTotal(collections),
            invoiceHistory = ReportCalculator.invoiceHistory(occurrences, range),
            pending = ReportCalculator.pending(occurrences, claims, sel.today),
            loaded = true
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialState())

    private fun initialState(): ReportsState {
        val today = dayTicker.today.value
        val r = RangePreset.THIS_MONTH.range(today) ?: DateRange(today, today)
        val empty = PeriodSummary(0, 0, 0, 0, 0, 0)
        return ReportsState(
            today, RangePreset.THIS_MONTH, r, r.previous(), empty, empty, emptyList(), emptyList(), emptyList(),
            emptyList(), emptyList(), 0, emptyList(),
            PendingReport(emptyList(), emptyList(), 0, 0, 0, 0, 0), loaded = false
        )
    }

    // ---- aralık seçimi ----
    fun onPreset(p: RangePreset) {
        if (p == RangePreset.CUSTOM && custom.value == null) custom.value = state.value.range
        preset.value = p
    }

    fun onCustomStart(epochDay: Long) {
        val r = state.value.range
        val s = LocalDate.ofEpochDay(epochDay)
        custom.value = DateRange(s, maxOf(s, r.end))
        preset.value = RangePreset.CUSTOM
    }

    fun onCustomEnd(epochDay: Long) {
        val r = state.value.range
        val e = LocalDate.ofEpochDay(epochDay)
        custom.value = DateRange(minOf(r.start, e), e)
        preset.value = RangePreset.CUSTOM
    }

    // ---- Dışa aktarma (CSV / PDF) ----
    private val exportRequest = MutableStateFlow<ExportRequest?>(null)
    val exportRequestState: StateFlow<ExportRequest?> = exportRequest.asStateFlow()
    private var pendingBytes: ByteArray? = null

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun prepareExport(tab: ReportTab) {
        val s = state.value
        val csv = when (tab) {
            ReportTab.SUMMARY -> ReportCsv.summary(s.range, s.previousRange, s.comparison, s.monthly, s.yearly)
            ReportTab.PERSONS -> ReportCsv.persons(s.range, s.statements)
            ReportTab.INVOICES -> ReportCsv.invoices(s.invoiceHistory)
            ReportTab.COLLECTIONS -> ReportCsv.collections(s.collections)
            ReportTab.PENDING -> ReportCsv.pending(s.pending)
        }
        pendingBytes = CsvWriter.bytes(csv)
        exportRequest.value = ExportRequest("KFT_Rapor_${tab.id}_${s.range.start}_${s.range.end}.csv", ExportKind.CSV)
    }

    /**
     * Açık sekmenin PDF'i. Kişiler sekmesinde [personId] verilirse yalnızca o kişinin hesap dökümü.
     * PDF şifrelenmez.
     */
    fun preparePdf(tab: ReportTab, personId: Long? = null) {
        val s = state.value
        val report: PdfReport = when (tab) {
            ReportTab.SUMMARY ->
                ReportPdf.summary(s.range, s.previousRange, s.summary.count, s.comparison, s.monthly, s.yearly)
            ReportTab.PERSONS ->
                ReportPdf.persons(s.range, if (personId == null) s.statements else s.statements.filter { it.person.id == personId })
            ReportTab.INVOICES -> ReportPdf.invoices(s.range, s.invoiceHistory)
            ReportTab.COLLECTIONS -> ReportPdf.collections(s.range, s.collections, s.collectionsTotal)
            ReportTab.PENDING -> ReportPdf.pending(s.today, s.pending)
        }
        val personName = personId?.let { id -> s.statements.firstOrNull { it.person.id == id }?.person?.fullName }
        val name = if (tab == ReportTab.PERSONS && personName != null) {
            "KFT_Hesap_${ReportPdf.fileSafe(personName)}_${s.range.start}_${s.range.end}.pdf"
        } else {
            "KFT_Rapor_${tab.id}_${s.range.start}_${s.range.end}.pdf"
        }
        viewModelScope.launch {
            try {
                val bytes = withContext(Dispatchers.Default) {
                    PdfReportRenderer.render(report, CsvWriter.date(s.today))
                }
                pendingBytes = bytes
                exportRequest.value = ExportRequest(name, ExportKind.PDF)
            } catch (e: Exception) {
                _messages.tryEmit("PDF oluşturulamadı.")
            }
        }
    }

    /** Dosya seçici açılmadan önce: dönüşte uygulama kilitlenmesin. */
    fun allowExternalUi() = lockManager.allowExternalUi(SystemClock.elapsedRealtime())

    /** Seçici açıldı; tekrar tetiklenmesin. */
    fun exportLaunched() {
        exportRequest.value = null
    }

    fun onExportUri(uri: Uri?) {
        lockManager.endExternalUi()
        val bytes = pendingBytes
        pendingBytes = null
        if (uri == null || bytes == null) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
                    ?: throw java.io.IOException("açılamadı")
                _messages.tryEmit("Rapor dışa aktarıldı.")
            } catch (e: Exception) {
                _messages.tryEmit("Dosya yazılamadı.")
            }
        }
    }
}
