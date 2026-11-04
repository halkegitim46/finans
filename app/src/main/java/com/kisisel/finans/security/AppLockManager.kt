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
    @Volatile
    private var suppressLockUntil: Long = Long.MIN_VALUE

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

    /**
     * Yedekleme gibi sistem dosya seçicisine gitmeden önce çağrılır. Dönüşteki ilk ön plana geçiş
     * (en çok 5 dakika içinde) kilitlemez; aksi halde seçici sonucu kilit ekranında kaybolurdu.
     */
    fun allowExternalUi(elapsedMillis: Long) {
        suppressLockUntil = elapsedMillis + EXTERNAL_UI_WINDOW_MS
    }

    fun onBackgrounded(elapsedMillis: Long) {
        backgroundedAt = elapsedMillis
    }

    /** Ön plana dönünce, ayarlanan süre aşıldıysa kilitler. Eşzamanlıdır (içerik bir an görünmesin). */
    fun onForegrounded(elapsedMillis: Long) {
        val at = backgroundedAt ?: return
        backgroundedAt = null
        if (suppressLockUntil >= elapsedMillis) {
            // Sistem dosya seçicisinden dönüş: bu bir kereye mahsus kilitleme yapılmaz.
            suppressLockUntil = Long.MIN_VALUE
            return
        }
        val s = settings
        if (_state.value == LockState.UNLOCKED &&
            s.passwordRecord != null &&
            s.lockOnBackground &&
            elapsedMillis - at >= s.lockTimeoutSeconds * 1000L
        ) {
            lock()
        }
    }

    private companion object {
        const val EXTERNAL_UI_WINDOW_MS = 5 * 60 * 1000L
    }
}
