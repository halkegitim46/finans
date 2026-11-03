package com.kisisel.finans.ui.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Locale

/** Geri alınamaz silme için yazarak onay ("SİL"). */
@Composable
fun ResetConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val confirmed = text.trim().uppercase(Locale("tr", "TR")) in setOf("SİL", "SIL")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tüm verileri sil") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Tüm kişi, işlem ve fatura kayıtları ile ayarlar kalıcı olarak silinir. Bu işlem geri alınamaz.")
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Onaylamak için SİL yazın") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = confirmed) {
                Text("Kalıcı olarak sil", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } }
    )
}
