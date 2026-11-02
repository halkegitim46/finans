package com.kisisel.finans.ui.persons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kisisel.finans.core.Money
import com.kisisel.finans.ui.common.AmountField
import com.kisisel.finans.ui.common.DateField
import java.time.LocalDate
import java.util.UUID

/**
 * Tutar/tarih/açıklama giriş penceresi. Pencere açılırken üretilen anahtar (key) çift
 * dokunuşta aynı işlemin iki kez kaydedilmesini önler.
 */
@Composable
fun EntryDialog(
    title: String,
    showDueDate: Boolean,
    initialDay: Long,
    hint: String?,
    onDismiss: () -> Unit,
    onSubmit: (
        amountKurus: Long,
        date: LocalDate,
        dueDate: LocalDate?,
        description: String,
        key: String,
        onResult: (String?) -> Unit
    ) -> Unit
) {
    var amountText by rememberSaveable { mutableStateOf("") }
    var dayEpoch by rememberSaveable { mutableLongStateOf(initialDay) }
    var dueEpoch by rememberSaveable { mutableStateOf<Long?>(null) }
    var description by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val key = rememberSaveable { UUID.randomUUID().toString() }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(title) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AmountField(
                    value = amountText,
                    onValueChange = { amountText = it; error = null },
                    label = "Tutar",
                    supportingText = hint
                )
                DateField(label = "İşlem tarihi", epochDay = dayEpoch, onChange = { dayEpoch = it })
                if (showDueDate) {
                    DateField(label = "Vade tarihi (isteğe bağlı)", epochDay = dueEpoch, onChange = { dueEpoch = it })
                    if (dueEpoch != null) {
                        TextButton(onClick = { dueEpoch = null }) { Text("Vadeyi kaldır") }
                    }
                }
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Açıklama") },
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(
                enabled = !busy,
                onClick = {
                    val kurus = Money.parsePositive(amountText)
                    if (kurus == null) {
                        error = "Geçerli bir tutar girin (ör. 1.500,50)."
                    } else {
                        busy = true
                        onSubmit(
                            kurus,
                            LocalDate.ofEpochDay(dayEpoch),
                            dueEpoch?.let { LocalDate.ofEpochDay(it) },
                            description,
                            key
                        ) { err ->
                            busy = false
                            if (err == null) onDismiss() else error = err
                        }
                    }
                }
            ) { Text("Kaydet") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Vazgeç") } }
    )
}
