package com.kisisel.finans.report

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Başlangıç ve bitiş DAHİL tarih aralığı. */
data class DateRange(val start: LocalDate, val end: LocalDate) {
    init {
        require(!end.isBefore(start)) { "Bitiş tarihi başlangıçtan önce olamaz" }
    }

    operator fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)

    val days: Long get() = ChronoUnit.DAYS.between(start, end) + 1

    fun isWholeMonth(): Boolean =
        start.dayOfMonth == 1 && end == start.withDayOfMonth(start.lengthOfMonth())

    fun isWholeYear(): Boolean =
        start.dayOfYear == 1 && end.year == start.year && end.monthValue == 12 && end.dayOfMonth == 31

    /**
     * Karşılaştırma için önceki dönem: tam ay → önceki ay, tam yıl → önceki yıl,
     * diğer durumlarda aynı uzunlukta hemen önceki aralık.
     */
    fun previous(): DateRange = when {
        isWholeMonth() -> {
            val s = start.minusMonths(1)
            DateRange(s, s.withDayOfMonth(s.lengthOfMonth()))
        }
        isWholeYear() -> DateRange(start.minusYears(1), end.minusYears(1))
        else -> DateRange(start.minusDays(days), start.minusDays(1))
    }
}

enum class RangePreset(val label: String) {
    THIS_MONTH("Bu ay"),
    LAST_MONTH("Geçen ay"),
    LAST_3_MONTHS("Son 3 ay"),
    THIS_YEAR("Bu yıl"),
    LAST_YEAR("Geçen yıl"),
    CUSTOM("Özel aralık");

    /** CUSTOM için null (kullanıcı belirler). */
    fun range(today: LocalDate): DateRange? = when (this) {
        THIS_MONTH -> month(today)
        LAST_MONTH -> month(today.minusMonths(1))
        LAST_3_MONTHS -> DateRange(today.minusMonths(2).withDayOfMonth(1), today)
        THIS_YEAR -> DateRange(LocalDate.of(today.year, 1, 1), LocalDate.of(today.year, 12, 31))
        LAST_YEAR -> DateRange(LocalDate.of(today.year - 1, 1, 1), LocalDate.of(today.year - 1, 12, 31))
        CUSTOM -> null
    }

    private fun month(d: LocalDate) = DateRange(d.withDayOfMonth(1), d.withDayOfMonth(d.lengthOfMonth()))
}
