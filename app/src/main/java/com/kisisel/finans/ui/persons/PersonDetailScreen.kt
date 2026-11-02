package com.kisisel.finans.ui.persons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kisisel.finans.core.Money
import com.kisisel.finans.domain.model.LedgerEntry
import com.kisisel.finans.domain.model.PersonBalance
import com.kisisel.finans.domain.model.TransactionType
import com.kisisel.finans.ui.common.ConfirmDialog
import com.kisisel.finans.ui.common.dialogTitle
import com.kisisel.finans.ui.common.label
import com.kisisel.finans.ui.common.tr
import java.util.UUID

private sealed interface DetailDialog {
    data class NewEntry(val type: TransactionType) : DetailDialog
    data class Close(val type: TransactionType, val amountKurus: Long, val key: String) : DetailDialog
    data class Cancel(val entryId: Long) : DetailDialog
    data object ToggleArchive : DetailDialog
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    vm: PersonDetailViewModel = hiltViewModel()
) {
    val summary by vm.summary.collectAsStateWithLifecycle()
    val entries by vm.entries.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var dialog by remember { mutableStateOf<DetailDialog?>(null) }
    var menuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    val s = summary

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(s?.person?.fullName ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
                actions = {
                    if (s != null) {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Menü")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Düzenle") },
                                onClick = { menuOpen = false; onEdit(s.person.id) }
                            )
                            DropdownMenuItem(
                                text = { Text(if (s.person.isArchived) "Arşivden çıkar" else "Arşivle") },
                                onClick = { menuOpen = false; dialog = DetailDialog.ToggleArchive }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (s == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val b = s.balance
            val archived = s.person.isArchived
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { BalanceCard(b) }

                if (archived) {
                    item {
                        Text(
                            "Bu kişi arşivlenmiş. Yeni işlem eklemek için arşivden çıkarın.",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                } else {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ActionRow(
                                TransactionType.LEND, TransactionType.BORROW,
                                onClick = { dialog = DetailDialog.NewEntry(it) }
                            )
                            ActionRow(
                                TransactionType.PAID_ON_BEHALF, null,
                                onClick = { dialog = DetailDialog.NewEntry(it) }
                            )
                            ActionRow(
                                TransactionType.COLLECTION, TransactionType.DEBT_PAYMENT,
                                onClick = { dialog = DetailDialog.NewEntry(it) }
                            )
                            if (b.remainingReceivable > 0) {
                                OutlinedButton(
                                    onClick = {
                                        dialog = DetailDialog.Close(
                                            TransactionType.COLLECTION, b.remainingReceivable, UUID.randomUUID().toString()
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Alacağı kapat (${Money.format(b.remainingReceivable)})") }
                            }
                            if (b.remainingPayable > 0) {
                                OutlinedButton(
                                    onClick = {
                                        dialog = DetailDialog.Close(
                                            TransactionType.DEBT_PAYMENT, b.remainingPayable, UUID.randomUUID().toString()
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Borcu kapat (${Money.format(b.remainingPayable)})") }
                            }
                        }
                    }
                }

                item { Text("İşlem geçmişi", style = MaterialTheme.typography.titleMedium) }
                if (entries.isEmpty()) {
                    item { Text("Henüz işlem yok.") }
                }
                items(entries, key = { it.id }) { e ->
                    EntryCard(e, onCancel = { dialog = DetailDialog.Cancel(e.id) })
                }
            }
        }
    }

    when (val d = dialog) {
        is DetailDialog.NewEntry -> {
            val type = d.type
            val b = s?.balance
            val hint = when (type) {
                TransactionType.COLLECTION -> b?.let { "Kalan alacak: ${Money.format(it.remainingReceivable)}" }
                TransactionType.DEBT_PAYMENT -> b?.let { "Kalan borç: ${Money.format(it.remainingPayable)}" }
                else -> null
            }
            EntryDialog(
                title = type.dialogTitle(),
                showDueDate = !type.isSettlement,
                initialDay = vm.today().toEpochDay(),
                hint = hint,
                onDismiss = { dialog = null },
                onSubmit = { amount, date, due, desc, key, onResult ->
                    vm.submitEntry(type, amount, date, due, desc, key, onResult)
                }
            )
        }

        is DetailDialog.Close -> ConfirmDialog(
            title = if (d.type == TransactionType.COLLECTION) "Alacağı kapat" else "Borcu kapat",
            message = "Kalan ${Money.format(d.amountKurus)} tek işlemde " +
                (if (d.type == TransactionType.COLLECTION) "tahsil edilmiş" else "ödenmiş") +
                " olarak kaydedilecek.",
            confirmText = "Kapat",
            onConfirm = { vm.close(d.type, d.key); dialog = null },
            onDismiss = { dialog = null }
        )

        is DetailDialog.Cancel -> ConfirmDialog(
            title = "İşlemi iptal et",
            message = "İşlem silinmez, iptal edildi olarak işaretlenir ve hesaba katılmaz.",
            confirmText = "İptal et",
            onConfirm = { vm.cancel(d.entryId); dialog = null },
            onDismiss = { dialog = null }
        )

        DetailDialog.ToggleArchive -> {
            val archived = s?.person?.isArchived == true
            ConfirmDialog(
                title = if (archived) "Arşivden çıkar" else "Kişiyi arşivle",
                message = if (archived) {
                    "Kişi tekrar listede görünecek."
                } else {
                    "Kişi listeden kalkar; tüm finansal kayıtlar korunur."
                },
                confirmText = if (archived) "Arşivden çıkar" else "Arşivle",
                onConfirm = { vm.setArchived(!archived); dialog = null },
                onDismiss = { dialog = null }
            )
        }

        null -> Unit
    }
}

@Composable
private fun BalanceCard(b: PersonBalance) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Kalan alacak", style = MaterialTheme.typography.labelLarge)
            Text(
                Money.format(b.remainingReceivable),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text("Kalan borç", style = MaterialTheme.typography.labelLarge)
            Text(
                Money.format(b.remainingPayable),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            StatRow("Toplam alacak", b.totalReceivable)
            StatRow("Tahsil edilen", b.collected)
            StatRow("Toplam borç", b.totalPayable)
            StatRow("Ödenen", b.paid)
        }
    }
}

@Composable
private fun StatRow(label: String, kurus: Long) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(Money.format(kurus), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ActionRow(first: TransactionType, second: TransactionType?, onClick: (TransactionType) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(onClick = { onClick(first) }, modifier = Modifier.weight(1f)) {
            Text(first.dialogTitle())
        }
        if (second != null) {
            FilledTonalButton(onClick = { onClick(second) }, modifier = Modifier.weight(1f)) {
                Text(second.dialogTitle())
            }
        }
    }
}

@Composable
private fun EntryCard(e: LedgerEntry, onCancel: () -> Unit) {
    val decoration = if (e.isCancelled) TextDecoration.LineThrough else TextDecoration.None
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(e.type.label(), style = MaterialTheme.typography.titleSmall, textDecoration = decoration)
                Text(
                    buildString {
                        append(e.date.tr())
                        e.dueDate?.let { append("  •  Vade: ${it.tr()}") }
                    },
                    style = MaterialTheme.typography.bodySmall
                )
                if (e.description.isNotBlank()) Text(e.description, style = MaterialTheme.typography.bodySmall)
                if (e.isCancelled) {
                    Text("İptal edildi", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                }
            }
            Text(
                Money.format(e.amountKurus),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textDecoration = decoration
            )
            if (!e.isCancelled && e.occurrenceId == null) {
                IconButton(onClick = onCancel) { Icon(Icons.Filled.Cancel, contentDescription = "İptal et") }
            } else {
                Box(Modifier.padding(end = 12.dp))
            }
        }
    }
}
