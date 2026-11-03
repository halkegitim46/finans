package com.kisisel.finans.ui.home

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kisisel.finans.core.Money
import com.kisisel.finans.domain.model.DashboardTotals
import com.kisisel.finans.domain.model.InvoiceOccurrence
import com.kisisel.finans.domain.model.LedgerLine
import com.kisisel.finans.domain.model.PersonSummary
import com.kisisel.finans.domain.model.TransactionType
import com.kisisel.finans.ui.common.dialogTitle
import com.kisisel.finans.ui.common.NotificationsBanner
import com.kisisel.finans.ui.common.rememberNotificationsEnabled
import com.kisisel.finans.ui.common.dueStatusText
import com.kisisel.finans.ui.common.label
import com.kisisel.finans.ui.common.tr
import com.kisisel.finans.ui.persons.EntryDialog
import java.time.LocalDate

private sealed interface HomeDialog {
    data class PickPaymentKind(val unused: Int = 0) : HomeDialog
    data class PickPerson(val type: TransactionType) : HomeDialog
    data class Entry(val type: TransactionType, val personId: Long, val personName: String) : HomeDialog
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenOccurrence: (Long) -> Unit,
    onOpenPerson: (Long) -> Unit,
    onAddInvoice: () -> Unit,
    onAddPerson: () -> Unit,
    onSeeInvoices: () -> Unit,
    onSeeTransactions: () -> Unit,
    vm: HomeViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<HomeDialog?>(null) }
    val notificationsEnabled = rememberNotificationsEnabled()

    Scaffold(
        contentWindowInsets = WindowInsets.statusBars,
        topBar = { TopAppBar(title = { Text("Kişisel Finans Takip") }) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!notificationsEnabled) item { NotificationsBanner() }

            state.totals?.let { t -> item { TotalsSection(t) } }

            item {
                QuickActions(
                    onLend = { dialog = HomeDialog.PickPerson(TransactionType.LEND) },
                    onBorrow = { dialog = HomeDialog.PickPerson(TransactionType.BORROW) },
                    onInvoice = onAddInvoice,
                    onPay = { dialog = HomeDialog.PickPaymentKind() },
                    onCollect = { dialog = HomeDialog.PickPerson(TransactionType.COLLECTION) }
                )
            }

            if (state.overdue.isNotEmpty()) {
                item { SectionHeader("Geciken faturalar", "Tümü", onSeeInvoices, isAlert = true) }
                items(state.overdue.take(5), key = { "o${it.id}" }) {
                    InvoiceMini(it, state.today) { onOpenOccurrence(it.id) }
                }
            }

            item { SectionHeader("Yaklaşan faturalar (30 gün)", "Tümü", onSeeInvoices) }
            if (state.loaded && state.upcoming.isEmpty()) {
                item { Text("Önümüzdeki 30 günde ödenecek fatura yok.", style = MaterialTheme.typography.bodyMedium) }
            }
            items(state.upcoming.take(5), key = { "u${it.id}" }) {
                InvoiceMini(it, state.today) { onOpenOccurrence(it.id) }
            }

            item { SectionHeader("Son işlemler", "Tümü", onSeeTransactions) }
            if (state.loaded && state.recent.isEmpty()) {
                item { Text("Henüz işlem yok.", style = MaterialTheme.typography.bodyMedium) }
            }
            items(state.recent, key = { "r${it.entry.id}" }) {
                LineMini(it) { onOpenPerson(it.entry.personId) }
            }
        }
    }

    when (val d = dialog) {
        is HomeDialog.PickPaymentKind -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Ödeme yap") },
            text = { Text("Ne tür bir ödeme yapıyorsunuz?") },
            confirmButton = {
                TextButton(onClick = { dialog = HomeDialog.PickPerson(TransactionType.DEBT_PAYMENT) }) {
                    Text("Borcumu öde")
                }
            },
            dismissButton = {
                TextButton(onClick = { dialog = HomeDialog.PickPerson(TransactionType.PAID_ON_BEHALF) }) {
                    Text("Kişi adına öde")
                }
            }
        )

        is HomeDialog.PickPerson -> PersonPickerDialog(
            type = d.type,
            persons = state.persons,
            onPick = { dialog = HomeDialog.Entry(d.type, it.person.id, it.person.fullName) },
            onAddPerson = { dialog = null; onAddPerson() },
            onDismiss = { dialog = null }
        )

        is HomeDialog.Entry -> {
            val summary = state.persons.firstOrNull { it.person.id == d.personId }
            val hint = when (d.type) {
                TransactionType.COLLECTION -> summary?.let { "Kalan alacak: ${Money.format(it.balance.remainingReceivable)}" }
                TransactionType.DEBT_PAYMENT -> summary?.let { "Kalan borç: ${Money.format(it.balance.remainingPayable)}" }
                else -> null
            }
            EntryDialog(
                title = "${d.type.dialogTitle()} – ${d.personName}",
                showDueDate = !d.type.isSettlement,
                initialDay = vm.today().toEpochDay(),
                hint = hint,
                onDismiss = { dialog = null },
                onSubmit = { amount, date, due, desc, key, onResult ->
                    vm.submit(d.personId, d.type, amount, date, due, desc, key, onResult)
                }
            )
        }

        null -> Unit
    }
}

