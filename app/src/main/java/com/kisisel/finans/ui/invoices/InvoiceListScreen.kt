package com.kisisel.finans.ui.invoices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kisisel.finans.core.Money
import com.kisisel.finans.domain.model.InvoiceOccurrence
import com.kisisel.finans.domain.model.InvoiceTemplate
import com.kisisel.finans.ui.common.dueStatusText
import com.kisisel.finans.ui.common.label
import com.kisisel.finans.ui.common.tr
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceListScreen(
    onOpenOccurrence: (Long) -> Unit,
    onOpenTemplate: (Long) -> Unit,
    onAdd: () -> Unit,
    vm: InvoiceListViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        contentWindowInsets = WindowInsets.statusBars,
        topBar = { TopAppBar(title = { Text("Faturalar") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Fatura ekle") }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                listOf("Bekleyen", "Ödenen", "Şablonlar").forEachIndexed { i, title ->
                    androidx.compose.material3.Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title) })
                }
            }
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (tab) {
                    0 -> {
                        if (state.loaded && state.pending.isEmpty()) item { Text("Bekleyen fatura yok.") }
                        items(state.pending, key = { it.id }) {
                            OccurrenceCard(it, state.today, showPaidDate = false) { onOpenOccurrence(it.id) }
                        }
                    }
                    1 -> {
                        if (state.loaded && state.paid.isEmpty()) item { Text("Henüz ödenmiş fatura yok.") }
                        items(state.paid, key = { it.id }) {
                            OccurrenceCard(it, state.today, showPaidDate = true) { onOpenOccurrence(it.id) }
                        }
                    }
                    else -> {
                        if (state.loaded && state.templates.isEmpty()) {
                            item { Text("Henüz fatura tanımı yok. \"Fatura ekle\" ile başlayın.") }
                        }
                        items(state.templates, key = { it.id }) {
                            TemplateCard(it) { onOpenTemplate(it.id) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OccurrenceCard(o: InvoiceOccurrence, today: LocalDate, showPaidDate: Boolean, onClick: () -> Unit) {
    val overdue = o.isOverdue(today)
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(o.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    listOfNotNull(o.type.label(), o.personName).joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall
                )
                if (showPaidDate) {
                    Text("Ödendi: ${o.paidDate?.tr() ?: "-"}  •  Vade: ${o.dueDate.tr()}", style = MaterialTheme.typography.bodySmall)
                } else {
                    Text(
                        "Son ödeme: ${o.dueDate.tr()}  •  " + dueStatusText(o.status, o.daysUntilDue(today)),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (overdue) MaterialTheme.colorScheme.error else Color.Unspecified,
                        fontWeight = if (overdue) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
            Text(
                o.amountKurus?.let { Money.format(it) } ?: "Tutar girilmedi",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun TemplateCard(t: InvoiceTemplate, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(t.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    listOfNotNull(t.type.label(), t.recurrence.label(), t.personName ?: "Kendim").joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(
                if (t.isAmountFixed) t.amountKurus?.let { Money.format(it) } ?: "-" else "Değişken",
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}
