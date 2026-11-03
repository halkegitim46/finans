package com.kisisel.finans.ui.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kisisel.finans.security.AppLockManager
import com.kisisel.finans.security.AppResetter
import com.kisisel.finans.security.AuthResult
import com.kisisel.finans.security.AuthService
import com.kisisel.finans.security.LockState
import com.kisisel.finans.security.SecurityStore
import com.kisisel.finans.ui.common.launchCatching
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject
import kotlin.math.ceil

data class LockUi(
    val pinSet: Boolean = false,
    val biometricEnabled: Boolean = false,
    val lockoutUntil: Long = 0,
    val error: String? = null,
    val busy: Boolean = false
)

@HiltViewModel
class LockViewModel @Inject constructor(
    private val manager: AppLockManager,
    private val auth: AuthService,
    store: SecurityStore,
    private val resetter: AppResetter,
    private val clock: Clock
) : ViewModel() {

    val lockState: StateFlow<LockState> = manager.state

    private val error = MutableStateFlow<String?>(null)
    private val busy = MutableStateFlow(false)

    val ui: StateFlow<LockUi> = combine(store.settings, error, busy) { s, e, b ->
        LockUi(
            pinSet = s.pinRecord != null,
            biometricEnabled = s.biometricEnabled,
            lockoutUntil = s.lockoutUntilMillis,
            error = e,
            busy = b
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LockUi())

    /** İlk kurulum: parola belirle, başarılıysa uygulamayı aç. */
    fun setup(password: String, confirm: String) {
        if (busy.value) return
        if (password != confirm) {
            error.value = "Parolalar eşleşmiyor."
            return
        }
        busy.value = true
        error.value = null
        launchCatching(
            onResult = { err ->
                busy.value = false
                if (err == null) manager.onUnlocked() else error.value = err
            }
        ) { auth.setInitialPassword(password) }
    }

    fun submitPassword(password: String) = submit { auth.verifyPassword(password) }

    fun submitPin(pin: String) = submit { auth.verifyPin(pin) }

    fun onBiometricSuccess() {
        viewModelScope.launch {
            auth.resetFailures()
            manager.onUnlocked()
        }
    }

    fun resetAll() {
        viewModelScope.launch(Dispatchers.IO) { resetter.wipeAndRestart() }
    }

    private fun submit(block: suspend () -> AuthResult) {
        if (busy.value) return
        busy.value = true
        error.value = null
        viewModelScope.launch {
            try {
                when (val r = block()) {
                    AuthResult.Success -> manager.onUnlocked()
                    is AuthResult.Wrong -> error.value = "Hatalı giriş. Kilitlenmeden önce kalan deneme: ${r.attemptsLeft}"
                    is AuthResult.LockedOut -> error.value = lockoutText(r.untilMillis)
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

    private fun lockoutText(untilMillis: Long): String {
        val seconds = ceil((untilMillis - clock.millis()) / 1000.0).toLong().coerceAtLeast(1)
        return "Çok fazla hatalı deneme. $seconds sn sonra tekrar deneyin."
    }
}
