package com.kisisel.finans.ui.agenda

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kisisel.finans.agenda.AgendaBuilder
import com.kisisel.finans.agenda.AgendaView
import com.kisisel.finans.core.DayTicker
import com.kisisel.finans.domain.repository.AgendaRepository
import com.kisisel.finans.domain.repository.InvoiceRepository
import com.kisisel.finans.domain.repository.LedgerRepository
import com.kisisel.finans.domain.usecase.SetAgendaDoneUseCase
import com.kisisel.finans.reminder.ReminderScheduler
import com.kisisel.finans.ui.common.launchCatching
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

data class AgendaUiState(val today: LocalDate, val view: AgendaView, val loaded: Boolean)

@HiltViewModel
class AgendaViewModel @Inject constructor(
    invoices: InvoiceRepository,
    ledger: LedgerRepository,
    agenda: AgendaRepository,
    private val setDone: SetAgendaDoneUseCase,
    private val dayTicker: DayTicker,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val state: StateFlow<AgendaUiState> = combine(
        invoices.observeOccurrences(),
        ledger.observeClaims(),
        agenda.observeAll(),
        dayTicker.today
    ) { occurrences, claims, notes, today ->
        AgendaUiState(today, AgendaBuilder.build(occurrences, claims, notes, today), loaded = true)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AgendaUiState(dayTicker.today.value, AgendaView(emptyList(), emptyList(), 0, emptyList()), loaded = false)
    )

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun toggleDone(id: Long, done: Boolean) {
        launchCatching(
            onResult = { err ->
                if (err != null) _messages.tryEmit(err) else ReminderScheduler.checkAgendaNow(context)
            }
        ) { setDone(id, done) }
    }
}
