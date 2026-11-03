package com.kisisel.finans.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kisisel.finans.domain.model.DashboardTotals
import com.kisisel.finans.domain.model.InvoiceOccurrence
import com.kisisel.finans.domain.model.LedgerLine
import com.kisisel.finans.domain.model.PersonSummary
import com.kisisel.finans.domain.model.TransactionType
import com.kisisel.finans.domain.repository.InvoiceRepository
import com.kisisel.finans.domain.repository.LedgerRepository
import com.kisisel.finans.domain.usecase.EnsureOccurrencesUseCase
import com.kisisel.finans.domain.usecase.RecordClaimUseCase
import com.kisisel.finans.domain.usecase.RecordSettlementUseCase
import com.kisisel.finans.ui.common.launchCatching
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class HomeState(
    val today: LocalDate,
    val totals: DashboardTotals? = null,
    val overdue: List<InvoiceOccurrence> = emptyList(),
    val upcoming: List<InvoiceOccurrence> = emptyList(),
    val recent: List<LedgerLine> = emptyList(),
    val persons: List<PersonSummary> = emptyList(),
    val loaded: Boolean = false
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    ledger: LedgerRepository,
    invoices: InvoiceRepository,
    private val recordClaim: RecordClaimUseCase,
    private val recordSettlement: RecordSettlementUseCase,
    private val ensureOccurrences: EnsureOccurrencesUseCase,
    clock: Clock
) : ViewModel() {

    private val today: LocalDate = LocalDate.now(clock)

    val state: StateFlow<HomeState> = combine(
        ledger.observeDashboard(today),
        invoices.observeOccurrences(),
        ledger.observeRecentLines(RECENT_LIMIT),
        ledger.observePersonSummaries()
    ) { totals, occurrences, recent, persons ->
        val split = splitInvoices(occurrences, today)
        HomeState(
            today = today,
            totals = totals,
            overdue = split.overdue,
            upcoming = split.upcoming,
            recent = recent,
            persons = persons,
            loaded = true
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState(today))

    init {
        launchCatching { ensureOccurrences() }
    }

    fun today(): LocalDate = today

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
    }
}
