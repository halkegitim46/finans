package com.kisisel.finans.backup

import com.kisisel.finans.domain.FinanceException
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

const val MAX_BACKUP_FILE_BYTES = 64 * 1024 * 1024

/** Sınır aşılırsa okumayı keser (bozuk/kötü niyetli büyük dosyalara karşı). */
fun readLimited(input: InputStream, maxBytes: Int): ByteArray {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    var total = 0
    while (true) {
        val n = input.read(buffer)
        if (n < 0) break
        total += n
        if (total > maxBytes) throw FinanceException.BackupInvalid("Dosya çok büyük.")
        out.write(buffer, 0, n)
    }
    return out.toByteArray()
}

fun backupFileName(at: LocalDateTime): String =
    "KisiselFinansTakip_yedek_" + at.format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm")) + ".kftbak"
