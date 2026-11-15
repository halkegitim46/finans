package com.kisisel.finans.core

import java.time.format.DateTimeFormatter

/** Uygulamada kullanılan tarih/saat biçimleri (tek kaynak; ekran, CSV, bildirim ve dosya adlarında aynı). */
object DateFormats {
    /** 05.11.2026 */
    val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    /** 14:30 */
    val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    /** 05.11.2026 14:30 */
    val DATE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")

    /** Yedek dosya adı damgası: 2026-11-05_1430 */
    val FILE_STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm")
}
