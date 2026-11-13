package com.kisisel.finans.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kisisel.finans.agenda.AgendaBuilder
import com.kisisel.finans.agenda.AgendaEntry
import com.kisisel.finans.core.DayTicker
import com.kisisel.finans.domain.model.DashboardTotals
import com.kisisel.finans.domain.model.InvoiceOccurrence
import com.kisisel.finans.domain.model.LedgerLine
import com.kisisel.finans.domain.model.PersonSummary
import com.kisisel.finans.domain.model.TransactionType
import com.kisisel.finans.domain.repository.AgendaRepository
import com.kisisel.finans.domain.repository.InvoiceRepository
import com.kisisel.finans.domain.repository.LedgerRepository
import com.kisisel.finans.domain.usecase.EnsureOccurrencesUseCase
import com.kisisel.finans.domain.usecase.RecordClaimUseCase
import com.kisisel.finans.domain.usecase.RecordSettlementUseCase
import com.kisisel.finans.ui.common.launchCatching
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

data class HomeState(
    val today: LocalDate,
    val totals: DashboardTotals? = null,
    val overdue: List<InvoiceOccurrence> = emptyList(),
    val upcoming: List<InvoiceOccurrence> = emptyList(),
    val recent: List<LedgerLine> = emptyList(),
    val persons: List<PersonSummary> = emptyList(),
    /** Ajanda önizlemesi: notlar ve vadesi olan borç/alacaklar (faturalar zaten kendi bölümlerinde). */
    val agenda: List<AgendaEntry> = emptyList(),
    val agendaOverdueCount: Int = 0,
    val loaded: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    ledger: LedgerRepository,
    invoices: InvoiceRepository,
    agenda: AgendaRepository,
    private val recordClaim: RecordClaimUseCase,
    private val recordSettlement: RecordSettlementUseCase,
    private val ensureOccurrences: EnsureOccurrencesUseCase,
    private val dayTicker: DayTicker
) : ViewModel() {

    /** Gün değişince (ay toplamları, geciken/yaklaşan faturalar, ajanda dahil) yeniden hesaplanır. */
    val state: StateFlow<HomeState> = dayTicker.today.flatMapLatest { today ->
        combine(
            ledger.observeDashboard(today),
            invoices.observeOccurrences(),
            ledger.observeRecentLines(RECENT_LIMIT),
            ledger.observePersonSummaries(),
            combine(ledger.observeClaims(), agenda.observeAll()) { claims, notes -> claims to notes }
        ) { totals, occurrences, recent, persons, (claims, notes) ->
            val split = splitInvoices(occurrences, today)
            val view = AgendaBuilder.build(occurrences, claims, notes, today, windowDays = 7)
            val preview = (view.overdue + view.days.flatMap { it.entries }).filter { it !is AgendaEntry.Invoice }
            HomeState(
                today = today,
                totals = totals,
                overdue = split.overdue,
                upcoming = split.upcoming,
                recent = recent,
                persons = persons,
                agenda = preview.take(AGENDA_PREVIEW),
                agendaOverdueCount = view.overdue.count { it !is AgendaEntry.Invoice },
                loaded = true
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState(dayTicker.today.value))

    init {
        launchCatching { ensureOccurrences() }
    }

    fun today(): LocalDate = dayTicker.today.value

    /** Hızlı işlem: borç verme/alma, kişi adına ödeme, tahsilat, borç ödeme. */
    fun submit(
        personId: Long,
        type: TransactionType,
        amountKurus: Long,
        date: LocalDate,
        dueDate: LocalDate?,
        description: String,
        key: String,
        onResult: (String?) -> Unit
    ) {
        launchCatching(onResult) {
            if (type.isSettlement) {
                recordSettlement(personId, type, amountKurus, date, description, key)
            } else {
                recordClaim(personId, type, amountKurus, date, dueDate, description, key)
            }
        }
    }

    private companion object {
        const val RECENT_LIMIT = 8
        const val AGENDA_PREVIEW = 5
    }
}
