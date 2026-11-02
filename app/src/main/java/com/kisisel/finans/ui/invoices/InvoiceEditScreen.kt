package com.kisisel.finans.ui.invoices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kisisel.finans.domain.model.InvoiceType
import com.kisisel.finans.domain.model.Recurrence
import com.kisisel.finans.ui.common.AmountField
import com.kisisel.finans.ui.common.ConfirmDialog
import com.kisisel.finans.ui.common.DateField
import com.kisisel.finans.ui.common.DropdownField
import com.kisisel.finans.ui.common.label
import com.kisisel.finans.ui.common.tr
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceEditScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    vm: InvoiceEditViewModel = hiltViewModel()
) {
    val s by vm.state.collectAsStateWithLifecycle()
    var confirmArchive by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (s.isEdit) "Faturayı düzenle" else "Fatura ekle") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = s.name,
                onValueChange = vm::onName,
                label = { Text("Fatura adı (ör. Dayım elektrik)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            DropdownField(
                label = "Fatura türü",
                options = InvoiceType.entries.map { it to it.label() },
                selected = s.type,
                onSelect = vm::onType
            )
            DropdownField(
                label = "İlgili kişi",
                options = listOf<Pair<Long?, String>>(null to "Kendim (alacak oluşmaz)") +
                    s.persons.map { it.id to it.fullName },
                selected = s.personId,
                onSelect = vm::onPerson
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = s.isFixed, onClick = { vm.onFixed(true) }, label = { Text("Sabit tutar") })
                FilterChip(selected = !s.isFixed, onClick = { vm.onFixed(false) }, label = { Text("Değişken tutar") })
            }
            AmountField(
                value = s.amountText,
                onValueChange = vm::onAmount,
                label = if (s.isFixed) "Tutar" else "Tahmini tutar (isteğe bağlı)",
                supportingText = if (s.isFixed) null else "Her dönem fatura gelince tutarı girersiniz."
            )

            if (s.isEdit) {
                Text(
                    "Tekrar: ${s.recurrence.label()}  •  İlk vade: ${LocalDate.ofEpochDay(s.firstDueDay).tr()}\n" +
                        "Tekrar ve ilk vade sonradan değiştirilemez. Değişiklikler yalnızca sonraki dönemleri etkiler.",
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                DropdownField(
                    label = "Tekrar",
                    options = Recurrence.entries.map { it to it.label() },
                    selected = s.recurrence,
                    onSelect = vm::onRecurrence
                )
                if (s.recurrence == Recurrence.CUSTOM_DAYS) {
                    OutlinedTextField(
                        value = s.customDaysText,
                        onValueChange = vm::onCustomDays,
                        label = { Text("Kaç günde bir") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                DateField(label = "İlk son ödeme tarihi", epochDay = s.firstDueDay, onChange = vm::onFirstDue)
            }

            OutlinedTextField(
                value = s.note,
                onValueChange = vm::onNote,
                label = { Text("Açıklama") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()) {
                Text("Hatırlatmalar", style = MaterialTheme.typography.titleSmall)
                Switch(checked = s.remindersEnabled, onCheckedChange = vm::onRemindersEnabled)
            }
            if (s.remindersEnabled) {
                listOf(7 to "7 gün önce", 3 to "3 gün önce", 1 to "1 gün önce", 0 to "Vade günü").forEach { (day, text) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = day in s.reminderDays, onCheckedChange = { vm.onToggleReminderDay(day, it) })
                        Text(text)
                    }
                }
                DropdownField(
                    label = "Geciken fatura hatırlatması",
                    options = listOf(0 to "Kapalı", 1 to "Her gün", 2 to "2 günde bir", 3 to "3 günde bir", 7 to "Haftada bir"),
                    selected = s.overdueEvery,
                    onSelect = vm::onOverdueEvery
                )
            }

            s.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Button(
                onClick = { vm.save(onDone) },
                enabled = !s.saving && !s.loading,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Kaydet") }

            if (s.isEdit) {
                OutlinedButton(onClick = { confirmArchive = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Faturayı arşivle")
                }
            }
        }
    }

    if (confirmArchive) {
        ConfirmDialog(
            title = "Faturayı arşivle",
            message = "Yeni dönem üretilmez. Geçmiş dönemler ve ödemeler korunur.",
            confirmText = "Arşivle",
            onConfirm = { confirmArchive = false; vm.archive(onDone) },
            onDismiss = { confirmArchive = false }
        )
    }
}
