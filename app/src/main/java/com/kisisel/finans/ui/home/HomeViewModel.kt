package com.kisisel.finans.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kisisel.finans.agenda.AgendaBuilder
import com.kisisel.finans.agenda.AgendaEntry
import com.kisisel.finans.core.DayTicker
import com.kisisel.finans.domain.model.DashboardTotals
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

/**
 * Ana Sayfa durumu: özet, "dikkat gerektirenler" (geciken + önümüzdeki 7 gün: fatura vadeleri, borç/alacak vadeleri, notlar)
 * ve son hareketler. Ayrıntılı listeler kendi bölümlerindedir (Faturalar, İşlemler, Ajanda).
 */
data class HomeState(
    val today: LocalDate,
    val totals: DashboardTotals? = null,
    val attention: List<AgendaEntry> = emptyList(),
    val attentionOverdue: Int = 0,
    val recent: List<LedgerLine> = emptyList(),
    /** Hızlı işlem kişi seçici için; ekranda liste olarak gösterilmez. */
    val persons: List<PersonSummary> = emptyList(),
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

    /** Gün değişince (ay toplamları, geciken/yaklaşan kalemler dahil) yeniden hesaplanır. */
    val state: StateFlow<HomeState> = dayTicker.today.flatMapLatest { today ->
        combine(
            ledger.observeDashboard(today),
            invoices.observeOccurrences(),
            ledger.observeRecentLines(RECENT_LIMIT),
            ledger.observePersonSummaries(),
            combine(ledger.observeClaims(), agenda.observeAll()) { claims, notes -> claims to notes }
        ) { totals, occurrences, recent, persons, (claims, notes) ->
            val view = AgendaBuilder.build(occurrences, claims, notes, today, windowDays = ATTENTION_DAYS)
            HomeState(
                today = today,
                totals = totals,
                attention = (view.overdue + view.days.flatMap { it.entries }).take(ATTENTION_PREVIEW),
                attentionOverdue = view.overdue.size,
                recent = recent,
                persons = persons,
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
        const val RECENT_LIMIT = 5
        const val ATTENTION_DAYS = 7L
        const val ATTENTION_PREVIEW = 5
    }
}
