package com.kisisel.finans.reminder

import com.kisisel.finans.core.Money
import com.kisisel.finans.domain.model.AgendaItem
import com.kisisel.finans.domain.model.ClaimLine
import com.kisisel.finans.domain.model.OccurrenceStatus
import com.kisisel.finans.domain.model.ReminderCandidate
import com.kisisel.finans.domain.model.TransactionType
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.time.format.DateTimeFormatter

const val EXTRA_OCCURRENCE_ID = "occurrence_id"
const val EXTRA_PERSON_ID = "person_id"
const val EXTRA_AGENDA_ID = "agenda_id"

data class PlannedReminder(
    val occurrenceId: Long,
    /** Aynı hatırlatmanın tekrar gönderilmemesi için benzersiz anahtar. */
    val key: String,
    val title: String,
    val text: String,
    /** Doluysa dokununca bu kişinin sayfası açılır (alacak/borç vade hatırlatmaları). */
    val personId: Long? = null,
    /** Doluysa dokununca bu ajanda kaydı açılır. */
    val agendaId: Long? = null
)

/** Anahtardan fatura dönemi id'sini çıkarır ("12:d3" -> 12). Alacak/borç ("c12:...") ve ajanda ("a5:...") anahtarları için null. */
fun occurrenceIdOfKey(key: String): Long? = key.substringBefore(':').toLongOrNull()

/** Anahtarın sahibi: fatura dönemi "12", alacak/borç "c12", ajanda notu "a5". Eski anahtarları temizlemekte kullanılır. */
fun ownerOfKey(key: String): String = key.substringBefore(':')

/**
 * Hangi hatırlatmaların bugün gönderileceğini belirleyen saf mantık.
 *  - Vadeden önce: kalan gün sayısı şablonun seçtiği günlerden biriyse (7/3/1/vade günü) bir kez.
 *  - Gecikmişse: gecikme aralığı N ise 1., 1+N., 1+2N. günlerde (aralık 0 = kapalı).
 *  - Ödenmiş/iptal dönemler ve daha önce gönderilmiş anahtarlar atlanır.
 */
object ReminderPlanner {
    private val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    private val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    /** Borç/alacak vade hatırlatma günleri ve gecikme aralığı (sabit; ayarlardan yalnızca açılıp kapatılır). */
    private val CLAIM_DAYS = setOf(3, 1, 0)
    private const val CLAIM_OVERDUE_EVERY_DAYS = 3L

    /** Ajanda hatırlatması bu süreden eskiyse (uygulama/telefon uzun süre kapalıydı) artık gönderilmez. */
    const val AGENDA_STALE_HOURS = 24L

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

    // ---------------- alacak / borç vadesi ----------------

    /**
     * Vadesi olan açık alacak/borçlar için: vadeden 3, 1 gün önce ve vade günü; gecikmişse 1. günde ve sonra her 3 günde.
     * Faturadan doğan alacaklar hariçtir (vadeleri fatura vadesidir; fatura zaten ayrıca hatırlatılır).
     */
    fun planClaims(claims: List<ClaimLine>, today: LocalDate, sentKeys: Set<String>): List<PlannedReminder> =
        claims.mapNotNull { claimReminder(it, today) }.filter { it.key !in sentKeys }.sortedBy { it.key }

    private fun claimReminder(c: ClaimLine, today: LocalDate): PlannedReminder? {
        val due = c.dueDate ?: return null
        if (c.occurrenceId != null || c.openKurus <= 0) return null
        val days = ChronoUnit.DAYS.between(today, due)

        val key: String
        val status: String
        if (days >= 0) {
            if (days > 30 || days.toInt() !in CLAIM_DAYS) return null
            key = "c${c.entryId}:d$days"
            status = if (days == 0L) "Bugün vade günü" else "$days gün sonra vade"
        } else {
            val late = -days
            if ((late - 1) % CLAIM_OVERDUE_EVERY_DAYS != 0L) return null
            key = "c${c.entryId}:o$late"
            status = "$late gün gecikti"
        }

        val payable = c.type == TransactionType.BORROW
        val title = (if (payable) "Borç vadesi: " else "Alacak vadesi: ") + c.personName
        val text = listOfNotNull(
            c.description.takeIf { it.isNotBlank() },
            Money.format(c.openKurus),
            "Vade: ${due.format(DATE_FMT)}",
            status
        ).joinToString(" • ")
        return PlannedReminder(0, key, title, text, personId = c.personId)
    }

    // ---------------- ajanda notları ----------------

    /**
     * Hatırlatma zamanı gelmiş (zamanı <= şimdi) ve en çok [AGENDA_STALE_HOURS] saat öncesine kadar kaçmış notlar.
     * Anahtar hatırlatma zamanını içerir: not düzenlenip zamanı değişirse yeniden bildirilir.
     */
    fun planAgenda(items: List<AgendaItem>, now: LocalDateTime, sentKeys: Set<String>): List<PlannedReminder> =
        items.mapNotNull { agendaReminder(it, now) }.filter { it.key !in sentKeys }.sortedBy { it.key }

    private fun agendaReminder(item: AgendaItem, now: LocalDateTime): PlannedReminder? {
        if (item.isDone) return null
        val remindAt = item.reminderAt() ?: return null
        if (remindAt.isAfter(now)) return null
        if (Duration.between(remindAt, now).toHours() >= AGENDA_STALE_HOURS) return null

        val whenText = item.time?.let { "${item.date.format(DATE_FMT)} ${it.format(TIME_FMT)}" }
            ?: "${item.date.format(DATE_FMT)} (gün boyu)"
        val text = listOfNotNull(whenText, item.personName, item.note.takeIf { it.isNotBlank() }).joinToString(" • ")
        return PlannedReminder(
            occurrenceId = 0,
            key = "a${item.id}:t${remindAt.toEpochSecond(ZoneOffset.UTC)}",
            title = "Ajanda: ${item.title}",
            text = text,
            agendaId = item.id
        )
    }

    /** Henüz gelmemiş en yakın not hatırlatma zamanı (planlı kontrol için); yoksa null. */
    fun nextAgendaReminder(items: List<AgendaItem>, now: LocalDateTime): LocalDateTime? =
        items.filter { !it.isDone }.mapNotNull { it.reminderAt() }.filter { it.isAfter(now) }.minOrNull()
}
