package com.kisisel.finans.ui.agenda

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kisisel.finans.ui.common.ConfirmDialog
import com.kisisel.finans.ui.common.DateField
import com.kisisel.finans.ui.common.DropdownField

private fun timeText(minutes: Int) = "%02d:%02d".format(minutes / 60, minutes % 60)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgendaEditScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    vm: AgendaEditViewModel = hiltViewModel()
) {
    val s by vm.state.collectAsStateWithLifecycle()
    var showTime by remember { mutableStateOf(false) }
    var confirmArchive by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (s.isEdit) "Notu düzenle" else "Ajanda notu ekle") },
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
                value = s.title,
                onValueChange = vm::onTitle,
                label = { Text("Başlık (ör. Ahmet'i ara)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = s.note,
                onValueChange = vm::onNote,
                label = { Text("Not (isteğe bağlı)") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
            DateField(label = "Tarih", epochDay = s.dateEpoch, onChange = vm::onDate)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                OutlinedButton(onClick = { showTime = true }) {
                    Text(s.timeMinutes?.let { "Saat: ${timeText(it)}" } ?: "Saat ekle (isteğe bağlı)")
                }
                if (s.timeMinutes != null) {
                    TextButton(onClick = { vm.onTime(null) }) { Text("Saati kaldır") }
                }
            }

            DropdownField(
                label = "İlgili kişi (isteğe bağlı)",
                options = listOf<Pair<Long?, String>>(null to "Kişi yok") + s.persons.map { it.id to it.fullName },
                selected = s.personId,
                onSelect = vm::onPerson
            )
            DropdownField(
                label = "Hatırlatma",
                options = listOf<Pair<Int?, String>>(
                    null to "Hatırlatma yok", 0 to "Zamanında", 60 to "1 saat önce", 1440 to "1 gün önce"
                ),
                selected = s.remind,
                onSelect = vm::onRemind
            )
            if (s.remind != null && s.timeMinutes == null) {
                Text("Saat seçilmezse hatırlatma o günün 09:00'unda yapılır.", style = MaterialTheme.typography.bodySmall)
            }

            s.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Button(
                onClick = { vm.save(onDone) },
                enabled = !s.saving && !s.loading,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Kaydet") }

            if (s.isEdit) {
                OutlinedButton(onClick = { vm.toggleDone(onDone) }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (s.isDone) "Tamamlanmayı geri al" else "Tamamlandı olarak işaretle")
                }
                OutlinedButton(onClick = { confirmArchive = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Notu sil", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (showTime) {
        TimeDialog(
            initialMinutes = s.timeMinutes,
            onConfirm = { vm.onTime(it); showTime = false },
            onDismiss = { showTime = false }
        )
    }
    if (confirmArchive) {
        ConfirmDialog(
            title = "Notu sil",
            message = "Not listelerden kalkar ve hatırlatması iptal edilir. (Kayıt arşivde saklanır.)",
            confirmText = "Sil",
            onConfirm = { confirmArchive = false; vm.archiveItem(onDone) },
            onDismiss = { confirmArchive = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(initialMinutes: Int?, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    val start = initialMinutes ?: (9 * 60)
    val state = rememberTimePickerState(initialHour = start / 60, initialMinute = start % 60, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Saat seçin") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) { TimePicker(state = state) }
        },
        confirmButton = { TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text("Tamam") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } }
    )
}
