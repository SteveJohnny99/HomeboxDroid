package it.stevej.homeboxdroid.ui.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import it.stevej.homeboxdroid.ui.home.CreateDialogs
import it.stevej.homeboxdroid.ui.home.EntityDetailSheet
import it.stevej.homeboxdroid.ui.home.HomeViewModel
import it.stevej.homeboxdroid.ui.home.BrowseScreen
import it.stevej.homeboxdroid.ui.home.StatsScreen
import it.stevej.homeboxdroid.ui.settings.SettingsScreen
import kotlinx.coroutines.launch

enum class MainTab(val label: String) {
    BROWSE("Sfoglia"), STATS("Statistiche"), SETTINGS("Impostazioni")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold(viewModel: HomeViewModel = hiltViewModel()) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(MainTab.BROWSE) }
    var showCreateChooser by remember { mutableStateOf(false) }

    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    // Messaggi (creazioni, errori di salvataggio) come snackbar.
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(24.dp))
                Text(
                    "HomeboxDroid",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
                Spacer(Modifier.height(12.dp))
                DrawerRow("Sfoglia", Icons.Outlined.Inventory2, tab == MainTab.BROWSE) {
                    tab = MainTab.BROWSE; scope.launch { drawerState.close() }
                }
                DrawerRow("Statistiche", Icons.Outlined.BarChart, tab == MainTab.STATS) {
                    tab = MainTab.STATS; scope.launch { drawerState.close() }
                }
                DrawerRow("Impostazioni", Icons.Outlined.Settings, tab == MainTab.SETTINGS) {
                    tab = MainTab.SETTINGS; scope.launch { drawerState.close() }
                }
            }
        },
    ) {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                TopAppBar(
                    title = { Text(tab.label) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Outlined.Menu, contentDescription = "Menu")
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
            floatingActionButton = {
                if (tab == MainTab.BROWSE) {
                    ExtendedFloatingActionButton(
                        onClick = { showCreateChooser = true },
                        icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                        text = { Text("Aggiungi") },
                    )
                }
            },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { inner ->
            when (tab) {
                MainTab.BROWSE -> BrowseScreen(viewModel, Modifier.padding(inner))
                MainTab.STATS -> StatsScreen(viewModel, Modifier.padding(inner))
                MainTab.SETTINGS -> SettingsScreen(Modifier.padding(inner))
            }
        }
    }

    // Scheda dettaglio entita'.
    EntityDetailSheet(viewModel)

    // Scelta tipo + dialog di creazione.
    CreateDialogs(
        viewModel = viewModel,
        show = showCreateChooser,
        onDismiss = { showCreateChooser = false },
    )

    // Scanner barcode a schermo intero (in creazione oggetto). Il dialog e'
    // chiuso durante la scansione, quindi niente conflitti di z-order.
    val scannerForItem by viewModel.scannerForItem.collectAsState()
    if (scannerForItem) {
        androidx.compose.material3.Surface(
            modifier = Modifier.fillMaxSize(),
            color = androidx.compose.ui.graphics.Color.Black,
        ) {
            it.stevej.homeboxdroid.ui.scan.BarcodeScannerScreen(
                title = "Scansiona il codice a barre",
                onResult = { code -> viewModel.onItemBarcode(code) },
                onCancel = { viewModel.cancelScan() },
            )
        }
    }
}

@Composable
private fun DrawerRow(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    NavigationDrawerItem(
        label = { Text(label) },
        selected = selected,
        icon = { Icon(icon, contentDescription = null) },
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 12.dp),
    )
}
