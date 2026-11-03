package com.kisisel.finans.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class LockState { LOADING, NEEDS_SETUP, LOCKED, UNLOCKED }

/**
 * Uygulama kilit durumu. Süreç yeniden başladığında her zaman kilitli başlar.
 * Arka plana geçiş/dönüş zamanları Activity'den (SystemClock.elapsedRealtime) verilir; böylece mantık saf ve test edilebilir.
 */
@Singleton
class AppLockManager @Inject constructor() {

    private val _state = MutableStateFlow(LockState.LOADING)
    val state: StateFlow<LockState> = _state.asStateFlow()

    @Volatile
    private var settings = SecuritySettings()
    private var backgroundedAt: Long? = null

    /** Ayarlar her değiştiğinde Activity tarafından iletilir. İlk iletimde başlangıç durumu belirlenir. */
    fun onSettings(s: SecuritySettings) {
        settings = s
        if (_state.value == LockState.LOADING) {
            _state.value = if (s.passwordRecord == null) LockState.NEEDS_SETUP else LockState.LOCKED
        }
    }

    fun onUnlocked() {
        _state.value = LockState.UNLOCKED
    }

    fun lock() {
        if (_state.value == LockState.UNLOCKED) _state.value = LockState.LOCKED
    }

    fun onBackgrounded(elapsedMillis: Long) {
        backgroundedAt = elapsedMillis
    }

    /** Ön plana dönünce, ayarlanan süre aşıldıysa kilitler. Eşzamanlıdır (içerik bir an görünmesin). */
    fun onForegrounded(elapsedMillis: Long) {
        val at = backgroundedAt ?: return
        backgroundedAt = null
        val s = settings
        if (_state.value == LockState.UNLOCKED &&
            s.passwordRecord != null &&
            s.lockOnBackground &&
            elapsedMillis - at >= s.lockTimeoutSeconds * 1000L
        ) {
            lock()
        }
    }
}
