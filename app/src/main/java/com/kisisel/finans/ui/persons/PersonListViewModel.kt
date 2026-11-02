package com.kisisel.finans.ui.persons

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kisisel.finans.domain.model.Person
import com.kisisel.finans.domain.model.PersonBalance
import com.kisisel.finans.domain.repository.LedgerRepository
import com.kisisel.finans.domain.repository.PersonRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.util.Locale
import javax.inject.Inject

/** balance null ise kişi arşivdedir (bakiye listede gösterilmez). */
data class PersonRow(val person: Person, val balance: PersonBalance?)

data class PersonListState(
    val query: String = "",
    val showArchived: Boolean = false,
    val rows: List<PersonRow> = emptyList(),
    val loaded: Boolean = false
)

private val TR = Locale("tr", "TR")

/** Büyük/küçük harf ve Türkçe i/ı farkına toleranslı arama. */
fun filterPersonRows(rows: List<PersonRow>, query: String): List<PersonRow> {
    val q = query.trim()
    if (q.isEmpty()) return rows
    val qTr = q.lowercase(TR)
    val qRoot = q.lowercase(Locale.ROOT)
    return rows.filter {
        val name = it.person.fullName
        name.lowercase(TR).contains(qTr) || name.lowercase(Locale.ROOT).contains(qRoot)
    }
}

@HiltViewModel
class PersonListViewModel @Inject constructor(
    ledger: LedgerRepository,
    persons: PersonRepository
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val showArchived = MutableStateFlow(false)

    val state: StateFlow<PersonListState> = combine(
        query,
        showArchived,
        ledger.observePersonSummaries(),
        persons.observeArchived()
    ) { q, archivedMode, active, archived ->
        val rows = if (archivedMode) {
            archived.map { PersonRow(it, null) }
        } else {
            active.map { PersonRow(it.person, it.balance) }
        }
        PersonListState(q, archivedMode, filterPersonRows(rows, q), loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PersonListState())

    fun onQueryChange(value: String) = query.update { value }

    fun onShowArchivedChange(value: Boolean) = showArchived.update { value }
}
