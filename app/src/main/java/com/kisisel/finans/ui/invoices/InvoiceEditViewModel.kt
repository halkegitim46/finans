package com.kisisel.finans.ui.invoices

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kisisel.finans.core.Money
import com.kisisel.finans.domain.FinanceException
import com.kisisel.finans.domain.model.InvoiceType
import com.kisisel.finans.domain.model.InvoiceUpdate
import com.kisisel.finans.domain.model.NewInvoice
import com.kisisel.finans.domain.model.Person
import com.kisisel.finans.domain.model.Recurrence
import com.kisisel.finans.domain.repository.InvoiceRepository
import com.kisisel.finans.domain.repository.PersonRepository
import com.kisisel.finans.domain.usecase.ArchiveInvoiceUseCase
import com.kisisel.finans.domain.usecase.CreateInvoiceUseCase
import com.kisisel.finans.domain.usecase.UpdateInvoiceUseCase
import com.kisisel.finans.ui.common.launchCatching
import com.kisisel.finans.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class InvoiceEditState(
    val isEdit: Boolean = false,
    val loading: Boolean = false,
    val persons: List<Person> = emptyList(),
    val name: String = "",
    val type: InvoiceType = InvoiceType.ELECTRICITY,
    val personId: Long? = null,
    val isFixed: Boolean = true,
    val amountText: String = "",
    val recurrence: Recurrence = Recurrence.MONTHLY,
    val customDaysText: String = "",
    val firstDueDay: Long = 0,
    val note: String = "",
    val remindersEnabled: Boolean = true,
    val reminderDays: Set<Int> = setOf(7, 3, 1, 0),
    val overdueEvery: Int = 1,
    val error: String? = null,
    val saving: Boolean = false
)

@HiltViewModel
class InvoiceEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val invoices: InvoiceRepository,
    personRepository: PersonRepository,
    private val createInvoice: CreateInvoiceUseCase,
    private val updateInvoice: UpdateInvoiceUseCase,
    private val archiveInvoice: ArchiveInvoiceUseCase,
    clock: Clock
) : ViewModel() {

    private val templateId: Long = savedStateHandle.get<Long>(Routes.ARG_TEMPLATE_ID) ?: 0L

    private val _state = MutableStateFlow(
        InvoiceEditState(
            isEdit = templateId > 0,
            loading = true,
            firstDueDay = LocalDate.now(clock).toEpochDay()
        )
    )
    val state: StateFlow<InvoiceEditState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val people = personRepository.observeActive().first()
            _state.update { it.copy(persons = people) }
            if (templateId > 0) {
                val t = invoices.getTemplate(templateId)
                _state.update {
                    if (t == null) {
                        it.copy(loading = false, error = "Fatura bulunamadı.")
                    } else {
                        it.copy(
                            loading = false,
                            name = t.name,
                            type = t.type,
                            personId = t.personId,
                            isFixed = t.isAmountFixed,
                            amountText = t.amountKurus?.let { k -> Money.format(k, withSymbol = false) } ?: "",
                            recurrence = t.recurrence,
                            customDaysText = t.customIntervalDays?.toString() ?: "",
                            firstDueDay = t.firstDueDate.toEpochDay(),
                            note = t.note,
                            remindersEnabled = t.remindersEnabled,
                            reminderDays = t.reminderDays,
                            overdueEvery = t.overdueReminderEveryDays
                        )
                    }
                }
            } else {
                _state.update { it.copy(loading = false) }
            }
        }
    }

    fun onName(v: String) = edit { copy(name = v) }
    fun onType(v: InvoiceType) = edit { copy(type = v) }
    fun onPerson(v: Long?) = edit { copy(personId = v) }
    fun onFixed(v: Boolean) = edit { copy(isFixed = v) }
    fun onAmount(v: String) = edit { copy(amountText = v) }
    fun onRecurrence(v: Recurrence) = edit { copy(recurrence = v) }
    fun onCustomDays(v: String) = edit { copy(customDaysText = v.filter { it.isDigit() }.take(3)) }
    fun onFirstDue(v: Long) = edit { copy(firstDueDay = v) }
    fun onNote(v: String) = edit { copy(note = v) }
    fun onRemindersEnabled(v: Boolean) = edit { copy(remindersEnabled = v) }
    fun onOverdueEvery(v: Int) = edit { copy(overdueEvery = v) }
    fun onToggleReminderDay(day: Int, on: Boolean) =
        edit { copy(reminderDays = if (on) reminderDays + day else reminderDays - day) }

    private fun edit(block: InvoiceEditState.() -> InvoiceEditState) =
        _state.update { it.block().copy(error = null) }

    fun save(onDone: () -> Unit) {
        val s = _state.value
        if (s.saving || s.loading) return
        _state.update { it.copy(saving = true, error = null) }
        launchCatching(
            onResult = { err -> if (err == null) onDone() else _state.update { it.copy(saving = false, error = err) } }
        ) {
            val amount = if (s.amountText.isBlank()) {
                null
            } else {
                Money.parse(s.amountText) ?: throw FinanceException.InvalidInput("Tutar geçersiz (ör. 1.500,50).")
            }
            if (templateId > 0) {
                updateInvoice(
                    InvoiceUpdate(
                        id = templateId, personId = s.personId, name = s.name, type = s.type,
                        amountKurus = amount, isAmountFixed = s.isFixed, note = s.note,
                        remindersEnabled = s.remindersEnabled, reminderDays = s.reminderDays,
                        overdueReminderEveryDays = s.overdueEvery
                    )
                )
            } else {
                createInvoice(
                    NewInvoice(
                        personId = s.personId, name = s.name, type = s.type, amountKurus = amount,
                        isAmountFixed = s.isFixed, recurrence = s.recurrence,
                        customIntervalDays = s.customDaysText.toIntOrNull(),
                        firstDueDate = LocalDate.ofEpochDay(s.firstDueDay), note = s.note,
                        remindersEnabled = s.remindersEnabled, reminderDays = s.reminderDays,
                        overdueReminderEveryDays = s.overdueEvery
                    )
                )
            }
        }
    }

    fun archive(onDone: () -> Unit) {
        launchCatching(
            onResult = { err -> if (err == null) onDone() else _state.update { it.copy(error = err) } }
        ) { archiveInvoice(templateId) }
    }
}
