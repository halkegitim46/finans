package com.kisisel.finans.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kisisel.finans.domain.model.LedgerLine
import com.kisisel.finans.domain.model.TransactionType
import com.kisisel.finans.domain.repository.LedgerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class TransactionsState(
    val query: String = "",
    val type: TransactionType? = null,
    val showCancelled: Boolean = false,
    val lines: List<LedgerLine> = emptyList(),
    val totalKurus: Long? = null,
    val loaded: Boolean = false
)

@HiltViewModel
class TransactionsViewModel @Inject constructor(ledger: LedgerRepository) : ViewModel() {

    private val query = MutableStateFlow("")
    private val type = MutableStateFlow<TransactionType?>(null)
    private val showCancelled = MutableStateFlow(false)

    val state: StateFlow<TransactionsState> = combine(
        query, type, showCancelled, ledger.observeAllLines()
    ) { q, t, cancelled, all ->
        val filtered = filterLines(all, q, t, cancelled)
        TransactionsState(q, t, cancelled, filtered, totalForType(filtered, t), loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionsState())

    fun onQuery(v: String) = query.update { v }
    fun onType(v: TransactionType?) = type.update { v }
    fun onShowCancelled(v: Boolean) = showCancelled.update { v }
}
