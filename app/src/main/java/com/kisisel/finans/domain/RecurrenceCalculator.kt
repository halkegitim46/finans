package com.kisisel.finans.domain

import com.kisisel.finans.domain.model.Recurrence
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Fatura dönem tarihleri. Her tarih ilk vade tarihinden hesaplanır (kayma olmaz):
 * 31 Ocak -> 28 Şubat -> 31 Mart.
 */
object RecurrenceCalculator {
    /** Tek çağrıda döndürülecek en çok dönem sayısı (en yeniler tutulur). */
    private const val MAX_OCCURRENCES = 1000

    /** Sonsuz döngüye karşı üst sınır (yanlış/çok uzak tarihler için). */
    private const val MAX_ITERATIONS = 50_000

    fun dueDateFor(firstDue: LocalDate, recurrence: Recurrence, customDays: Int?, index: Int): LocalDate {
        require(index >= 0) { "index negatif olamaz" }
        return when (recurrence) {
            Recurrence.ONCE -> {
                require(index == 0) { "Tek seferlik faturanın yalnızca 0. dönemi vardır" }
                firstDue
            }
            Recurrence.MONTHLY -> firstDue.plusMonths(index.toLong())
            Recurrence.YEARLY -> firstDue.plusYears(index.toLong())
            Recurrence.CUSTOM_DAYS -> {
                val days = requireNotNull(customDays) { "Özel tekrar için gün sayısı gerekli" }
                require(days >= 1) { "Gün sayısı en az 1 olmalı" }
                firstDue.plusDays(days.toLong() * index)
            }
        }
    }

    /**
     * Vadesi [from, until] (ikisi dahil) aralığında olan dönemleri (index, vade) olarak verir.
     * Başlangıç indeksi doğrudan hesaplanır; geçmişin tamamı dolaşılmaz, bu yüzden çok eski bir ilk vade
     * (ör. yıllar önce başlayan günlük tekrar) güncel dönemlerin atlanmasına yol açmaz.
     * Sonuç [maxResults]'ı aşarsa EN YENİ dönemler tutulur.
     */
    fun occurrencesBetween(
        firstDue: LocalDate,
        recurrence: Recurrence,
        customDays: Int?,
        from: LocalDate,
        until: LocalDate,
        maxResults: Int = MAX_OCCURRENCES
    ): List<Pair<Int, LocalDate>> {
        if (recurrence == Recurrence.CUSTOM_DAYS) {
            require(customDays != null && customDays >= 1) { "Özel tekrar için gün sayısı en az 1 olmalı" }
        }
        if (until.isBefore(from)) return emptyList()

        val result = ArrayList<Pair<Int, LocalDate>>()
        fun collect(startIndex: Long, dateAt: (Long) -> LocalDate) {
            var n = startIndex
            var guard = 0
            while (guard++ < MAX_ITERATIONS && n <= Int.MAX_VALUE) {
                val date = dateAt(n)
                if (date.isAfter(until)) break
                if (!date.isBefore(from)) result += n.toInt() to date
                n++
            }
        }

        when (recurrence) {
            Recurrence.ONCE -> if (firstDue in from..until) result += 0 to firstDue
            Recurrence.MONTHLY -> collect(
                maxOf(0L, ChronoUnit.MONTHS.between(firstDue, from) - 2)
            ) { firstDue.plusMonths(it) }
            Recurrence.YEARLY -> collect(
                maxOf(0L, ChronoUnit.YEARS.between(firstDue, from) - 2)
            ) { firstDue.plusYears(it) }
            Recurrence.CUSTOM_DAYS -> {
                val days = customDays!!.toLong()
                val start = if (from.isAfter(firstDue)) maxOf(0L, ChronoUnit.DAYS.between(firstDue, from) / days - 1) else 0L
                collect(start) { firstDue.plusDays(days * it) }
            }
        }
        return if (result.size > maxResults) result.takeLast(maxResults) else result
    }

    /** İlk vadeden `until` tarihine kadar (dahil) tüm dönemler. */
    fun occurrencesUntil(
        firstDue: LocalDate,
        recurrence: Recurrence,
        customDays: Int?,
        until: LocalDate
    ): List<Pair<Int, LocalDate>> = occurrencesBetween(firstDue, recurrence, customDays, firstDue, until)

    /** (şablon, dönem anahtarı) benzersizdir; aynı dönem iki kez oluşturulamaz. */
    fun periodKey(recurrence: Recurrence, due: LocalDate): String = when (recurrence) {
        Recurrence.MONTHLY -> "%04d-%02d".format(due.year, due.monthValue)
        Recurrence.YEARLY -> "%04d".format(due.year)
        Recurrence.ONCE, Recurrence.CUSTOM_DAYS -> due.toString()
    }
}
