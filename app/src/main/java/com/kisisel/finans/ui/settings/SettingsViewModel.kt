package com.kisisel.finans.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kisisel.finans.data.local.LegacyDatabaseMigrator
import com.kisisel.finans.domain.FinanceException
import com.kisisel.finans.security.AppLockManager
import com.kisisel.finans.security.AppResetter
import com.kisisel.finans.security.AuthResult
import com.kisisel.finans.security.AuthService
import com.kisisel.finans.security.SecuritySettings
import com.kisisel.finans.reminder.ReminderPrefs
import com.kisisel.finans.security.SecurityStore
import com.kisisel.finans.ui.common.launchCatching
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: SecurityStore,
    private val auth: AuthService,
    private val manager: AppLockManager,
    private val resetter: AppResetter,
    private val reminderPrefs: ReminderPrefs
) : ViewModel() {

    val settings: StateFlow<SecuritySettings> = store.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SecuritySettings())

    /** Borç/alacak vade hatırlatmaları (varsayılan açık). */
    val claimReminders: StateFlow<Boolean> = reminderPrefs.claimRemindersFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setClaimReminders(value: Boolean) {
        viewModelScope.launch { reminderPrefs.setClaimRemindersEnabled(value) }
    }

    private val _legacyExists = MutableStateFlow(LegacyDatabaseMigrator.legacyExists(context))
    val legacyExists: StateFlow<Boolean> = _legacyExists.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    val versionName: String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "-"

    fun showMessage(text: String) {
        _messages.tryEmit(text)
    }

    fun setLockOnBackground(value: Boolean) {
        viewModelScope.launch { store.update { it.copy(lockOnBackground = value) } }
    }

    fun setTimeout(seconds: Int) {
        viewModelScope.launch { store.update { it.copy(lockTimeoutSeconds = seconds) } }
    }

    fun setSecureScreen(value: Boolean) {
        viewModelScope.launch { store.update { it.copy(secureScreen = value) } }
    }

    fun setBiometric(value: Boolean) {
        viewModelScope.launch { store.update { it.copy(biometricEnabled = value) } }
    }

    fun changePassword(old: String, new: String, onResult: (String?) -> Unit) {
        launchCatching(
            onResult = { err ->
                if (err == null) showMessage("Parola değiştirildi.")
                onResult(err)
            }
        ) { auth.changePassword(old, new).orThrow() }
    }

    fun setPin(pin: String, currentPassword: String, onResult: (String?) -> Unit) {
        launchCatching(
            onResult = { err ->
                if (err == null) showMessage("PIN ayarlandı.")
                onResult(err)
            }
        ) { auth.setPin(pin, currentPassword).orThrow() }
    }

    fun removePin() {
        viewModelScope.launch {
            auth.removePin()
            showMessage("PIN kaldırıldı.")
        }
    }

    fun lockNow() = manager.lock()

    fun deleteLegacy() {
        LegacyDatabaseMigrator.deleteLegacy(context)
        _legacyExists.value = false
        showMessage("Eski şifresiz veri dosyası silindi.")
    }

    fun resetAll() {
        viewModelScope.launch(Dispatchers.IO) { resetter.wipeAndRestart() }
    }

    private fun AuthResult.orThrow() {
        when (this) {
            AuthResult.Success -> Unit
            is AuthResult.Wrong -> throw FinanceException.InvalidInput("Mevcut parola hatalı. Kalan deneme: $attemptsLeft")
            is AuthResult.LockedOut -> throw FinanceException.InvalidInput("Çok fazla hatalı deneme. Daha sonra tekrar deneyin.")
            AuthResult.NotConfigured -> throw FinanceException.InvalidInput("Parola tanımlı değil.")
        }
    }
}
