package com.kisisel.finans.ui.persons

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kisisel.finans.domain.model.LedgerEntry
import com.kisisel.finans.domain.model.PersonSummary
import com.kisisel.finans.domain.model.TransactionType
import com.kisisel.finans.domain.repository.LedgerRepository
import com.kisisel.finans.domain.usecase.ArchivePersonUseCase
import com.kisisel.finans.domain.usecase.CancelTransactionUseCase
import com.kisisel.finans.domain.usecase.CloseAccountUseCase
import com.kisisel.finans.domain.usecase.RecordClaimUseCase
import com.kisisel.finans.domain.usecase.RecordSettlementUseCase
import com.kisisel.finans.ui.common.launchCatching
import com.kisisel.finans.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class PersonDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    ledger: LedgerRepository,
    private val recordClaim: RecordClaimUseCase,
    private val recordSettlement: RecordSettlementUseCase,
    private val closeAccount: CloseAccountUseCase,
    private val cancelTransaction: CancelTransactionUseCase,
    private val archivePerson: ArchivePersonUseCase,
    private val clock: Clock
) : ViewModel() {

    private val personId: Long = savedStateHandle.get<Long>(Routes.ARG_PERSON_ID) ?: 0L

    val summary: StateFlow<PersonSummary?> = ledger.observePersonSummary(personId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val entries: StateFlow<List<LedgerEntry>> = ledger.observeEntries(personId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun today(): LocalDate = LocalDate.now(clock)

    /** Borç verme/alma, kişi adına ödeme, tahsilat, borç ödeme. onResult: null = başarılı. */
    fun submitEntry(
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

    /** Kalan tutarın tamamını kapatır (alacak/borç kapatma). */
    fun close(type: TransactionType, key: String) {
        launchCatching(onResult = { err -> _messages.tryEmit(err ?: "Hesap kapatıldı.") }) {
            closeAccount(personId, type, today(), "Hesap kapatma", key)
        }
    }

    fun cancel(entryId: Long) {
        launchCatching(onResult = { err -> _messages.tryEmit(err ?: "İşlem iptal edildi.") }) {
            cancelTransaction(entryId)
        }
    }

    fun setArchived(archived: Boolean) {
        launchCatching(
            onResult = { err ->
                _messages.tryEmit(err ?: if (archived) "Kişi arşivlendi." else "Kişi arşivden çıkarıldı.")
            }
        ) {
            archivePerson(personId, archived)
        }
    }
}
