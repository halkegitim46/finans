package com.kisisel.finans.agenda

import com.kisisel.finans.domain.model.AgendaItem
import com.kisisel.finans.domain.model.ClaimLine
import com.kisisel.finans.domain.model.InvoiceOccurrence
import com.kisisel.finans.domain.model.OccurrenceStatus
import java.time.LocalDate
import java.time.LocalTime

/** Ajandadaki tek satır: fatura vadesi, borç/alacak vadesi veya kullanıcı notu. */
sealed interface AgendaEntry {
    val date: LocalDate
    val time: LocalTime?

    data class Invoice(val occurrence: InvoiceOccurrence) : AgendaEntry {
        override val date: LocalDate get() = occurrence.dueDate
        override val time: LocalTime? get() = null
    }

    data class Claim(val claim: ClaimLine) : AgendaEntry {
        override val date: LocalDate get() = claim.dueDate!!
        override val time: LocalTime? get() = null
    }

    data class Note(val item: AgendaItem) : AgendaEntry {
        override val date: LocalDate get() = item.date
        override val time: LocalTime? get() = item.time
    }
}

data class AgendaDay(val date: LocalDate, val entries: List<AgendaEntry>)

data class AgendaView(
    /** Vadesi/tarihi bugünden önce olan ve hâlâ açık kalemler (eskiden yeniye). */
    val overdue: List<AgendaEntry>,
    /** Bugünden itibaren pencere içindeki günler (yalnızca kalemi olan günler). */
    val days: List<AgendaDay>,
    /** Pencerenin ötesindeki kalem sayısı. */
    val laterCount: Int,
    /** Son tamamlanan notlar (en yeni önce, en çok 20). */
    val done: List<AgendaItem>
)

/**
 * Ajanda: ödenmemiş faturalar + vadesi olan açık borç/alacaklar + tamamlanmamış notlar tek zaman çizelgesinde.
 * Faturadan doğan alacaklar (vade = fatura vadesi) ayrıca gösterilmez; fatura zaten listededir.
 */
object AgendaBuilder {
    private const val MAX_DONE = 20

    fun build(
        occurrences: List<InvoiceOccurrence>,
        claims: List<ClaimLine>,
        notes: List<AgendaItem>,
        today: LocalDate,
        windowDays: Long = 30
    ): AgendaView {
        val entries = ArrayList<AgendaEntry>()
        occurrences.filter { it.status == OccurrenceStatus.UNPAID }.forEach { entries += AgendaEntry.Invoice(it) }
        claims.filter { it.occurrenceId == null && it.dueDate != null && it.openKurus > 0 }.forEach { entries += AgendaEntry.Claim(it) }
        notes.filter { !it.isDone }.forEach { entries += AgendaEntry.Note(it) }

        // Aynı gün: gün boyu kalemler önce, sonra saate göre; eşitse fatura < borç/alacak < not.
        val sorted = entries.sortedWith(
            compareBy<AgendaEntry>({ it.date }, { it.time ?: LocalTime.MIN }, { typeOrder(it) }, { idOf(it) })
        )
        val limit = today.plusDays(windowDays)
        val overdue = sorted.filter { it.date.isBefore(today) }
        val upcoming = sorted.filter { !it.date.isBefore(today) && !it.date.isAfter(limit) }
        val days = upcoming.groupBy { it.date }.map { (date, list) -> AgendaDay(date, list) }.sortedBy { it.date }

        return AgendaView(
            overdue = overdue,
            days = days,
            laterCount = sorted.count { it.date.isAfter(limit) },
            done = notes.filter { it.isDone }.sortedByDescending { it.doneAt ?: 0L }.take(MAX_DONE)
        )
    }

    private fun typeOrder(e: AgendaEntry): Int = when (e) {
        is AgendaEntry.Invoice -> 0
        is AgendaEntry.Claim -> 1
        is AgendaEntry.Note -> 2
    }

    private fun idOf(e: AgendaEntry): Long = when (e) {
        is AgendaEntry.Invoice -> e.occurrence.id
        is AgendaEntry.Claim -> e.claim.entryId
        is AgendaEntry.Note -> e.item.id
    }
}
