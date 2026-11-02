package com.kisisel.finans.ui.invoices

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kisisel.finans.core.Money
import com.kisisel.finans.domain.model.InvoiceOccurrence
import com.kisisel.finans.domain.model.OccurrenceStatus
import com.kisisel.finans.ui.common.AmountField
import com.kisisel.finans.ui.common.ConfirmDialog
import com.kisisel.finans.ui.common.DateField
import com.kisisel.finans.ui.common.dueStatusText
import com.kisisel.finans.ui.common.label
import com.kisisel.finans.ui.common.tr
import java.time.LocalDate

private enum class InvoiceDialog { AMOUNT, PAY, UNDO, CANCEL }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceDetailScreen(
    onBack: () -> Unit,
    onEditTemplate: (Long) -> Unit,
    vm: InvoiceDetailViewModel = hiltViewModel()
) {
    val occurrence by vm.occurrence.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var dialog by remember { mutableStateOf<InvoiceDialog?>(null) }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    val o = occurrence
    val today = vm.today()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(o?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                }
            )
        }
    ) { padding ->
        if (o == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { InfoCard(o, today) }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        when (o.status) {
                            OccurrenceStatus.UNPAID -> {
                                Button(
                                    onClick = { dialog = InvoiceDialog.PAY },
                                    enabled = o.amountKurus != null,
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Ödendi olarak işaretle") }
                                FilledTonalButton(
                                    onClick = { dialog = InvoiceDialog.AMOUNT },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text(if (o.amountKurus == null) "Tutarı gir" else "Tutarı değiştir") }
                                OutlinedButton(
                                    onClick = { dialog = InvoiceDialog.CANCEL },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Bu dönemi iptal et") }
                            }
                            OccurrenceStatus.PAID -> OutlinedButton(
                                onClick = { dialog = InvoiceDialog.UNDO },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Ödemeyi geri al") }
                            OccurrenceStatus.CANCELLED -> Unit
                        }
                        TextButton(onClick = { onEditTemplate(o.templateId) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Fatura tanımını düzenle")
                        }
                    }
                }
                item { Text("Dönem geçmişi", style = MaterialTheme.typography.titleMedium) }
                items(history, key = { it.id }) { h -> HistoryRow(h, today, current = h.id == o.id) }
            }
        }
    }

    when (dialog) {
        InvoiceDialog.AMOUNT -> AmountDialog(
            initialKurus = o?.amountKurus,
            onDismiss = { dialog = null },
            onSave = { kurus, onResult -> vm.saveAmount(kurus, onResult) }
        )

        InvoiceDialog.PAY -> if (o != null) PayDialog(
            occurrence = o,
            initialDay = today.toEpochDay(),
            onDismiss = { dialog = null },
            onPay = { date, onResult -> vm.pay(date, onResult) }
        )

        InvoiceDialog.UNDO -> ConfirmDialog(
            title = "Ödemeyi geri al",
            message = "Dönem tekrar ödenmemiş olur ve bağlı alacak kaydı iptal edilir.",
            confirmText = "Geri al",
            onConfirm = { vm.undo(); dialog = null },
            onDismiss = { dialog = null }
        )

        InvoiceDialog.CANCEL -> ConfirmDialog(
            title = "Dönemi iptal et",
            message = "Bu dönem ödenmeyecek olarak işaretlenir. Kayıt silinmez.",
            confirmText = "İptal et",
            onConfirm = { vm.cancel(); dialog = null },
            onDismiss = { dialog = null }
        )

        null -> Unit
    }
}

@Composable
private fun InfoCard(o: InvoiceOccurrence, today: LocalDate) {
    val overdue = o.isOverdue(today)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                o.amountKurus?.let { Money.format(it) } ?: "Tutar girilmedi",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                dueStatusText(o.status, o.daysUntilDue(today)),
                color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            InfoRow("Tür", o.type.label())
            InfoRow("Son ödeme tarihi", o.dueDate.tr())
            InfoRow("Dönem", o.periodKey)
            InfoRow("Kişi", o.personName ?: "Kendim (alacak oluşmaz)")
            o.paidDate?.let { InfoRow("Ödeme tarihi", it.tr()) }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun HistoryRow(h: InvoiceOccurrence, today: LocalDate, current: Boolean) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${h.dueDate.tr()}${if (current) "  (bu dönem)" else ""}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (current) FontWeight.Bold else FontWeight.Normal
                )
                Text(dueStatusText(h.status, h.daysUntilDue(today)), style = MaterialTheme.typography.bodySmall)
            }
            Text(h.amountKurus?.let { Money.format(it) } ?: "-", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun AmountDialog(
    initialKurus: Long?,
    onDismiss: () -> Unit,
    onSave: (Long, (String?) -> Unit) -> Unit
) {
    var text by remember { mutableStateOf(initialKurus?.let { Money.format(it, withSymbol = false) } ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Fatura tutarı") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AmountField(value = text, onValueChange = { text = it; error = null }, label = "Tutar")
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(enabled = !busy, onClick = {
                val kurus = Money.parsePositive(text)
                if (kurus == null) {
                    error = "Geçerli bir tutar girin (ör. 1.500,50)."
                } else {
                    busy = true
                    onSave(kurus) { err -> busy = false; if (err == null) onDismiss() else error = err }
                }
            }) { Text("Kaydet") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Vazgeç") } }
    )
}

@Composable
private fun PayDialog(
    occurrence: InvoiceOccurrence,
    initialDay: Long,
    onDismiss: () -> Unit,
    onPay: (LocalDate, (String?) -> Unit) -> Unit
) {
    var day by remember { mutableLongStateOf(initialDay) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val amount = occurrence.amountKurus?.let { Money.format(it) } ?: ""

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Faturayı öde") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (occurrence.personName != null) {
                        "$amount tutarında ödeme kaydı oluşturulacak ve ${occurrence.personName} alacağına eklenecek."
                    } else {
                        "$amount ödendi olarak işaretlenecek. Kendi faturanız olduğu için alacak kaydı oluşmaz."
                    }
                )
                DateField(label = "Ödeme tarihi", epochDay = day, onChange = { day = it })
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(enabled = !busy, onClick = {
                busy = true
                onPay(LocalDate.ofEpochDay(day)) { err -> busy = false; if (err == null) onDismiss() else error = err }
            }) { Text("Öde") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Vazgeç") } }
    )
}
