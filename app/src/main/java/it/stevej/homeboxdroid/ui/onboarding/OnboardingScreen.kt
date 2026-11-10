package it.stevej.homeboxdroid.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import it.stevej.homeboxdroid.ui.scan.BarcodeScannerScreen

/**
 * Onboarding funzionante (M1): URL + API Key (predefinito) oppure email/password.
 * Test connessione e login reali. La scansione QR arriva in una versione successiva.
 */
@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel = hiltViewModel()) {
    val s by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var showScanner by remember { mutableStateOf(false) }

    if (showScanner) {
        BarcodeScannerScreen(
            title = "Scansiona QR di configurazione",
            onResult = { value -> viewModel.applyScanned(value); showScanner = false },
            onCancel = { showScanner = false },
        )
        return
    }

    val gradient = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.surfaceVariant,
        )
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { inner ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .background(gradient)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(top = 48.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("HomeboxDroid", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Collega il tuo server Homebox per iniziare.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = s.serverUrl,
                    onValueChange = viewModel::onUrl,
                    label = { Text("URL del server") },
                    placeholder = { Text("https://box.esempio.it") },
                    singleLine = true,
                    enabled = !s.loading,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )

                if (!s.emailMode) {
                    OutlinedTextField(
                        value = s.apiKey,
                        onValueChange = viewModel::onApiKey,
                        label = { Text("API Key (hb_...)") },
                        singleLine = true,
                        enabled = !s.loading,
                        visualTransformation = PasswordVisualTransformation(),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedButton(
                        onClick = { showScanner = true },
                        enabled = !s.loading,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Outlined.QrCodeScanner, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Scansiona QR")
                    }
                } else {
                    OutlinedTextField(
                        value = s.email,
                        onValueChange = viewModel::onEmail,
                        label = { Text("Email") },
                        singleLine = true,
                        enabled = !s.loading,
                        shape = RoundedCornerShape(16.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = s.password,
                        onValueChange = viewModel::onPassword,
                        label = { Text("Password") },
                        singleLine = true,
                        enabled = !s.loading,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // Opzione avanzata: TLS self-signed (default off).
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Accetta certificati self-signed", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Solo per LAN/CA interne. Rischioso: lascia OFF con HTTPS valido.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = s.allowSelfSigned,
                        onCheckedChange = viewModel::onSelfSigned,
                        enabled = !s.loading,
                    )
                }

                AnimatedVisibility(visible = s.error != null) {
                    Text(
                        text = s.error ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Button(
                    onClick = viewModel::connect,
                    enabled = s.canSubmit,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (s.loading) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.height(20.dp).width(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text("Connetti")
                    }
                }

                TextButton(
                    onClick = viewModel::toggleMode,
                    enabled = !s.loading,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text(
                        if (s.emailMode) "Usa invece una API Key"
                        else "Accedi con email e password"
                    )
                }
            }
        }
    }
}

