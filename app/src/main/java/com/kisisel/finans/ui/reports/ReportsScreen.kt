package com.kisisel.finans.ui.reports

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kisisel.finans.core.Money
import com.kisisel.finans.domain.model.LedgerLine
import com.kisisel.finans.report.ComparisonRow
import com.kisisel.finans.report.DateRange
import com.kisisel.finans.report.InvoiceHistory
import com.kisisel.finans.report.PendingItem
import com.kisisel.finans.report.PendingKind
import com.kisisel.finans.report.PersonStatement
import com.kisisel.finans.report.RangePreset
import com.kisisel.finans.report.SeriesPoint
import com.kisisel.finans.ui.common.ConfirmDialog
import com.kisisel.finans.ui.common.DateField
import com.kisisel.finans.ui.common.DropdownField
import com.kisisel.finans.ui.common.label
import com.kisisel.finans.ui.common.tr
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(vm: ReportsViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val exportName by vm.exportNameState.collectAsStateWithLifecycle()
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    var confirmExport by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val tab = ReportTab.entries[tabIndex]

    val createLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        vm.onExportUri(uri)
    }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(exportName) {
        val name = exportName
        if (name != null) {
            vm.allowExternalUi()
            vm.exportLaunched()
            createLauncher.launch(name)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.statusBars,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Raporlar") },
                actions = {
                    IconButton(onClick = { confirmExport = true }) {
                        Icon(Icons.Filled.Download, contentDescription = "CSV olarak dışa aktar")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            RangeSelector(s.preset, s.range, vm)
            ScrollableTabRow(selectedTabIndex = tabIndex, edgePadding = 8.dp) {
                ReportTab.entries.forEachIndexed { i, t ->
                    Tab(selected = tabIndex == i, onClick = { tabIndex = i }, text = { Text(t.label) })
                }
            }
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (tab) {
                    ReportTab.SUMMARY -> summaryItems(s.summary.count, s.range, s.previousRange, s.comparison, s.monthly, s.yearly)
                    ReportTab.PERSONS -> personItems(s.statements)
                    ReportTab.INVOICES -> invoiceItems(s.invoiceHistory)
                    ReportTab.COLLECTIONS -> collectionItems(s.collections, s.collectionsTotal)
                    ReportTab.PENDING -> pendingItems(s.pending)
                }
            }
        }
    }

    if (confirmExport) {
        ConfirmDialog(
            title = "CSV dışa aktarma",
            message = "\"${tab.label}\" raporu CSV dosyası olarak kaydedilecek. Dosya ŞİFRELENMEZ ve tutarları açık metin içerir; " +
                "güvenli bir yere kaydedin. Excel'de açabilirsiniz.",
            confirmText = "Devam",
            onConfirm = { confirmExport = false; vm.prepareExport(tab) },
            onDismiss = { confirmExport = false }
        )
    }
}