@Composable
private fun TotalsSection(t: DashboardTotals) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Net bakiye (alacak − borç)", style = MaterialTheme.typography.labelLarge)
                Text(
                    Money.format(t.net),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (t.net >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("Toplam alacak", t.remainingReceivable, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
            StatCard("Toplam borç", t.remainingPayable, MaterialTheme.colorScheme.error, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("Bu ay ödemeler", t.monthPaid, Color.Unspecified, Modifier.weight(1f))
            StatCard("Bu ay tahsilatlar", t.monthCollected, Color.Unspecified, Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatCard(label: String, kurus: Long, color: Color, modifier: Modifier) {
    Card(modifier = modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(
                Money.format(kurus),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
        }
    }
}

@Composable
private fun QuickActions(
    onLend: () -> Unit,
    onBorrow: () -> Unit,
    onInvoice: () -> Unit,
    onPay: () -> Unit,
    onCollect: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Hızlı işlemler", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = onLend, modifier = Modifier.weight(1f)) { Text("Alacak ekle") }
            FilledTonalButton(onClick = onBorrow, modifier = Modifier.weight(1f)) { Text("Borç ekle") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = onInvoice, modifier = Modifier.weight(1f)) { Text("Fatura ekle") }
            FilledTonalButton(onClick = onPay, modifier = Modifier.weight(1f)) { Text("Ödeme yap") }
        }
        Button(onClick = onCollect, modifier = Modifier.fillMaxWidth()) { Text("Tahsilat al") }
    }
}

@Composable
private fun SectionHeader(title: String, action: String, onAction: () -> Unit, isAlert: Boolean = false) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = if (isAlert) MaterialTheme.colorScheme.error else Color.Unspecified
        )
        TextButton(onClick = onAction) { Text(action) }
    }
}

@Composable
private fun InvoiceMini(o: InvoiceOccurrence, today: LocalDate, onClick: () -> Unit) {
    val overdue = o.isOverdue(today)
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(o.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    listOfNotNull(o.personName, "${o.dueDate.tr()} • ${dueStatusText(o.status, o.daysUntilDue(today))}")
                        .joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (overdue) MaterialTheme.colorScheme.error else Color.Unspecified
                )
            }
            Text(
                o.amountKurus?.let { Money.format(it) } ?: "Tutar yok",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun LineMini(line: LedgerLine, onClick: () -> Unit) {
    val e = line.entry
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${e.type.label()} • ${line.personName}", style = MaterialTheme.typography.titleSmall)
                Text(
                    e.date.tr() + if (e.isCancelled) " • İptal edildi" else "",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(
                Money.format(e.amountKurus),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                textDecoration = if (e.isCancelled) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
            )
        }
    }
}

/** Hızlı işlem için kişi seçici. Tahsilat/ödemede yalnızca açık bakiyesi olan kişiler listelenir. */
@Composable
private fun PersonPickerDialog(
    type: TransactionType,
    persons: List<PersonSummary>,
    onPick: (PersonSummary) -> Unit,
    onAddPerson: () -> Unit,
    onDismiss: () -> Unit
) {
    val candidates = persons.filter {
        when (type) {
            TransactionType.COLLECTION -> it.balance.remainingReceivable > 0
            TransactionType.DEBT_PAYMENT -> it.balance.remainingPayable > 0
            else -> true
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${type.dialogTitle()}: kişi seçin") },
        text = {
            when {
                persons.isEmpty() -> Text("Henüz kişi yok. Önce bir kişi ekleyin.")
                candidates.isEmpty() -> Text(
                    if (type == TransactionType.COLLECTION) "Tahsil edilecek açık alacak yok." else "Ödenecek açık borç yok."
                )
                else -> LazyColumn {
                    items(candidates, key = { it.person.id }) { s ->
                        Column(Modifier.fillMaxWidth().clickable { onPick(s) }.padding(vertical = 10.dp)) {
                            Text(s.person.fullName, style = MaterialTheme.typography.titleSmall)
                            val sub = when (type) {
                                TransactionType.COLLECTION -> "Kalan alacak: ${Money.format(s.balance.remainingReceivable)}"
                                TransactionType.DEBT_PAYMENT -> "Kalan borç: ${Money.format(s.balance.remainingPayable)}"
                                else -> null
                            }
                            sub?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {
            if (persons.isEmpty()) TextButton(onClick = onAddPerson) { Text("Kişi ekle") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } }
    )
}
