package com.kisisel.finans.ui.vault

import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.kisisel.finans.security.AuthResult
import com.kisisel.finans.security.AuthService
import com.kisisel.finans.security.SecurityStore
import com.kisisel.finans.ui.common.PasswordField
import com.kisisel.finans.ui.lock.BiometricHelper
import com.kisisel.finans.ui.lock.findFragmentActivity
import com.kisisel.finans.vault.VaultAccess
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject
import kotlin.math.ceil

data class VaultUnlockUi(
    val pinSet: Boolean = false,
    val biometricEnabled: Boolean = false,
    val error: String? = null,
    val busy: Boolean = false
)

@HiltViewModel
class VaultUnlockViewModel @Inject constructor(
    private val access: VaultAccess,
    private val auth: AuthService,
    store: SecurityStore,
    private val clock: Clock
) : ViewModel() {

    val unlockedUntil: StateFlow<Long> = access.unlockedUntil

    private val error = MutableStateFlow<String?>(null)
    private val busy = MutableStateFlow(false)

    val ui: StateFlow<VaultUnlockUi> = combine(store.settings, error, busy) { s, e, b ->
        VaultUnlockUi(pinSet = s.pinRecord != null, biometricEnabled = s.biometricEnabled, error = e, busy = b)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VaultUnlockUi())

    fun submitPassword(password: String) = submit { auth.verifyPassword(password) }
    fun submitPin(pin: String) = submit { auth.verifyPin(pin) }

    fun onBiometricSuccess() {
        viewModelScope.launch {
            auth.resetFailures()
            access.unlock()
        }
    }

    fun lock() = access.lock()
    fun touch() = access.touch()

    private fun submit(block: suspend () -> AuthResult) {
        if (busy.value) return
        busy.value = true
        error.value = null
        viewModelScope.launch {
            try {
                when (val r = block()) {
                    AuthResult.Success -> access.unlock()
                    is AuthResult.Wrong -> error.value = "Hatalı giriş. Kilitlenmeden önce kalan deneme: ${r.attemptsLeft}"
                    is AuthResult.LockedOut -> {
                        val seconds = ceil((r.untilMillis - clock.millis()) / 1000.0).toLong().coerceAtLeast(1)
                        error.value = "Çok fazla hatalı deneme. $seconds sn sonra tekrar deneyin."
                    }
                    AuthResult.NotConfigured -> error.value = "Bu giriş yöntemi tanımlı değil."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error.value = "Beklenmeyen bir hata oluştu."
            } finally {
                busy.value = false
            }
        }
    }
}

/**
 * Şifre kasası ekranlarını sarar: kasa açık değilse uygulama kilidinden BAĞIMSIZ olarak parola/PIN/biyometrik doğrulama ister.
 * İçerik (ve şifreleri yükleyen ViewModel'ler) yalnızca kasa açıkken oluşturulur.
 */
@Composable
fun VaultGate(onBack: () -> Unit, content: @Composable () -> Unit) {
    val vm: VaultUnlockViewModel = hiltViewModel()
    val until by vm.unlockedUntil.collectAsStateWithLifecycle()
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = SystemClock.elapsedRealtime()
            delay(1_000)
        }
    }
    if (until >= now) content() else VaultUnlockContent(vm, onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VaultUnlockContent(vm: VaultUnlockViewModel, onBack: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }
    val biometricAvailable = remember { BiometricHelper.isAvailable(context) }
    var pinMode by rememberSaveable { mutableStateOf(false) }
    var secret by remember { mutableStateOf("") }

    fun promptBiometric() {
        activity?.let {
            BiometricHelper.prompt(it, onSuccess = vm::onBiometricSuccess, subtitle = "Şifre kasasını açmak için doğrulayın")
        }
    }

    LaunchedEffect(ui.biometricEnabled) {
        if (ui.biometricEnabled && biometricAvailable) promptBiometric()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Şifre Kasası") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(24.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Kasa kilitli", style = MaterialTheme.typography.titleLarge)
            Text(
                if (pinMode) "Devam etmek için PIN'inizi girin." else "Devam etmek için uygulama parolanızı girin.",
                style = MaterialTheme.typography.bodyMedium
            )
            PasswordField(
                value = secret,
                onValueChange = { secret = it },
                label = if (pinMode) "PIN" else "Parola",
                modifier = Modifier.fillMaxWidth(),
                keyboardType = if (pinMode) KeyboardType.NumberPassword else KeyboardType.Password,
                isError = ui.error != null
            )
            ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = { if (pinMode) vm.submitPin(secret) else vm.submitPassword(secret) },
                enabled = secret.isNotEmpty() && !ui.busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Kasayı aç") }
            if (ui.pinSet) {
                TextButton(onClick = { pinMode = !pinMode; secret = "" }) {
                    Text(if (pinMode) "Parola ile aç" else "PIN ile aç")
                }
            }
            if (ui.biometricEnabled && biometricAvailable) {
                OutlinedButton(onClick = { promptBiometric() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Parmak izi / yüz ile aç")
                }
            }
        }
    }
}
