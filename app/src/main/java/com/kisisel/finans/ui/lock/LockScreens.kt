package com.kisisel.finans.ui.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kisisel.finans.security.LockState
import com.kisisel.finans.ui.common.PasswordField
import kotlinx.coroutines.delay
import kotlin.math.ceil

/** Kilit durumuna göre kurulum, kilit ekranı veya uygulama içeriğini gösterir. */
@Composable
fun LockGate(
    vm: LockViewModel = hiltViewModel(),
    content: @Composable () -> Unit
) {
    val state by vm.lockState.collectAsStateWithLifecycle()
    when (state) {
        LockState.LOADING -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        LockState.NEEDS_SETUP -> SetupScreen(vm)
        LockState.LOCKED -> LockScreen(vm)
        LockState.UNLOCKED -> content()
    }
}

@Composable
private fun SetupScreen(vm: LockViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().systemBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Kişisel Finans Takip", style = MaterialTheme.typography.headlineMedium)
        Text("Verilerinizi korumak için bir uygulama parolası belirleyin.", style = MaterialTheme.typography.bodyLarge)
        PasswordField(password, { password = it }, "Parola (en az 8 karakter)")
        PasswordField(
            confirm, { confirm = it }, "Parolayı tekrar girin",
            imeAction = ImeAction.Done, onImeAction = { vm.setup(password, confirm) }
        )
        ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Text(
            "Önemli: Parolanızı unutursanız kurtarılamaz. Veriler cihazda şifreli tutulur; kurtarma yolu, " +
                "önceden aldığınız şifreli yedekten geri yükleme veya verileri silip baştan başlamaktır.",
            style = MaterialTheme.typography.bodySmall
        )
        Button(
            onClick = { vm.setup(password, confirm) },
            enabled = !ui.busy,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Parolayı belirle") }
    }
}

@Composable
private fun LockScreen(vm: LockViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }
    val biometricAvailable = remember { BiometricHelper.isAvailable(context) }

    var usePin by rememberSaveable { mutableStateOf(true) }
    var input by rememberSaveable { mutableStateOf("") }
    var autoPrompted by rememberSaveable { mutableStateOf(false) }
    var showForgot by remember { mutableStateOf(false) }
    var showReset by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val until = ui.lockoutUntil
    LaunchedEffect(until) {
        now = System.currentTimeMillis()
        while (until > now) {
            delay(500)
            now = System.currentTimeMillis()
        }
    }
    val remainingSeconds = if (until > now) ceil((until - now) / 1000.0).toLong() else 0L

    val pinMode = ui.pinSet && usePin
    val canUseBiometric = ui.biometricEnabled && biometricAvailable && activity != null

    val submit = { if (pinMode) vm.submitPin(input) else vm.submitPassword(input) }

    LaunchedEffect(canUseBiometric) {
        if (canUseBiometric && !autoPrompted) {
            autoPrompted = true
            activity?.let { BiometricHelper.prompt(it, onSuccess = vm::onBiometricSuccess) }
        }
    }
    LaunchedEffect(ui.error) { if (ui.error != null) input = "" }

    Column(
        Modifier.fillMaxSize().systemBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Kişisel Finans Takip", style = MaterialTheme.typography.headlineMedium)
        Text(
            if (pinMode) "PIN kodunuzu girin" else "Parolanızı girin",
            style = MaterialTheme.typography.bodyLarge
        )
        PasswordField(
            value = input,
            onValueChange = { input = it },
            label = if (pinMode) "PIN" else "Parola",
            keyboardType = if (pinMode) KeyboardType.NumberPassword else KeyboardType.Password,
            imeAction = ImeAction.Done,
            onImeAction = { submit() },
            isError = ui.error != null
        )

        if (remainingSeconds > 0) {
            Text(
                "Çok fazla hatalı deneme. $remainingSeconds sn sonra tekrar deneyin.",
                color = MaterialTheme.colorScheme.error
            )
        } else {
            ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }

        Button(
            onClick = { submit() },
            enabled = !ui.busy && remainingSeconds == 0L && input.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Kilidi aç") }

        if (ui.pinSet) {
            TextButton(onClick = { usePin = !usePin; input = "" }) {
                Text(if (pinMode) "Parolayla gir" else "PIN ile gir")
            }
        }
        if (canUseBiometric) {
            TextButton(onClick = {
                activity?.let { BiometricHelper.prompt(it, onSuccess = vm::onBiometricSuccess) }
            }) { Text("Parmak izi / yüz ile aç") }
        }
        TextButton(onClick = { showForgot = true }) { Text("Parolamı unuttum") }
    }

    if (showForgot) {
        AlertDialog(
            onDismissRequest = { showForgot = false },
            title = { Text("Parolamı unuttum") },
            text = {
                Text(
                    "Parola kurtarılamaz; veriler cihazda şifrelidir. İki yol var:\n\n" +
                        "1) Daha önce aldığınız şifreli yedekten geri yükleme (yedekleme Aşama 9'da eklenecek).\n" +
                        "2) Tüm verileri silip baştan başlama."
                )
            },
            confirmButton = {
                TextButton(onClick = { showForgot = false; showReset = true }) {
                    Text("Tüm verileri sil", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showForgot = false }) { Text("Kapat") } }
        )
    }
    if (showReset) {
        ResetConfirmDialog(onConfirm = { showReset = false; vm.resetAll() }, onDismiss = { showReset = false })
    }
}
