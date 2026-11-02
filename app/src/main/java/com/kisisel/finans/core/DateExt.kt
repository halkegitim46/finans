package com.kisisel.finans.core

import java.time.Clock
import java.time.LocalDate

/** Tarihler veritabanında epochDay (Long), zaman damgaları epoch milisaniye olarak saklanır. */
fun LocalDate.toDb(): Long = toEpochDay()

fun Long.toLocalDate(): LocalDate = LocalDate.ofEpochDay(this)

fun Clock.today(): LocalDate = LocalDate.now(this)

fun Clock.nowMillis(): Long = millis()
