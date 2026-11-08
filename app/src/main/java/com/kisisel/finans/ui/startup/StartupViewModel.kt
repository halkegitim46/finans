package com.kisisel.finans.ui.startup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kisisel.finans.data.local.DatabaseHealthChecker
import com.kisisel.finans.data.local.DbHealth
import com.kisisel.finans.security.AppResetter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface StartupState {
    data object Checking : StartupState
    data object Ready : StartupState
    data class Failed(val problem: DbHealth.Problem) : StartupState
}

@HiltViewModel
class StartupViewModel @Inject constructor(
    private val checker: DatabaseHealthChecker,
    private val resetter: AppResetter
) : ViewModel() {

    private val _state = MutableStateFlow<StartupState>(StartupState.Checking)
    val state: StateFlow<StartupState> = _state.asStateFlow()

    init {
        runCheck()
    }

    fun retry() {
        _state.value = StartupState.Checking
        runCheck()
    }

    /** Yalnızca kullanıcı "SİL" yazarak onayladıktan sonra çağrılır. */
    fun resetAll() {
        viewModelScope.launch(Dispatchers.IO) { resetter.wipeAndRestart() }
    }

    private fun runCheck() {
        viewModelScope.launch {
            _state.value = when (val h = checker.check()) {
                DbHealth.Ok -> StartupState.Ready
                is DbHealth.Problem -> StartupState.Failed(h)
            }
        }
    }
}
