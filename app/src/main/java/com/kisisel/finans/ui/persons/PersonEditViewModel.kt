package com.kisisel.finans.ui.persons

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kisisel.finans.core.Money
import com.kisisel.finans.domain.FinanceException
import com.kisisel.finans.domain.repository.PersonRepository
import com.kisisel.finans.domain.usecase.CreatePersonUseCase
import com.kisisel.finans.domain.usecase.UpdatePersonUseCase
import com.kisisel.finans.ui.common.launchCatching
import com.kisisel.finans.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PersonEditState(
    val isEdit: Boolean = false,
    val loading: Boolean = false,
    val name: String = "",
    val phone: String = "",
    val note: String = "",
    val openingText: String = "",
    val openingIsDebt: Boolean = false,
    val error: String? = null,
    val saving: Boolean = false
)

@HiltViewModel
class PersonEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val persons: PersonRepository,
    private val createPerson: CreatePersonUseCase,
    private val updatePerson: UpdatePersonUseCase
) : ViewModel() {

    private val personId: Long = savedStateHandle.get<Long>(Routes.ARG_PERSON_ID) ?: 0L

    private val _state = MutableStateFlow(PersonEditState(isEdit = personId > 0, loading = personId > 0))
    val state: StateFlow<PersonEditState> = _state.asStateFlow()

    init {
        if (personId > 0) {
            viewModelScope.launch {
                val p = persons.getById(personId)
                _state.update {
                    if (p == null) {
                        it.copy(loading = false, error = "Kişi bulunamadı.")
                    } else {
                        it.copy(loading = false, name = p.fullName, phone = p.phone.orEmpty(), note = p.note)
                    }
                }
            }
        }
    }

    fun onName(v: String) = _state.update { it.copy(name = v, error = null) }
    fun onPhone(v: String) = _state.update { it.copy(phone = v, error = null) }
    fun onNote(v: String) = _state.update { it.copy(note = v, error = null) }
    fun onOpening(v: String) = _state.update { it.copy(openingText = v, error = null) }
    fun onOpeningIsDebt(v: Boolean) = _state.update { it.copy(openingIsDebt = v) }

    fun save(onSaved: () -> Unit) {
        val s = _state.value
        if (s.saving || s.loading) return
        _state.update { it.copy(saving = true, error = null) }
        launchCatching(
            onResult = { err ->
                if (err == null) onSaved() else _state.update { it.copy(saving = false, error = err) }
            }
        ) {
            val phone = s.phone.ifBlank { null }
            if (personId > 0) {
                updatePerson(personId, s.name, phone, s.note)
            } else {
                val opening = if (s.openingText.isBlank()) {
                    0L
                } else {
                    Money.parse(s.openingText)
                        ?: throw FinanceException.InvalidInput("Başlangıç tutarı geçersiz (ör. 1.500,50).")
                }
                createPerson(s.name, phone, s.note, if (s.openingIsDebt) -opening else opening)
            }
        }
    }
}
