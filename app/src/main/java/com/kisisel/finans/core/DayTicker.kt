package com.kisisel.finans.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Bugün" tarihinin tek kaynağı. Uygulama ön plana her geldiğinde [refresh] çağrılır; tarih değişmişse
 * (gece yarısından sonra) bunu dinleyen ekranlar (ana sayfa, raporlar, faturalar) otomatik yeniden hesaplanır.
 * Aynı gün içinde çağrı hiçbir şeyi tetiklemez (StateFlow eşit değeri yaymaz).
 */
@Singleton
class DayTicker @Inject constructor(private val clock: Clock) {
    private val _today = MutableStateFlow(LocalDate.now(clock))
    val today: StateFlow<LocalDate> = _today.asStateFlow()

    fun refresh() {
        _today.value = LocalDate.now(clock)
    }
}
