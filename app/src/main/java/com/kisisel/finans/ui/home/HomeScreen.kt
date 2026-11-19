package com.kisisel.finans.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kisisel.finans.agenda.AgendaEntry
import com.kisisel.finans.core.Money
import com.kisisel.finans.domain.model.DashboardTotals
import com.kisisel.finans.domain.model.LedgerLine
import com.kisisel.finans.domain.model.PersonSummary
import com.kisisel.finans.domain.model.TransactionType
import com.kisisel.finans.ui.agenda.AgendaEntryCard
import com.kisisel.finans.ui.agenda.agendaEntryKey
import com.kisisel.finans.ui.common.NotificationsBanner
import com.kisisel.finans.ui.common.dialogTitle
import com.kisisel.finans.ui.common.label
import com.kisisel.finans.ui.common.rememberNotificationsEnabled
import com.kisisel.finans.ui.common.tr
import com.kisisel.finans.ui.navigation.MenuNavigationIcon
import com.kisisel.finans.ui.persons.EntryDialog

private sealed interface HomeDialog {
    data object PickPaymentKind : HomeDialog
    data class PickPerson(val type: TransactionType) : HomeDialog
    data class Entry(val type: TransactionType, val personId: Long, val personName: String) : HomeDialog
}

/**
 * Ana Sayfa: yalnızca günlük karar için gerekenler.
 *  1) Net durum özeti  2) Dört hızlı işlem  3) Dikkat gerektirenler (geciken + 7 gün)  4) Son hareketler.
 * Fatura ekleme, ajanda notu, şifre kasası vb. yan menüde ve kendi bölümlerindedir.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenOccurrence: (Long) -> Unit,
    onOpenPerson: (Long) -> Unit,
    onAddPerson: () -> Unit,
    onSeeTransactions: () -> Unit,
    onSeeAgenda: () -> Unit,
    onOpenAgendaNote: (Long) -> Unit,
    vm: HomeViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<HomeDialog?>(null) }
    val notificationsEnabled = rememberNotificationsEnabled()

    Scaffold(
        contentWindowInsets = WindowInsets.statusBars,
        topBar = { TopAppBar(title = { Text("Kişisel Finans Takip") }, navigationIcon = { MenuNavigationIcon() }) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!notificationsEnabled) item { NotificationsBanner() }

            if (state.loaded && state.persons.isEmpty()) item { WelcomeCard(onAddPerson) }

            state.totals?.let { totals -> item { SummaryCard(totals) } }

            item {
                QuickActions(
                    onLend = { dialog = HomeDialog.PickPerson(TransactionType.LEND) },
                    onBorrow = { dialog = HomeDialog.PickPerson(TransactionType.BORROW) },
                    onCollect = { dialog = HomeDialog.PickPerson(TransactionType.COLLECTION) },
                    onPay = { dialog = HomeDialog.PickPaymentKind }
                )
            }

            item {
                SectionHeader(
                    title = if (state.attentionOverdue > 0) "Dikkat gerektirenler (${state.attentionOverdue} geciken)" else "Yaklaşanlar (7 gün)",
                    action = "Ajanda",
                    onAction = onSeeAgenda,
                    isAlert = state.attentionOverdue > 0
                )
            }
            if (state.loaded && state.attention.isEmpty()) {
                item { Text("Gecikmiş veya önümüzdeki 7 günde vadesi gelen bir şey yok.", style = MaterialTheme.typography.bodyMedium) }
            }
            items(state.attention, key = { "a" + agendaEntryKey(it) }) { entry ->
                AgendaEntryCard(
                    entry = entry,
                    today = state.today,
                    onClick = {
                        when (entry) {
                            is AgendaEntry.Invoice -> onOpenOccurrence(entry.occurrence.id)
                            is AgendaEntry.Claim -> onOpenPerson(entry.claim.personId)
                            is AgendaEntry.Note -> onOpenAgendaNote(entry.item.id)
                        }
                    }
                )
            }

            item { SectionHeader("Son hareketler", "Tümü", onSeeTransactions) }
            if (state.loaded && state.recent.isEmpty()) {
                item { Text("Henüz işlem yok.", style = MaterialTheme.typography.bodyMedium) }
            }
            items(state.recent, key = { "r${it.entry.id}" }) { line ->
                LineMini(line) { onOpenPerson(line.entry.personId) }
            }
        }
    }

    when (val d = dialog) {
        HomeDialog.PickPaymentKind -> AlertDialog(
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

/** İlk kullanım: henüz kişi yokken yol gösterir. */
@Composable
private fun WelcomeCard(onAddPerson: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Hoş geldiniz", style = MaterialTheme.typography.titleMedium)
            Text(
                "Alacak, borç ve faturalarınızı takip etmek için önce bir kişi ekleyin.",
                style = MaterialTheme.typography.bodyMedium
            )
            Button(onClick = onAddPerson) { Text("İlk kişiyi ekle") }
        }
    }
}

/** Tek bakışta durum: net bakiye, toplam alacak/borç ve bu ayın hareketi. */
@Composable
private fun SummaryCard(t: DashboardTotals) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column {
                Text("Net durum (alacak − borç)", style = MaterialTheme.typography.labelLarge)
                Text(
                    Money.format(t.net),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (t.net >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }
            Row(Modifier.fillMaxWidth()) {
                Balance("Toplam alacak", t.remainingReceivable, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                Balance("Toplam borç", t.remainingPayable, MaterialTheme.colorScheme.error, Modifier.weight(1f))
            }
            HorizontalDivider()
            Text(
                "Bu ay: ${Money.format(t.monthCollected)} tahsilat • ${Money.format(t.monthPaid)} ödeme",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun Balance(label: String, kurus: Long, color: Color, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(Money.format(kurus), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = color)
    }
}

/** En sık dört işlem; diğer eylemler (fatura ekleme, not, kasa) yan menüde ve kendi bölümlerinde. */
@Composable
private fun QuickActions(onLend: () -> Unit, onBorrow: () -> Unit, onCollect: () -> Unit, onPay: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        QuickAction(Icons.Filled.ArrowUpward, "Alacak ekle", onLend, Modifier.weight(1f))
        QuickAction(Icons.Filled.ArrowDownward, "Borç ekle", onBorrow, Modifier.weight(1f))
        QuickAction(Icons.Filled.Payments, "Tahsilat", onCollect, Modifier.weight(1f))
        QuickAction(Icons.Filled.CreditCard, "Ödeme", onPay, Modifier.weight(1f))
    }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        FilledTonalIconButton(onClick = onClick, modifier = Modifier.size(56.dp)) {
            Icon(icon, contentDescription = label)
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 1)
    }
}

@Composable
private fun SectionHeader(title: String, action: String, onAction: () -> Unit, isAlert: Boolean = false) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = if (isAlert) MaterialTheme.colorScheme.error else Color.Unspecified,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onAction) { Text(action) }
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
                textDecoration = if (e.isCancelled) TextDecoration.LineThrough else null
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
