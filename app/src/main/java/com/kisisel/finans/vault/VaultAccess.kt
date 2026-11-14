package com.kisisel.finans.vault

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Şifre kasası erişim penceresi: uygulama kilidi açık olsa bile kasa için parola/PIN/biyometrik doğrulama ayrıca istenir.
 * Doğrulamadan sonra [WINDOW_MS] boyunca açık kalır (etkileşimle uzar); uygulama arka plana geçince veya "Kasayı kilitle" ile kapanır.
 * Süre kaynağı enjekte edilir (test edilebilirlik).
 */
@Singleton
class VaultAccess(private val now: () -> Long) {

    @Inject
    constructor() : this({ SystemClock.elapsedRealtime() })

    private val _unlockedUntil = MutableStateFlow(Long.MIN_VALUE)
    val unlockedUntil: StateFlow<Long> = _unlockedUntil.asStateFlow()

    fun unlock() {
        _unlockedUntil.value = now() + WINDOW_MS
    }

    /** Kasa içinde etkileşim: pencereyi uzatır (yalnızca açıkken). */
    fun touch() {
        if (isUnlocked()) _unlockedUntil.value = now() + WINDOW_MS
    }

    fun lock() {
        _unlockedUntil.value = Long.MIN_VALUE
    }

    fun isUnlocked(): Boolean = _unlockedUntil.value >= now()

    companion object {
        const val WINDOW_MS = 2 * 60 * 1000L
    }
}
