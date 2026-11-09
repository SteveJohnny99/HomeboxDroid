package it.stevej.homeboxdroid.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import it.stevej.homeboxdroid.domain.EntityDetail

// ---------------------------------------------------------------------------
// Dettaglio entita'
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntityDetailSheet(viewModel: HomeViewModel) {
    val detail by viewModel.detail.collectAsState()
    if (detail is HomeViewModel.DetailState.Hidden) return

    ModalBottomSheet(onDismissRequest = { viewModel.closeDetail() }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 40.dp),
        ) {
            when (val d = detail) {
                is HomeViewModel.DetailState.Loading ->
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                is HomeViewModel.DetailState.Error ->
                    Text(d.message, color = MaterialTheme.colorScheme.error)
                is HomeViewModel.DetailState.Ready -> DetailBody(d.entity)
                else -> Unit
            }
        }
    }
}

@Composable
private fun DetailBody(e: EntityDetail) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (e.isLocation) Icons.Outlined.Folder else Icons.Outlined.Inventory2,
            contentDescription = null,
            tint = if (e.isLocation) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
        )
        Spacer(Modifier.height(0.dp))
        Text(
            e.name,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
    Spacer(Modifier.height(4.dp))
    e.typeName?.let {
        Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(Modifier.height(16.dp))

    if (!e.isLocation) {
        DetailField("Quantita'", e.quantity?.let { formatQty(it) })
        DetailField("Posizione", e.locationName)
        DetailField("Produttore", e.manufacturer)
        DetailField("Modello", e.modelNumber)
        DetailField("Numero seriale", e.serialNumber)
        DetailField("Asset ID", e.assetId)
        DetailField("Prezzo d'acquisto", e.purchasePrice?.let { "%.2f".format(it) })
        DetailField("Data acquisto", e.purchaseDate)
        DetailField("Acquistato da", e.purchaseFrom)
        DetailField(
            "Garanzia",
            when {
                e.lifetimeWarranty -> "A vita"
                e.warrantyExpires != null -> "Scade il ${e.warrantyExpires}"
                else -> null
            },
        )
    } else {
        DetailField("Elementi contenuti", e.childrenCount.toString())
    }
    DetailField("Descrizione", e.description)
    DetailField("Note", e.notes)
    if (e.tags.isNotEmpty()) DetailField("Tag", e.tags.joinToString(", "))
}

@Composable
private fun DetailField(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Column(Modifier.padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun formatQty(q: Double): String =
    if (q == q.toLong().toDouble()) q.toLong().toString() else q.toString()

// ---------------------------------------------------------------------------
// Creazione: scelta tipo + dialog luogo/oggetto
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateDialogs(viewModel: HomeViewModel, show: Boolean, onDismiss: () -> Unit) {
    var openLocation by remember { mutableStateOf(false) }
    var openItem by remember { mutableStateOf(false) }

    if (show) {
        ModalBottomSheet(onDismissRequest = onDismiss) {
            Column(Modifier.fillMaxWidth().padding(24.dp)) {
                Text("Cosa vuoi aggiungere?", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = { onDismiss(); openLocation = true },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.Folder, contentDescription = null)
                    Spacer(Modifier.height(0.dp))
                    Text("  Nuovo luogo")
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { onDismiss(); openItem = true },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.Inventory2, contentDescription = null)
                    Spacer(Modifier.height(0.dp))
                    Text("  Nuovo oggetto")
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (openLocation) {
        CreateLocationDialog(viewModel, onClose = { openLocation = false })
    }
    if (openItem) {
        CreateItemDialog(viewModel, onClose = { openItem = false })
    }
}

@Composable
private fun CreateLocationDialog(viewModel: HomeViewModel, onClose: () -> Unit) {
    val state by viewModel.state.collectAsState()
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    val parentName = state.crumbs.lastOrNull()?.takeIf { it.id.isNotBlank() }?.name

    AlertDialog(
        onDismissRequest = { if (!state.saving) onClose() },
        title = { Text("Nuovo luogo") },
        text = {
            Column {
                parentName?.let {
                    Text(
                        "Dentro: $it",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Nome") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = description, onValueChange = { description = it },
                    label = { Text("Descrizione (opzionale)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { viewModel.createLocation(name, description); onClose() },
                enabled = name.isNotBlank() && !state.saving,
            ) { Text("Crea") }
        },
        dismissButton = {
            TextButton(onClick = onClose, enabled = !state.saving) { Text("Annulla") }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateItemDialog(viewModel: HomeViewModel, onClose: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val locations = remember { viewModel.locations }

    var name by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("1") }
    var description by remember { mutableStateOf("") }
    var manufacturer by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }

    val defaultLoc = state.currentLocationId
    var selectedLocId by remember { mutableStateOf(defaultLoc ?: locations.firstOrNull()?.id) }
    var expanded by remember { mutableStateOf(false) }

    val selectedLocName = locations.firstOrNull { it.id == selectedLocId }?.name ?: "Nessuna"

    AlertDialog(
        onDismissRequest = { if (!state.saving) onClose() },
        title = { Text("Nuovo oggetto") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Nome") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = quantity, onValueChange = { quantity = it },
                    label = { Text("Quantita'") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))

                if (locations.isNotEmpty()) {
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = it },
                    ) {
                        OutlinedTextField(
                            value = selectedLocName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Posizione") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                        ) {
                            locations.forEach { loc ->
                                DropdownMenuItem(
                                    text = { Text("  ".repeat(loc.depth) + loc.name) },
                                    onClick = { selectedLocId = loc.id; expanded = false },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                OutlinedTextField(
                    value = manufacturer, onValueChange = { manufacturer = it },
                    label = { Text("Produttore (opzionale)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = model, onValueChange = { model = it },
                    label = { Text("Modello (opzionale)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = description, onValueChange = { description = it },
                    label = { Text("Descrizione (opzionale)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val q = quantity.replace(',', '.').toDoubleOrNull() ?: 1.0
                    viewModel.createItem(name, q, description, manufacturer, model, selectedLocId)
                    onClose()
                },
                enabled = name.isNotBlank() && !state.saving,
            ) { Text("Crea") }
        },
        dismissButton = {
            TextButton(onClick = onClose, enabled = !state.saving) { Text("Annulla") }
        },
    )
}
