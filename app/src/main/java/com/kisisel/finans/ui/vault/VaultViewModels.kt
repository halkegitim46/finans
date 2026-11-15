package com.kisisel.finans.ui.vault

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kisisel.finans.domain.model.NewVaultItem
import com.kisisel.finans.domain.model.VaultItem
import com.kisisel.finans.domain.model.VaultSummary
import com.kisisel.finans.domain.model.VaultUpdate
import com.kisisel.finans.domain.repository.VaultRepository
import com.kisisel.finans.domain.usecase.ArchiveVaultItemUseCase
import com.kisisel.finans.domain.usecase.CreateVaultItemUseCase
import com.kisisel.finans.domain.usecase.UpdateVaultItemUseCase
import com.kisisel.finans.ui.common.launchCatching
import com.kisisel.finans.ui.navigation.Routes
import com.kisisel.finans.vault.VaultAccess
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.kisisel.finans.core.TurkishLocale


// ---------------- liste ----------------
data class VaultListState(
    val items: List<VaultSummary> = emptyList(),
    val query: String = "",
    val total: Int = 0,
    val loaded: Boolean = false
)

@HiltViewModel
class VaultListViewModel @Inject constructor(
    repo: VaultRepository,
    private val access: VaultAccess
) : ViewModel() {
    private val query = MutableStateFlow("")

    val state: StateFlow<VaultListState> = combine(repo.observeSummaries(), query) { items, q ->
        val needle = q.trim().lowercase(TurkishLocale)
        val shown = if (needle.isEmpty()) items else items.filter {
            it.title.lowercase(TurkishLocale).contains(needle) || it.username.lowercase(TurkishLocale).contains(needle) || it.url.lowercase(TurkishLocale).contains(needle)
        }
        VaultListState(shown, q, items.size, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VaultListState())

    fun setQuery(q: String) {
        query.value = q
    }

    fun lock() = access.lock()
    fun touch() = access.touch()
}

// ---------------- ayrıntı ----------------
data class VaultItemState(val loading: Boolean = true, val item: VaultItem? = null, val error: String? = null)

@HiltViewModel
class VaultItemViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repo: VaultRepository,
    private val archiveItem: ArchiveVaultItemUseCase,
    private val access: VaultAccess
) : ViewModel() {
    private val id: Long = savedStateHandle.get<Long>(Routes.ARG_VAULT_ID) ?: 0L
    private val error = MutableStateFlow<String?>(null)

    /** Kayıt değişince/arşivlenince kendiliğinden güncellenir; arşivlenmiş/yok ise item = null. */
    val state: StateFlow<VaultItemState> = combine(repo.observeById(id), error) { item, e ->
        VaultItemState(loading = false, item = item, error = e)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VaultItemState())

    fun touch() = access.touch()

    fun archive() {
        launchCatching(onResult = { err -> if (err != null) error.value = err }) { archiveItem(id) }
    }
}

// ---------------- ekle / düzenle ----------------
data class VaultEditState(
    val isEdit: Boolean = false,
    val loading: Boolean = true,
    val title: String = "",
    val username: String = "",
    val secret: String = "",
    val url: String = "",
    val note: String = "",
    val error: String? = null,
    val saving: Boolean = false
) {
    // Şifre yanlışlıkla log'a düşmesin.
    override fun toString(): String = "VaultEditState(isEdit=$isEdit, title=$title, secret=***)"
}

@HiltViewModel
class VaultEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repo: VaultRepository,
    private val createItem: CreateVaultItemUseCase,
    private val updateItem: UpdateVaultItemUseCase,
    private val access: VaultAccess
) : ViewModel() {
    private val id: Long = savedStateHandle.get<Long>(Routes.ARG_VAULT_ID) ?: 0L

    private val _state = MutableStateFlow(VaultEditState(isEdit = id > 0))
    val state: StateFlow<VaultEditState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            if (id > 0) {
                val item = repo.getById(id)
                _state.update {
                    if (item == null) {
                        it.copy(loading = false, error = "Kasa kaydı bulunamadı.")
                    } else {
                        it.copy(
                            loading = false, title = item.title, username = item.username,
                            secret = item.secret, url = item.url, note = item.note
                        )
                    }
                }
            } else {
                _state.update { it.copy(loading = false) }
            }
        }
    }

    private fun edit(block: VaultEditState.() -> VaultEditState) {
        access.touch()
        _state.update { it.block().copy(error = null) }
    }

    fun onTitle(v: String) = edit { copy(title = v) }
    fun onUsername(v: String) = edit { copy(username = v) }
    fun onSecret(v: String) = edit { copy(secret = v) }
    fun onUrl(v: String) = edit { copy(url = v) }
    fun onNote(v: String) = edit { copy(note = v) }

    fun save(onDone: () -> Unit) {
        val s = _state.value
        if (s.saving || s.loading) return
        _state.update { it.copy(saving = true, error = null) }
        launchCatching(
            onResult = { err ->
                if (err == null) {
                    access.touch()
                    onDone()
                } else {
                    _state.update { it.copy(saving = false, error = err) }
                }
            }
        ) {
            if (id > 0) {
                updateItem(VaultUpdate(id, s.title, s.username, s.secret, s.url, s.note))
            } else {
                createItem(NewVaultItem(s.title, s.username, s.secret, s.url, s.note))
            }
        }
    }
}
