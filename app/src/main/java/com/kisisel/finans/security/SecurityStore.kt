package com.kisisel.finans.security

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

data class SecuritySettings(
    /** PBKDF2 kaydı; null = parola henüz belirlenmemiş (ilk kurulum). */
    val passwordRecord: String? = null,
    val pinRecord: String? = null,
    val biometricEnabled: Boolean = false,
    val lockOnBackground: Boolean = true,
    /** Arka planda bu süreden uzun kalırsa kilitlenir (0 = hemen). */
    val lockTimeoutSeconds: Int = 0,
    /** Ekran görüntüsü / son uygulamalar önizlemesini engeller. */
    val secureScreen: Boolean = true,
    val failedAttempts: Int = 0,
    val lockoutUntilMillis: Long = 0
)

interface SecurityStore {
    val settings: Flow<SecuritySettings>
    suspend fun load(): SecuritySettings = settings.first()

    /** Okuma-değiştirme-yazma tek adımda. */
    suspend fun update(transform: (SecuritySettings) -> SecuritySettings)
}
