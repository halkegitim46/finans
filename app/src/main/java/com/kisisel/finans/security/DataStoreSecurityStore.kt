package com.kisisel.finans.security

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.securityDataStore: DataStore<Preferences> by preferencesDataStore(name = "security")

private object Keys {
    val PASSWORD = stringPreferencesKey("password_record")
    val PIN = stringPreferencesKey("pin_record")
    val BIOMETRIC = booleanPreferencesKey("biometric_enabled")
    val LOCK_BG = booleanPreferencesKey("lock_on_background")
    val TIMEOUT = intPreferencesKey("lock_timeout_s")
    val SECURE = booleanPreferencesKey("secure_screen")
    val FAILED = intPreferencesKey("failed_attempts")
    val LOCKOUT = longPreferencesKey("lockout_until")
}

private fun Preferences.toSettings() = SecuritySettings(
    passwordRecord = this[Keys.PASSWORD],
    pinRecord = this[Keys.PIN],
    biometricEnabled = this[Keys.BIOMETRIC] ?: false,
    lockOnBackground = this[Keys.LOCK_BG] ?: true,
    lockTimeoutSeconds = this[Keys.TIMEOUT] ?: 0,
    secureScreen = this[Keys.SECURE] ?: true,
    failedAttempts = this[Keys.FAILED] ?: 0,
    lockoutUntilMillis = this[Keys.LOCKOUT] ?: 0L
)

private fun MutablePreferences.setOrRemove(key: Preferences.Key<String>, value: String?) {
    if (value == null) remove(key) else this[key] = value
}

private fun MutablePreferences.write(s: SecuritySettings) {
    setOrRemove(Keys.PASSWORD, s.passwordRecord)
    setOrRemove(Keys.PIN, s.pinRecord)
    this[Keys.BIOMETRIC] = s.biometricEnabled
    this[Keys.LOCK_BG] = s.lockOnBackground
    this[Keys.TIMEOUT] = s.lockTimeoutSeconds
    this[Keys.SECURE] = s.secureScreen
    this[Keys.FAILED] = s.failedAttempts
    this[Keys.LOCKOUT] = s.lockoutUntilMillis
}

@Singleton
class DataStoreSecurityStore @Inject constructor(
    @ApplicationContext private val context: Context
) : SecurityStore {

    override val settings: Flow<SecuritySettings> = context.securityDataStore.data.map { it.toSettings() }

    override suspend fun update(transform: (SecuritySettings) -> SecuritySettings) {
        context.securityDataStore.edit { prefs -> prefs.write(transform(prefs.toSettings())) }
    }
}