@Composable
private fun RangeSelector(preset: RangePreset, range: DateRange, vm: ReportsViewModel) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DropdownField(
            label = "Dönem",
            options = RangePreset.entries.map { it to it.label },
            selected = preset,
            onSelect = vm::onPreset
        )
        if (preset == RangePreset.CUSTOM) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateField("Başlangıç", range.start.toEpochDay(), vm::onCustomStart, Modifier.weight(1f))
                DateField("Bitiş", range.end.toEpochDay(), vm::onCustomEnd, Modifier.weight(1f))
            }
        } else {
            Text("${range.start.tr()} – ${range.end.tr()}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

// ---------------- Özet ----------------

private fun LazyListScope.summaryItems(
    count: Int,
    range: DateRange,
    previousRange: DateRange,
    comparison: List<ComparisonRow>,
    monthly: List<SeriesPoint>,
    yearly: List<SeriesPoint>
) {
    item {
        ReportCard("Dönem özeti • $count işlem") {
            comparison.forEach { AmountRow(it.label, it.current) }
        }
    }
    item {
        ReportCard("Önceki dönemle karşılaştırma") {
            Text(
                "${previousRange.start.tr()} – ${previousRange.end.tr()} ile",
                style = MaterialTheme.typography.bodySmall
            )
            comparison.forEach { ComparisonLine(it) }
        }
    }
    item {
        val primary = MaterialTheme.colorScheme.primary
        val tertiary = MaterialTheme.colorScheme.tertiary
        ReportCard("Aylık: alacak ve tahsilat") {
            GroupedBarChart(
                groups = monthly.map { BarGroup(it.label, listOf(it.summary.newReceivables, it.summary.collected)) },
                series = listOf(BarSeries("Alacak oluşan", primary), BarSeries("Tahsilat", tertiary))
            )
        }
    }
    item {
        val error = MaterialTheme.colorScheme.error
        val secondary = MaterialTheme.colorScheme.secondary
        ReportCard("Aylık: borç ve ödeme") {
            GroupedBarChart(
                groups = monthly.map { BarGroup(it.label, listOf(it.summary.borrowed, it.summary.debtPaid)) },
                series = listOf(BarSeries("Borç oluşan", error), BarSeries("Borç ödemesi", secondary))
            )
        }
    }
    item { SeriesTable("Aylık özet", monthly) }
    item { SeriesTable("Yıllık özet", yearly) }
}

@Composable
private fun SeriesTable(title: String, points: List<SeriesPoint>) {
    ReportCard(title) {
        points.forEach { p ->
            val s = p.summary
            Column(Modifier.padding(vertical = 4.dp)) {
                Text(p.label, style = MaterialTheme.typography.titleSmall)
                AmountRow("Alacak oluşan", s.newReceivables)
                AmountRow("Tahsilat", s.collected)
                AmountRow("Borç oluşan", s.borrowed)
                AmountRow("Borç ödemesi", s.debtPaid)
                AmountRow("Net değişim", s.netChange)
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun ComparisonLine(r: ComparisonRow) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(r.label, style = MaterialTheme.typography.titleSmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Bu dönem: ${Money.format(r.current)}", style = MaterialTheme.typography.bodyMedium)
            Text("Önceki: ${Money.format(r.previous)}", style = MaterialTheme.typography.bodyMedium)
        }
        val sign = if (r.delta > 0) "+" else ""
        val pct = r.percent?.let { String.format(Locale("tr", "TR"), " (%+.0f%%)", it) } ?: ""
        Text("Fark: $sign${Money.format(r.delta)}$pct", style = MaterialTheme.typography.bodySmall)
    }
    HorizontalDivider()
}

// ---------------- Kişiler ----------------

private fun LazyListScope.personItems(statements: List<PersonStatement>) {
    if (statements.isEmpty()) item { Text("Bu dönemde hareketi veya bakiyesi olan kişi yok.") }
    items(statements, key = { it.person.id }) { st -> PersonStatementCard(st) }
}

@Composable
private fun PersonStatementCard(st: PersonStatement) {
    var open by rememberSaveable(st.person.id) { mutableStateOf(false) }
    Card(onClick = { open = !open }, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(st.person.fullName, style = MaterialTheme.typography.titleMedium)
            AmountRow("Dönem başı bakiye", st.openingAtStart)
            AmountRow("Alacak oluşan", st.periodReceivables)
            AmountRow("Tahsilat", st.periodCollected)
            AmountRow("Borç oluşan", st.periodPayables)
            AmountRow("Borç ödemesi", st.periodDebtPaid)
            AmountRow("Dönem sonu bakiye", st.closing, bold = true)
            Text(
                "+ = kişi bana borçlu, − = ben borçluyum" + if (st.lines.isNotEmpty()) " • ${if (open) "Gizle" else "Hareketleri göster"}" else "",
                style = MaterialTheme.typography.bodySmall
            )
            if (open) {
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                st.lines.forEach { l ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text("${l.entry.date.tr()} • ${l.entry.type.label()}", style = MaterialTheme.typography.bodyMedium)
                            if (l.entry.description.isNotBlank()) Text(l.entry.description, style = MaterialTheme.typography.bodySmall)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(signed(l.delta), style = MaterialTheme.typography.bodyMedium)
                            Text("Bakiye ${Money.format(l.balanceAfter)}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

// ---------------- Faturalar ----------------

private fun LazyListScope.invoiceItems(history: List<InvoiceHistory>) {
    if (history.isEmpty()) item { Text("Bu dönemde ödenmiş fatura yok.") }
    items(history, key = { it.templateId }) { h ->
        var open by rememberSaveable(h.templateId) { mutableStateOf(false) }
        Card(onClick = { open = !open }, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(h.name, style = MaterialTheme.typography.titleMedium)
                Text(h.personName ?: "Kendim", style = MaterialTheme.typography.bodySmall)
                AmountRow("${h.count} ödeme • Toplam", h.totalKurus, bold = true)
                if (open) {
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    h.items.forEach { o ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                "${o.periodKey} • ödendi ${o.paidDate?.tr() ?: "-"}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(o.amountKurus?.let { Money.format(it) } ?: "-", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                } else {
                    Text("Ayrıntı için dokunun", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

// ---------------- Tahsilat ----------------

private fun LazyListScope.collectionItems(collections: List<LedgerLine>, total: Long) {
    item { ReportCard("Toplam tahsilat • ${collections.size} işlem") { AmountRow("Toplam", total, bold = true) } }
    if (collections.isEmpty()) item { Text("Bu dönemde tahsilat yok.") }
    items(collections, key = { it.entry.id }) { l ->
        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(l.personName, style = MaterialTheme.typography.titleSmall)
                    Text(l.entry.date.tr(), style = MaterialTheme.typography.bodySmall)
                    if (l.entry.description.isNotBlank()) Text(l.entry.description, style = MaterialTheme.typography.bodySmall)
                }
                Text(Money.format(l.entry.amountKurus), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ---------------- Bekleyen (geciken + ödenmemiş) ----------------

private fun LazyListScope.pendingItems(p: com.kisisel.finans.report.PendingReport) {
    item {
        ReportCard("Özet (bugün itibarıyla)") {
            AmountRow("Geciken: ödenecek", p.overdueToPay)
            AmountRow("Geciken: tahsil edilecek", p.overdueToCollect)
            AmountRow("Bekleyen: ödenecek", p.upcomingToPay)
            AmountRow("Bekleyen: tahsil edilecek", p.upcomingToCollect)
            if (p.unknownAmountCount > 0) {
                Text("${p.unknownAmountCount} kalemin tutarı henüz girilmediği için toplamlara dahil değil.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    item { Text("Geciken ödemeler (${p.overdue.size})", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error) }
    if (p.overdue.isEmpty()) item { Text("Geciken ödeme yok.") }
    items(p.overdue, key = { "o${it.kind}${it.refId}" }) { PendingCard(it, overdue = true) }
    item { Text("Ödenmemiş / bekleyen (${p.upcoming.size})", style = MaterialTheme.typography.titleMedium) }
    if (p.upcoming.isEmpty()) item { Text("Bekleyen kalem yok.") }
    items(p.upcoming, key = { "u${it.kind}${it.refId}" }) { PendingCard(it, overdue = false) }
}

@Composable
private fun PendingCard(i: PendingItem, overdue: Boolean) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(i.title, style = MaterialTheme.typography.titleSmall)
                Text(
                    listOfNotNull(kindText(i.kind), i.personName).joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "Vade ${i.dueDate.tr()}" + if (overdue) " • ${i.daysOverdue} gün gecikti" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (overdue) MaterialTheme.colorScheme.error else Color.Unspecified
                )
            }
            Text(
                i.amountKurus?.let { Money.format(it) } ?: "Tutar yok",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

private fun kindText(k: PendingKind) = when (k) {
    PendingKind.INVOICE -> "Fatura"
    PendingKind.RECEIVABLE -> "Alacak"
    PendingKind.PAYABLE -> "Borç"
}

// ---------------- ortak parçalar ----------------

@Composable
private fun ReportCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun AmountRow(label: String, kurus: Long, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            Money.format(kurus),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold
        )
    }
}

private fun signed(kurus: Long): String = (if (kurus > 0) "+" else "") + Money.format(kurus)
