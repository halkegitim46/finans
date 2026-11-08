package com.kisisel.finans.ui.startup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kisisel.finans.data.local.DbHealth
import com.kisisel.finans.ui.lock.ResetConfirmDialog

/** Veritabanı hazır olana kadar bekletir; hazır değilse çökmek yerine kurtarma ekranını gösterir. */
@Composable
fun StartupGate(
    vm: StartupViewModel = hiltViewModel(),
    content: @Composable () -> Unit
) {
    val state by vm.state.collectAsStateWithLifecycle()
    when (val s = state) {
        StartupState.Checking -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator()
                Text("Veritabanı hazırlanıyor…", style = MaterialTheme.typography.bodyMedium)
            }
        }
        StartupState.Ready -> content()
        is StartupState.Failed -> DatabaseRecoveryScreen(s.problem, onRetry = vm::retry, onReset = vm::resetAll)
    }
}

@Composable
private fun DatabaseRecoveryScreen(problem: DbHealth.Problem, onRetry: () -> Unit, onReset: () -> Unit) {
    var confirmReset by remember { mutableStateOf(false) }
    val explanation = when (problem.kind) {
        DbHealth.Kind.KEY_MISSING, DbHealth.Kind.KEY_UNREADABLE ->
            "Verilerinizi açan şifreleme anahtarı kullanılamıyor. Bu, cihazın güvenlik ayarlarının değişmesi " +
                "veya anahtar dosyasının silinmesi gibi durumlarda olabilir. Şifreli veri bu anahtar olmadan açılamaz."
        DbHealth.Kind.KEY_WRITE_FAILED ->
            "Şifreleme anahtarı kaydedilemedi. Depolama alanınızı kontrol edip tekrar deneyin."
        DbHealth.Kind.OPEN_FAILED ->
            "Veritabanı açılamadı. Geçici bir sorun olabilir; önce tekrar deneyin."
    }
    val canReset = problem.kind != DbHealth.Kind.KEY_WRITE_FAILED

    Column(
        Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Veritabanı açılamadı", style = MaterialTheme.typography.headlineSmall)
        Text(explanation, style = MaterialTheme.typography.bodyLarge)
        Text(problem.detail, style = MaterialTheme.typography.bodySmall)
        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("Tekrar dene") }
        if (canReset) {
            Text(
                "Önceden aldığınız şifreli bir yedeğiniz varsa verileri silip baştan başlayabilir, sonra " +
                    "Ayarlar → Yedekleme → Yedekten geri yükle ile verilerinizi geri getirebilirsiniz. " +
                    "Hiçbir veri onayınız olmadan silinmez.",
                style = MaterialTheme.typography.bodyMedium
            )
            OutlinedButton(onClick = { confirmReset = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Verileri silip baştan başla", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (confirmReset) {
        ResetConfirmDialog(onConfirm = { confirmReset = false; onReset() }, onDismiss = { confirmReset = false })
    }
}
