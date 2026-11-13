package com.kisisel.finans.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kisisel.finans.ui.common.ConfirmDialog
import com.kisisel.finans.ui.common.DropdownField
import com.kisisel.finans.ui.common.PasswordField
import com.kisisel.finans.ui.common.openNotificationSettings
import com.kisisel.finans.ui.lock.BiometricHelper
import com.kisisel.finans.ui.lock.ResetConfirmDialog
import com.kisisel.finans.ui.lock.findFragmentActivity

private enum class SettingsDialog { PASSWORD, PIN_SET, PIN_REMOVE, RESET, DELETE_LEGACY }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel = hiltViewModel()) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val legacyExists by vm.legacyExists.collectAsStateWithLifecycle()
    val claimReminders by vm.claimReminders.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }
    val biometricAvailable = remember { BiometricHelper.isAvailable(context) }
    val snackbar = remember { SnackbarHostState() }
    var dialog by remember { mutableStateOf<SettingsDialog?>(null) }

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        contentWindowInsets = WindowInsets.statusBars,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { TopAppBar(title = { Text("Ayarlar") }) }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Section("Güvenlik") {
                    SettingRow("Parolayı değiştir", onClick = { dialog = SettingsDialog.PASSWORD })
                    HorizontalDivider()
                    SettingRow(
                        title = "PIN ile giriş",
                        subtitle = if (s.pinRecord != null) "Açık (dokunarak kaldırın)" else "Kapalı (dokunarak ayarlayın)",
                        onClick = {
                            dialog = if (s.pinRecord != null) SettingsDialog.PIN_REMOVE else SettingsDialog.PIN_SET
                        }
                    )
                    HorizontalDivider()
                    SettingRow(
                        title = "Parmak izi / yüz ile giriş",
                        subtitle = if (biometricAvailable) null else "Cihazda kayıtlı güçlü biyometri yok",
                        trailing = {
                            Switch(
                                checked = s.biometricEnabled,
                                enabled = biometricAvailable && activity != null,
                                onCheckedChange = { on ->
                                    if (on) {
                                        activity?.let {
                                            BiometricHelper.prompt(
                                                it,
                                                onSuccess = { vm.setBiometric(true) },
                                                onError = { msg -> vm.showMessage(msg) }
                                            )
                                        }
                                    } else {
                                        vm.setBiometric(false)
                                    }
                                }
                            )
                        }
                    )
                    HorizontalDivider()
                    SettingRow(
                        title = "Arka plana geçince kilitle",
                        trailing = { Switch(checked = s.lockOnBackground, onCheckedChange = vm::setLockOnBackground) }
                    )
                    if (s.lockOnBackground) {
                        DropdownField(
                            label = "Kilitlenme süresi",
                            options = listOf(0 to "Hemen", 30 to "30 saniye sonra", 60 to "1 dakika sonra", 300 to "5 dakika sonra"),
                            selected = s.lockTimeoutSeconds,
                            onSelect = vm::setTimeout,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    HorizontalDivider()
                    SettingRow(
                        title = "Ekran görüntüsünü engelle",
                        subtitle = "Son uygulamalar önizlemesinde tutarlar görünmez",
                        trailing = { Switch(checked = s.secureScreen, onCheckedChange = vm::setSecureScreen) }
                    )
                    OutlinedButton(
                        onClick = vm::lockNow,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) { Text("Şimdi kilitle") }
                }
            }

            item {
                Section("Bildirimler ve pil") {
                    SettingRow("Bildirim ayarları", onClick = { openNotificationSettings(context) })
                    HorizontalDivider()
                    SettingRow(
                        title = "Borç/alacak vade hatırlatmaları",
                        subtitle = "Vadeden 3 ve 1 gün önce, vade günü ve gecikince 3 günde bir",
                        trailing = { Switch(checked = claimReminders, onCheckedChange = vm::setClaimReminders) }
                    )
                    HorizontalDivider()
                    SettingRow(
                        title = "Pil optimizasyonu ayarları",
                        subtitle = "Hatırlatmalar gecikirse listeden uygulamayı \"Kısıtlama yok\" yapın",
                        onClick = {
                            try {
                                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                            } catch (e: ActivityNotFoundException) {
                                vm.showMessage("Bu cihazda pil ayarları açılamadı.")
                            }
                        }
                    )
                }
            }

            item { BackupSection(onMessage = vm::showMessage) }

            item {
                Section("Veri") {
                    Text(
                        "Veritabanı AES-256 ile şifrelenir (SQLCipher). Şifreleme anahtarı Android Keystore ile korunur. " +
                            "Hiçbir veri sunucuya gönderilmez.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (legacyExists) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            modifier = Modifier.padding(top = 12.dp)
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text("Eski şifresiz veri dosyası bulundu", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Şifreli veritabanına taşınamadı ya da tamamlanmadı. Verileriniz bu dosyada duruyor. " +
                                        "Gerekmiyorsa silebilirsiniz.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                TextButton(onClick = { dialog = SettingsDialog.DELETE_LEGACY }) { Text("Dosyayı sil") }
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = { dialog = SettingsDialog.RESET },
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                    ) { Text("Tüm verileri sil", color = MaterialTheme.colorScheme.error) }
                }
            }

            item {
                Text("Sürüm ${vm.versionName}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    when (dialog) {
        SettingsDialog.PASSWORD -> ChangePasswordDialog(
            onDismiss = { dialog = null },
            onSubmit = { old, new, cb -> vm.changePassword(old, new, cb) }
        )

        SettingsDialog.PIN_SET -> SetPinDialog(
            onDismiss = { dialog = null },
            onSubmit = { pin, password, cb -> vm.setPin(pin, password, cb) }
        )

        SettingsDialog.PIN_REMOVE -> ConfirmDialog(
            title = "PIN'i kaldır",
            message = "PIN ile giriş kapatılır. Parola ile girmeye devam edersiniz.",
            confirmText = "Kaldır",
            onConfirm = { vm.removePin(); dialog = null },
            onDismiss = { dialog = null }
        )

        SettingsDialog.RESET -> ResetConfirmDialog(
            onConfirm = { dialog = null; vm.resetAll() },
            onDismiss = { dialog = null }
        )

        SettingsDialog.DELETE_LEGACY -> ConfirmDialog(
            title = "Eski dosyayı sil",
            message = "Şifresiz eski veri dosyası kalıcı olarak silinir.",
            confirmText = "Sil",
            onConfirm = { vm.deleteLegacy(); dialog = null },
            onDismiss = { dialog = null }
        )

        null -> Unit
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) { content() }
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
        trailing?.invoke()
    }
}

@Composable
private fun ChangePasswordDialog(
    onDismiss: () -> Unit,
    onSubmit: (old: String, new: String, onResult: (String?) -> Unit) -> Unit
) {
    var old by remember { mutableStateOf("") }
    var new1 by remember { mutableStateOf("") }
    var new2 by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Parolayı değiştir") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PasswordField(old, { old = it; error = null }, "Mevcut parola")
                PasswordField(new1, { new1 = it; error = null }, "Yeni parola (en az 8 karakter)")
                PasswordField(new2, { new2 = it; error = null }, "Yeni parola (tekrar)", imeAction = ImeAction.Done)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(enabled = !busy, onClick = {
                if (new1 != new2) {
                    error = "Yeni parolalar eşleşmiyor."
                } else {
                    busy = true
                    onSubmit(old, new1) { err -> busy = false; if (err == null) onDismiss() else error = err }
                }
            }) { Text("Değiştir") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Vazgeç") } }
    )
}

@Composable
private fun SetPinDialog(
    onDismiss: () -> Unit,
    onSubmit: (pin: String, password: String, onResult: (String?) -> Unit) -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("PIN ayarla") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PasswordField(
                    pin, { pin = it.filter(Char::isDigit).take(8); error = null }, "PIN (4-8 rakam)",
                    keyboardType = KeyboardType.NumberPassword
                )
                PasswordField(password, { password = it; error = null }, "Mevcut parolanız", imeAction = ImeAction.Done)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(enabled = !busy, onClick = {
                busy = true
                onSubmit(pin, password) { err -> busy = false; if (err == null) onDismiss() else error = err }
            }) { Text("Ayarla") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Vazgeç") } }
    )
}
