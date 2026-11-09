package it.stevej.homeboxdroid.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.compose.runtime.collectAsState
import dagger.hilt.android.lifecycle.HiltViewModel
import it.stevej.homeboxdroid.data.SecureSettings
import it.stevej.homeboxdroid.ui.main.MainScaffold
import it.stevej.homeboxdroid.ui.onboarding.OnboardingScreen
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class AppViewModel @Inject constructor(
    settings: SecureSettings,
) : ViewModel() {
    val configured: StateFlow<Boolean> = settings.configured
}

/**
 * Root dell'app: decide tra onboarding e area principale in base alla presenza
 * di una sessione valida. Il flag si aggiorna da solo dopo login/logout.
 */
@Composable
fun AppRoot(viewModel: AppViewModel) {
    val configured by viewModel.configured.collectAsState()
    if (configured) {
        MainScaffold()
    } else {
        OnboardingScreen()
    }
}
