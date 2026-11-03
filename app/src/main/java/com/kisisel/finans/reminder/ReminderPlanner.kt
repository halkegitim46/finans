package com.kisisel.finans.reminder

import com.kisisel.finans.core.Money
import com.kisisel.finans.domain.model.OccurrenceStatus
import com.kisisel.finans.domain.model.ReminderCandidate
import java.time.LocalDate
import java.time.format.DateTimeFormatter

const val EXTRA_OCCURRENCE_ID = "occurrence_id"

data class PlannedReminder(
    val occurrenceId: Long,
    /** Aynı hatırlatmanın tekrar gönderilmemesi için benzersiz anahtar. */
    val key: String,
    val title: String,
    val text: String
)

/** Anahtardan dönem id'sini çıkarır ("12:d3" -> 12). */
fun occurrenceIdOfKey(key: String): Long? = key.substringBefore(':').toLongOrNull()

/**
 * Hangi hatırlatmaların bugün gönderileceğini belirleyen saf mantık.
 *  - Vadeden önce: kalan gün sayısı şablonun seçtiği günlerden biriyse (7/3/1/vade günü) bir kez.
 *  - Gecikmişse: gecikme aralığı N ise 1., 1+N., 1+2N. günlerde (aralık 0 = kapalı).
 *  - Ödenmiş/iptal dönemler ve daha önce gönderilmiş anahtarlar atlanır.
 */
object ReminderPlanner {
    private val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    fun plan(candidates: List<ReminderCandidate>, today: LocalDate, sentKeys: Set<String>): List<PlannedReminder> =
        candidates.mapNotNull { reminderFor(it, today) }
            .filter { it.key !in sentKeys }
            .sortedBy { it.occurrenceId }

    private fun reminderFor(c: ReminderCandidate, today: LocalDate): PlannedReminder? {
        val o = c.occurrence
        if (o.status != OccurrenceStatus.UNPAID) return null
        val days = o.daysUntilDue(today)

        val key: String
        val status: String
        if (days >= 0) {
            if (days > 30 || days.toInt() !in c.reminderDays) return null
            key = "${o.id}:d$days"
            status = if (days == 0L) "Bugün son ödeme günü" else "$days gün sonra son ödeme"
        } else {
            val late = -days
            val every = c.overdueEveryDays.toLong()
            if (every <= 0 || (late - 1) % every != 0L) return null
            key = "${o.id}:o$late"
            status = "$late gün gecikti"
        }

        val amount = o.amountKurus?.let { Money.format(it) } ?: "Tutar girilmedi"
        return PlannedReminder(
            occurrenceId = o.id,
            key = key,
            title = "Fatura hatırlatma: ${o.name}",
            text = "$amount • Son ödeme: ${o.dueDate.format(DATE_FMT)} • $status"
        )
    }
}
