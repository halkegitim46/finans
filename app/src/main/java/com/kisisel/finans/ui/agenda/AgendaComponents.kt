package com.kisisel.finans.ui.agenda

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.kisisel.finans.agenda.AgendaEntry
import com.kisisel.finans.core.Money
import com.kisisel.finans.domain.model.OccurrenceStatus
import com.kisisel.finans.domain.model.TransactionType
import com.kisisel.finans.ui.common.dueStatusText
import com.kisisel.finans.ui.common.tr
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

private val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** Liste anahtarı: farklı türlerin kimlikleri çakışmasın. */
fun agendaEntryKey(e: AgendaEntry): String = when (e) {
    is AgendaEntry.Invoice -> "I${e.occurrence.id}"
    is AgendaEntry.Claim -> "C${e.claim.entryId}"
    is AgendaEntry.Note -> "N${e.item.id}"
}

fun agendaDayLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Bugün"
    today.plusDays(1) -> "Yarın"
    else -> date.tr() + " " + date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale("tr", "TR"))
}

private fun title(e: AgendaEntry): String = when (e) {
    is AgendaEntry.Invoice -> e.occurrence.name
    is AgendaEntry.Claim -> e.claim.description.ifBlank {
        if (e.claim.type == TransactionType.BORROW) "Borç vadesi" else "Alacak vadesi"
    }
    is AgendaEntry.Note -> e.item.title
}

private fun subtitle(e: AgendaEntry, today: LocalDate): String {
    val days = ChronoUnit.DAYS.between(today, e.date)
    return when (e) {
        is AgendaEntry.Invoice -> listOfNotNull(
            "Fatura", e.occurrence.personName, dueStatusText(OccurrenceStatus.UNPAID, days)
        ).joinToString(" • ")
        is AgendaEntry.Claim -> listOfNotNull(
            e.claim.personName,
            if (e.claim.type == TransactionType.BORROW) "Borç" else "Alacak",
            dueStatusText(OccurrenceStatus.UNPAID, days)
        ).joinToString(" • ")
        is AgendaEntry.Note -> listOfNotNull(
            e.item.time?.format(TIME_FMT) ?: "Gün boyu",
            e.item.personName,
            e.item.note.takeIf { it.isNotBlank() }
        ).joinToString(" • ")
    }
}

private fun amountText(e: AgendaEntry): String? = when (e) {
    is AgendaEntry.Invoice -> e.occurrence.amountKurus?.let { Money.format(it) } ?: "Tutar yok"
    is AgendaEntry.Claim -> Money.format(e.claim.openKurus)
    is AgendaEntry.Note -> null
}

/**
 * Ajanda satırı. onToggleDone verilirse (not satırları için) tamamlandı onay kutusu gösterilir.
 * Tarihi geçmiş kalemler kırmızı vurgulanır.
 */
@Composable
fun AgendaEntryCard(
    entry: AgendaEntry,
    today: LocalDate,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onToggleDone: ((Boolean) -> Unit)? = null
) {
    val overdue = entry.date.isBefore(today)
    val done = entry is AgendaEntry.Note && entry.item.isDone
    Card(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            when (entry) {
                is AgendaEntry.Note ->
                    if (onToggleDone != null) {
                        Checkbox(checked = entry.item.isDone, onCheckedChange = onToggleDone)
                    } else {
                        Icon(Icons.Filled.Event, contentDescription = "Ajanda notu")
                    }
                is AgendaEntry.Invoice -> Icon(Icons.Filled.Receipt, contentDescription = "Fatura")
                is AgendaEntry.Claim -> Icon(Icons.Filled.AccountBalanceWallet, contentDescription = "Borç veya alacak")
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title(entry),
                    style = MaterialTheme.typography.titleSmall,
                    textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None
                )
                Text(
                    subtitle(entry, today),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (overdue && !done) MaterialTheme.colorScheme.error else Color.Unspecified
                )
            }
            amountText(entry)?.let {
                Text(it, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
