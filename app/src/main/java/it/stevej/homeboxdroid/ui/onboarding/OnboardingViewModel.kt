package it.stevej.homeboxdroid.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.stevej.homeboxdroid.data.ApiResult
import it.stevej.homeboxdroid.data.HomeboxRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val repo: HomeboxRepository,
) : ViewModel() {

    data class UiState(
        val serverUrl: String = "",
        val apiKey: String = "",
        val email: String = "",
        val password: String = "",
        val emailMode: Boolean = false,
        val allowSelfSigned: Boolean = false,
        val loading: Boolean = false,
        val error: String? = null,
    ) {
        val canSubmit: Boolean
            get() = serverUrl.isNotBlank() && !loading &&
                if (emailMode) email.isNotBlank() && password.isNotBlank()
                else apiKey.isNotBlank()
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun onUrl(v: String) = _state.update { it.copy(serverUrl = v, error = null) }
    fun onApiKey(v: String) = _state.update { it.copy(apiKey = v, error = null) }
    fun onEmail(v: String) = _state.update { it.copy(email = v, error = null) }
    fun onPassword(v: String) = _state.update { it.copy(password = v, error = null) }
    fun onSelfSigned(v: Boolean) = _state.update { it.copy(allowSelfSigned = v) }
    fun toggleMode() = _state.update { it.copy(emailMode = !it.emailMode, error = null) }

    fun connect() {
        val s = _state.value
        if (!s.canSubmit) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val result = if (s.emailMode) {
                repo.loginWithPassword(s.serverUrl, s.email, s.password, s.allowSelfSigned)
            } else {
                repo.loginWithApiKey(s.serverUrl, s.apiKey, s.allowSelfSigned)
            }
            when (result) {
                is ApiResult.Error -> _state.update { it.copy(loading = false, error = result.message) }
                is ApiResult.Success -> _state.update { it.copy(loading = false, error = null) }
                // Success: SecureSettings.configured diventa true e AppRoot reinstrada.
            }
        }
    }
}
