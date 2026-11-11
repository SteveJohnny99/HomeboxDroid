package it.stevej.homeboxdroid.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.stevej.homeboxdroid.data.ApiResult
import it.stevej.homeboxdroid.data.HomeboxRepository
import it.stevej.homeboxdroid.data.SecureSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: HomeboxRepository,
    private val settings: SecureSettings,
) : ViewModel() {

    data class UiState(
        val serverUrl: String = "",
        val userName: String? = null,
        val userEmail: String? = null,
        val authMode: String = "",
        val allowSelfSigned: Boolean = false,
        val testing: Boolean = false,
        val testResult: String? = null,
    )

    private val _state = MutableStateFlow(load())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private fun load() = UiState(
        serverUrl = settings.serverUrl.orEmpty(),
        userName = settings.userName,
        userEmail = settings.userEmail,
        authMode = if (settings.authMode == SecureSettings.MODE_APIKEY) "API Key" else "Email/password",
        allowSelfSigned = settings.allowSelfSigned,
    )

    fun testConnection() {
        _state.update { it.copy(testing = true, testResult = null) }
        viewModelScope.launch {
            val msg = when (val r = repo.testConnection()) {
                is ApiResult.Success -> "Connesso. Versione server: ${r.data}"
                is ApiResult.Error -> r.message
            }
            _state.update { it.copy(testing = false, testResult = msg) }
        }
    }

    fun logout() {
        viewModelScope.launch { repo.logout() }
    }

    val pendingCount: StateFlow<Int> = repo.pendingCountFlow()
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, 0)

    fun syncNow() {
        _state.update { it.copy(testResult = "Sincronizzazione in corso...") }
        viewModelScope.launch {
            repo.syncPending()
            val left = repo.pendingCount()
            _state.update {
                it.copy(testResult = if (left == 0) "Coda sincronizzata." else "Ancora $left in coda (controlla la rete).")
            }
        }
    }

    fun clearQueue() {
        viewModelScope.launch {
            repo.clearPendingQueue()
            _state.update { it.copy(testResult = "Coda svuotata.") }
        }
    }
}
