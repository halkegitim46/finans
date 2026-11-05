package com.kisisel.finans.ui.invoices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kisisel.finans.core.DayTicker
import com.kisisel.finans.domain.model.InvoiceOccurrence
import com.kisisel.finans.domain.model.InvoiceTemplate
import com.kisisel.finans.domain.model.OccurrenceStatus
import com.kisisel.finans.domain.repository.InvoiceRepository
import com.kisisel.finans.domain.usecase.EnsureOccurrencesUseCase
import com.kisisel.finans.ui.common.launchCatching
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

data class InvoiceListState(
    val today: LocalDate = LocalDate.now(),
    val pending: List<InvoiceOccurrence> = emptyList(),
    val paid: List<InvoiceOccurrence> = emptyList(),
    val templates: List<InvoiceTemplate> = emptyList(),
    val loaded: Boolean = false
)

@HiltViewModel
class InvoiceListViewModel @Inject constructor(
    invoices: InvoiceRepository,
    private val ensureOccurrences: EnsureOccurrencesUseCase,
    private val dayTicker: DayTicker
) : ViewModel() {

    val state: StateFlow<InvoiceListState> = combine(
        invoices.observeOccurrences(),
        invoices.observeTemplates(),
        dayTicker.today
    ) { occurrences, templates, today ->
        InvoiceListState(
            today = today,
            pending = occurrences.filter { it.status == OccurrenceStatus.UNPAID }.sortedBy { it.dueDate },
            paid = occurrences.filter { it.status == OccurrenceStatus.PAID }
                .sortedByDescending { it.paidDate ?: it.dueDate },
            templates = templates,
            loaded = true
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InvoiceListState(today = dayTicker.today.value))

    init {
        // Eksik dönemleri üret (günlük otomatik üretim ReminderWorker'dadır).
        launchCatching { ensureOccurrences() }
    }
}
