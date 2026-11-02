package com.kisisel.finans.ui.invoices

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kisisel.finans.domain.model.InvoiceOccurrence
import com.kisisel.finans.domain.repository.InvoiceRepository
import com.kisisel.finans.domain.usecase.CancelOccurrenceUseCase
import com.kisisel.finans.domain.usecase.PayInvoiceUseCase
import com.kisisel.finans.domain.usecase.SetOccurrenceAmountUseCase
import com.kisisel.finans.domain.usecase.UndoInvoicePaymentUseCase
import com.kisisel.finans.ui.common.launchCatching
import com.kisisel.finans.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class InvoiceDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    invoices: InvoiceRepository,
    private val setAmount: SetOccurrenceAmountUseCase,
    private val payInvoice: PayInvoiceUseCase,
    private val undoPayment: UndoInvoicePaymentUseCase,
    private val cancelOccurrence: CancelOccurrenceUseCase,
    private val clock: Clock
) : ViewModel() {

    private val occurrenceId: Long = savedStateHandle.get<Long>(Routes.ARG_OCCURRENCE_ID) ?: 0L

    val occurrence: StateFlow<InvoiceOccurrence?> = invoices.observeOccurrence(occurrenceId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Aynı faturanın tüm dönemleri (fatura bazlı ödeme geçmişi). */
    val history: StateFlow<List<InvoiceOccurrence>> = occurrence
        .filterNotNull()
        .map { it.templateId }
        .distinctUntilChanged()
        .flatMapLatest { invoices.observeHistory(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun today(): LocalDate = LocalDate.now(clock)

    fun saveAmount(amountKurus: Long, onResult: (String?) -> Unit) =
        launchCatching(onResult) { setAmount(occurrenceId, amountKurus) }

    fun pay(paidDate: LocalDate, onResult: (String?) -> Unit) =
        launchCatching(onResult) { payInvoice(occurrenceId, paidDate) }

    fun undo() = launchCatching(onResult = { _messages.tryEmit(it ?: "Ödeme geri alındı.") }) {
        undoPayment(occurrenceId)
    }

    fun cancel() = launchCatching(onResult = { _messages.tryEmit(it ?: "Dönem iptal edildi.") }) {
        cancelOccurrence(occurrenceId)
    }
}
