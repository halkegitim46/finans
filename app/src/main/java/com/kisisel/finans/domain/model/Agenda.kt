package com.kisisel.finans.domain.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class AgendaItem(
    val id: Long,
    val title: String,
    val note: String,
    val date: LocalDate,
    /** null = gün boyu */
    val time: LocalTime?,
    val personId: Long?,
    val personName: String?,
    /** null = hatırlatma yok; 0 = zamanında; 60 = 1 saat önce; 1440 = 1 gün önce */
    val remindMinutesBefore: Int?,
    val isDone: Boolean,
    val doneAt: Long?
) {
    /** Hatırlatma zamanı (yerel). Saat yoksa 09:00 esas alınır; hatırlatma kapalıysa null. */
    fun reminderAt(): LocalDateTime? =
        remindMinutesBefore?.let { LocalDateTime.of(date, time ?: DEFAULT_REMINDER_TIME).minusMinutes(it.toLong()) }

    companion object {
        val DEFAULT_REMINDER_TIME: LocalTime = LocalTime.of(9, 0)
    }
}

data class NewAgendaItem(
    val title: String,
    val note: String,
    val date: LocalDate,
    val time: LocalTime?,
    val personId: Long?,
    val remindMinutesBefore: Int?
)

data class AgendaUpdate(
    val id: Long,
    val title: String,
    val note: String,
    val date: LocalDate,
    val time: LocalTime?,
    val personId: Long?,
    val remindMinutesBefore: Int?
)
