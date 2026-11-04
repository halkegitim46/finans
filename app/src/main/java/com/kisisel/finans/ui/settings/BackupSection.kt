package com.kisisel.finans.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kisisel.finans.backup.BackupInfo
import com.kisisel.finans.backup.BackupMeta
import com.kisisel.finans.ui.common.PasswordField
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DATE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")

private fun formatDateTime(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(DATE_TIME)

private fun formatSize(bytes: Long): String = String.format(Locale("tr", "TR"), "%.1f KB", bytes / 1024.0)

@Composable
fun BackupSection(
    onMessage: (String) -> Unit,
    vm: BackupViewModel = hiltViewModel()
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var showExportDialog by remember { mutableStateOf(false) }

    val createLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri -> vm.onExportUri(uri) }
    val openLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> vm.onRestoreUri(uri) }

    LaunchedEffect(Unit) { vm.messages.collect { onMessage(it) } }
    LaunchedEffect(ui.exportName) {
        val name = ui.exportName
        if (name != null) {
            vm.allowExternalUi()
            vm.exportLaunched()
            createLauncher.launch(name)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Yedekleme", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(lastBackupText(ui.info), style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Yedek, ayrı bir yedek parolasıyla AES-256 ile şifrelenir. Dosyayı istediğiniz yere (telefon, Drive vb.) " +
                        "kaydedebilirsiniz; uygulama hiçbir veriyi kendisi göndermez.",
                    style = MaterialTheme.typography.bodySmall
                )
                Button(onClick = { showExportDialog = true }, enabled = !ui.busy, modifier = Modifier.fillMaxWidth()) {
                    Text("Yedek al")
                }
                OutlinedButton(
                    onClick = { vm.allowExternalUi(); openLauncher.launch(arrayOf("*/*")) },
                    enabled = !ui.busy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Yedekten geri yükle") }
            }
        }
    }

    if (showExportDialog) {
        ExportPasswordDialog(
            onDismiss = { showExportDialog = false },
            onSubmit = { pw, confirm, cb -> vm.prepareExport(pw, confirm, cb) },
            onDone = { showExportDialog = false }
        )
    }
    if (ui.askRestorePassword) {
        RestorePasswordDialog(
            onDismiss = { vm.cancelRestore() },
            onSubmit = { pw, cb -> vm.readRestore(pw, cb) }
        )
    }
    ui.preview?.let { meta ->
        RestorePreviewDialog(meta, onConfirm = { vm.confirmRestore() }, onDismiss = { vm.cancelRestore() })
    }
}

private fun lastBackupText(info: BackupInfo?): String =
    if (info == null) {
        "Henüz yedek alınmadı."
    } else {
        "Son yedek: ${formatDateTime(info.createdAt)}\n" +
            "${info.fileName} • ${formatSize(info.sizeBytes)}\n" +
            "${info.persons} kişi • ${info.transactions} işlem • ${info.invoices} fatura dönemi"
    }

@Composable
private fun ExportPasswordDialog(
    onDismiss: () -> Unit,
    onSubmit: (String, String, (String?) -> Unit) -> Unit,
    onDone: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Yedek parolası") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Bu parola yedeği şifreler ve uygulama parolanızdan bağımsızdır. Unutursanız yedek açılamaz; " +
                        "güvenli bir yere not edin.",
                    style = MaterialTheme.typography.bodySmall
                )
                PasswordField(password, { password = it; error = null }, "Yedek parolası (en az 8 karakter)")
                PasswordField(confirm, { confirm = it; error = null }, "Parolayı tekrar girin", imeAction = ImeAction.Done)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(enabled = !busy, onClick = {
                busy = true
                onSubmit(password, confirm) { err -> busy = false; if (err == null) onDone() else error = err }
            }) { Text(if (busy) "Hazırlanıyor…" else "Yedeği hazırla") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Vazgeç") } }
    )
}

@Composable
private fun RestorePasswordDialog(
    onDismiss: () -> Unit,
    onSubmit: (String, (String?) -> Unit) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Yedek parolası") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Yedeği alırken belirlediğiniz yedek parolasını girin.", style = MaterialTheme.typography.bodySmall)
                PasswordField(password, { password = it; error = null }, "Yedek parolası", imeAction = ImeAction.Done)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(enabled = !busy && password.isNotEmpty(), onClick = {
                busy = true
                onSubmit(password) { err -> busy = false; error = err }
            }) { Text(if (busy) "Doğrulanıyor…" else "Devam") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Vazgeç") } }
    )
}

@Composable
private fun RestorePreviewDialog(meta: BackupMeta, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Yedeği geri yükle") },
        text = {
            Text(
                "Yedek tarihi: ${formatDateTime(meta.createdAt)}\n" +
                    "${meta.persons} kişi • ${meta.transactions} işlem • ${meta.invoiceOccurrences} fatura dönemi\n\n" +
                    "Şu anki TÜM verileriniz silinip bu yedekle değiştirilecek. Bu işlem geri alınamaz."
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Geri yükle", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } }
    )
}
