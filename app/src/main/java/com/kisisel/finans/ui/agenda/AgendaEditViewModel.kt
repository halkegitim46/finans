package com.kisisel.finans.ui.agenda

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kisisel.finans.core.DayTicker
import com.kisisel.finans.domain.model.AgendaUpdate
import com.kisisel.finans.domain.model.NewAgendaItem
import com.kisisel.finans.domain.model.Person
import com.kisisel.finans.domain.repository.AgendaRepository
import com.kisisel.finans.domain.repository.PersonRepository
import com.kisisel.finans.domain.usecase.ArchiveAgendaItemUseCase
import com.kisisel.finans.domain.usecase.CreateAgendaItemUseCase
import com.kisisel.finans.domain.usecase.SetAgendaDoneUseCase
import com.kisisel.finans.domain.usecase.UpdateAgendaItemUseCase
import com.kisisel.finans.reminder.ReminderScheduler
import com.kisisel.finans.ui.common.launchCatching
import com.kisisel.finans.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

data class AgendaEditState(
    val isEdit: Boolean = false,
    val loading: Boolean = true,
    val persons: List<Person> = emptyList(),
    val title: String = "",
    val note: String = "",
    val dateEpoch: Long = 0,
    /** Gece yarısından dakika; null = gün boyu. */
    val timeMinutes: Int? = null,
    val personId: Long? = null,
    /** null = hatırlatma yok; 0 zamanında; 60 bir saat önce; 1440 bir gün önce. */
    val remind: Int? = 0,
    val isDone: Boolean = false,
    val error: String? = null,
    val saving: Boolean = false
)

@HiltViewModel
class AgendaEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val agenda: AgendaRepository,
    personRepository: PersonRepository,
    private val createItem: CreateAgendaItemUseCase,
    private val updateItem: UpdateAgendaItemUseCase,
    private val setDone: SetAgendaDoneUseCase,
    private val archive: ArchiveAgendaItemUseCase,
    dayTicker: DayTicker,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val agendaId: Long = savedStateHandle.get<Long>(Routes.ARG_AGENDA_ID) ?: 0L

    private val _state = MutableStateFlow(
        AgendaEditState(isEdit = agendaId > 0, dateEpoch = dayTicker.today.value.toEpochDay())
    )
    val state: StateFlow<AgendaEditState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val people = personRepository.observeActive().first()
            _state.update { it.copy(persons = people) }
            if (agendaId > 0) {
                val item = agenda.getById(agendaId)
                _state.update {
                    if (item == null) {
                        it.copy(loading = false, error = "Ajanda kaydı bulunamadı.")
                    } else {
                        it.copy(
                            loading = false,
                            title = item.title,
                            note = item.note,
                            dateEpoch = item.date.toEpochDay(),
                            timeMinutes = item.time?.let { t -> t.hour * 60 + t.minute },
                            personId = item.personId,
                            remind = item.remindMinutesBefore,
                            isDone = item.isDone
                        )
                    }
                }
            } else {
                _state.update { it.copy(loading = false) }
            }
        }
    }

    private fun edit(block: AgendaEditState.() -> AgendaEditState) = _state.update { it.block().copy(error = null) }

    fun onTitle(v: String) = edit { copy(title = v) }
    fun onNote(v: String) = edit { copy(note = v) }
    fun onDate(epochDay: Long) = edit { copy(dateEpoch = epochDay) }
    fun onTime(minutes: Int?) = edit { copy(timeMinutes = minutes) }
    fun onPerson(id: Long?) = edit { copy(personId = id) }
    fun onRemind(minutes: Int?) = edit { copy(remind = minutes) }

    private fun refreshReminders() = ReminderScheduler.checkAgendaNow(context)

    fun save(onDone: () -> Unit) {
        val s = _state.value
        if (s.saving || s.loading) return
        _state.update { it.copy(saving = true, error = null) }
        val time = s.timeMinutes?.let { LocalTime.of(it / 60, it % 60) }
        val date = LocalDate.ofEpochDay(s.dateEpoch)
        launchCatching(
            onResult = { err ->
                if (err == null) {
                    refreshReminders()
                    onDone()
                } else {
                    _state.update { it.copy(saving = false, error = err) }
                }
            }
        ) {
            if (agendaId > 0) {
                updateItem(AgendaUpdate(agendaId, s.title, s.note, date, time, s.personId, s.remind))
            } else {
                createItem(NewAgendaItem(s.title, s.note, date, time, s.personId, s.remind))
            }
        }
    }

    fun toggleDone(onDone: () -> Unit) {
        val done = !_state.value.isDone
        launchCatching(
            onResult = { err ->
                if (err == null) {
                    refreshReminders()
                    onDone()
                } else {
                    _state.update { it.copy(error = err) }
                }
            }
        ) { setDone(agendaId, done) }
    }

    fun archiveItem(onDone: () -> Unit) {
        launchCatching(
            onResult = { err ->
                if (err == null) {
                    refreshReminders()
                    onDone()
                } else {
                    _state.update { it.copy(error = err) }
                }
            }
        ) { archive(agendaId) }
    }
}
