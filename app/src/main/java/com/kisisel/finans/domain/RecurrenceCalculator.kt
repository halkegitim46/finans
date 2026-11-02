package com.kisisel.finans.domain

import com.kisisel.finans.domain.model.Recurrence
import java.time.LocalDate

/**
 * Fatura dönem tarihleri. Her tarih ilk vade tarihinden hesaplanır (kayma olmaz):
 * 31 Ocak -> 28 Şubat -> 31 Mart.
 */
object RecurrenceCalculator {
    private const val MAX_OCCURRENCES = 1000

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

    /** Vade tarihi `until` dahil olacak şekilde (index, vade) çiftlerini üretir. */
    fun occurrencesUntil(
        firstDue: LocalDate,
        recurrence: Recurrence,
        customDays: Int?,
        until: LocalDate
    ): List<Pair<Int, LocalDate>> {
        if (recurrence == Recurrence.CUSTOM_DAYS) {
            require(customDays != null && customDays >= 1) { "Özel tekrar için gün sayısı en az 1 olmalı" }
        }
        val result = mutableListOf<Pair<Int, LocalDate>>()
        var index = 0
        while (index < MAX_OCCURRENCES) {
            val due = dueDateFor(firstDue, recurrence, customDays, index)
            if (due.isAfter(until)) break
            result += index to due
            if (recurrence == Recurrence.ONCE) break
            index++
        }
        return result
    }

    /** (şablon, dönem anahtarı) benzersizdir; aynı dönem iki kez oluşturulamaz. */
    fun periodKey(recurrence: Recurrence, due: LocalDate): String = when (recurrence) {
        Recurrence.MONTHLY -> "%04d-%02d".format(due.year, due.monthValue)
        Recurrence.YEARLY -> "%04d".format(due.year)
        Recurrence.ONCE, Recurrence.CUSTOM_DAYS -> due.toString()
    }
}
