package it.stevej.homeboxdroid.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import it.stevej.homeboxdroid.data.HomeboxRepository
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
                is HomeViewModel.DetailState.Ready -> DetailContent(viewModel, d.entity)
                else -> Unit
            }
        }
    }
}

@Composable
private fun DetailContent(viewModel: HomeViewModel, entity: EntityDetail) {
    val state by viewModel.state.collectAsState()
    var editing by remember(entity.id) { mutableStateOf(false) }
    var confirmDelete by remember(entity.id) { mutableStateOf(false) }

    DetailBody(entity)

    Spacer(Modifier.height(20.dp))
    AttachmentsSection(
        viewModel = viewModel,
        entityId = entity.id,
        attachments = entity.attachments,
        enabled = !state.saving,
    )

    Spacer(Modifier.height(20.dp))
    Row(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { editing = true },
            enabled = !state.saving,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.weight(1f),
        ) {
            Icon(Icons.Outlined.Edit, contentDescription = null)
            Text("  Modifica")
        }
        Spacer(Modifier.width(12.dp))
        OutlinedButton(
            onClick = { confirmDelete = true },
            enabled = !state.saving,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.weight(1f),
        ) {
            Icon(Icons.Outlined.Delete, contentDescription = null)
            Text("  Elimina")
        }
    }

    if (editing) {
        EditEntityDialog(viewModel, entity, onClose = { editing = false })
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { if (!state.saving) confirmDelete = false },
            title = { Text(if (entity.isLocation) "Eliminare il luogo?" else "Eliminare l'oggetto?") },
            text = {
                Text(
                    if (entity.isLocation)
                        "\"${entity.name}\" e il suo contenuto verranno eliminati. L'azione non e' reversibile."
                    else
                        "\"${entity.name}\" verra' eliminato. L'azione non e' reversibile."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.deleteEntity(entity.id); confirmDelete = false },
                    enabled = !state.saving,
                ) { Text("Elimina", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }, enabled = !state.saving) { Text("Annulla") }
            },
        )
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
        DetailField("Data acquisto", e.purchaseDate?.let { formatIsoToDisplay(it) })
        DetailField("Acquistato da", e.purchaseFrom)
        DetailField(
            "Garanzia",
            when {
                e.lifetimeWarranty -> "A vita"
                e.warrantyExpires != null -> "Scade il ${formatIsoToDisplay(e.warrantyExpires)}"
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
    val showItem by viewModel.showCreateItem.collectAsState()

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
                    onClick = { onDismiss(); viewModel.openCreateItem() },
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
    if (showItem) {
        CreateItemDialog(viewModel, onClose = { viewModel.closeCreateItem() })
    }
}

@Composable
private fun CreateLocationDialog(viewModel: HomeViewModel, onClose: () -> Unit) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.saveSuccess.collect { onClose() } }
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
                onClick = { viewModel.createLocation(name, description) },
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
    val tags by viewModel.tags.collectAsState()
    var selectedTags by remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(Unit) { viewModel.saveSuccess.collect { onClose() } }

    // Precompilazione da barcode (consumata una sola volta all'apertura).
    val prefill = remember { viewModel.consumeItemPrefill() }
    var name by remember { mutableStateOf(prefill?.name ?: "") }
    var quantity by remember { mutableStateOf("1") }
    var description by remember { mutableStateOf("") }
    var manufacturer by remember { mutableStateOf(prefill?.manufacturer ?: "") }
    var model by remember { mutableStateOf(prefill?.modelNumber ?: "") }

    val defaultLoc = state.currentLocationId
    var selectedLocId by remember { mutableStateOf(defaultLoc ?: locations.firstOrNull()?.id) }
    var expanded by remember { mutableStateOf(false) }

    val selectedLocName = locations.firstOrNull { it.id == selectedLocId }?.name ?: "Nessuna"

    AlertDialog(
        onDismissRequest = { if (!state.saving) onClose() },
        title = { Text("Nuovo oggetto") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedButton(
                    onClick = { viewModel.startItemBarcodeScan() },
                    enabled = !state.saving,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.QrCodeScanner, contentDescription = null)
                    Text("  Scansiona codice a barre")
                }
                Spacer(Modifier.height(8.dp))
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
                Spacer(Modifier.height(8.dp))
                TagSelector(
                    all = tags,
                    selected = selectedTags,
                    onToggle = { id ->
                        selectedTags = if (id in selectedTags) selectedTags - id else selectedTags + id
                    },
                    onCreate = { nm -> viewModel.createTag(nm) { t -> selectedTags = selectedTags + t.id } },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val q = quantity.replace(',', '.').toDoubleOrNull() ?: 1.0
                    viewModel.createItem(name, q, description, manufacturer, model, selectedLocId, selectedTags.toList())
                    // Chiusura su saveSuccess.
                },
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
private fun EditEntityDialog(viewModel: HomeViewModel, entity: EntityDetail, onClose: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val locations = remember { viewModel.locations }
    val isLocation = entity.isLocation
    val tags by viewModel.tags.collectAsState()
    var selectedTags by remember(entity.id) { mutableStateOf(entity.tagIds.toSet()) }

    // Chiudi la scheda SOLO quando il salvataggio va a buon fine: su errore
    // resta aperta con tutte le modifiche.
    LaunchedEffect(entity.id) { viewModel.saveSuccess.collect { onClose() } }

    var name by remember { mutableStateOf(entity.name) }
    var description by remember { mutableStateOf(entity.description ?: "") }
    var quantity by remember { mutableStateOf(entity.quantity?.let { formatQty(it) } ?: "1") }
    var manufacturer by remember { mutableStateOf(entity.manufacturer ?: "") }
    var model by remember { mutableStateOf(entity.modelNumber ?: "") }
    var serial by remember { mutableStateOf(entity.serialNumber ?: "") }
    var notes by remember { mutableStateOf(entity.notes ?: "") }
    var price by remember { mutableStateOf(entity.purchasePrice?.let { "%.2f".format(it) } ?: "") }
    var purchaseDate by remember { mutableStateOf(entity.purchaseDate ?: "") }
    var purchaseFrom by remember { mutableStateOf(entity.purchaseFrom ?: "") }
    var warranty by remember { mutableStateOf(entity.warrantyExpires ?: "") }
    var lifetime by remember { mutableStateOf(entity.lifetimeWarranty) }

    var selectedId by remember { mutableStateOf(if (isLocation) entity.parentId else entity.locationId) }
    var expanded by remember { mutableStateOf(false) }

    val options: List<Pair<String?, String>> = buildList {
        if (isLocation) add(null to "Nessuno (radice)")
        locations.filter { it.id != entity.id }.forEach { add(it.id to ("  ".repeat(it.depth) + it.name)) }
    }
    val selectedLabel = options.firstOrNull { it.first == selectedId }?.second
        ?: if (isLocation) "Nessuno (radice)" else "Nessuna"

    AlertDialog(
        onDismissRequest = { if (!state.saving) onClose() },
        title = { Text(if (isLocation) "Modifica luogo" else "Modifica oggetto") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Nome") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))

                if (!isLocation) {
                    OutlinedTextField(
                        value = quantity, onValueChange = { quantity = it },
                        label = { Text("Quantita'") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                }

                if (options.isNotEmpty()) {
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                        OutlinedTextField(
                            value = selectedLabel, onValueChange = {}, readOnly = true,
                            label = { Text(if (isLocation) "Luogo superiore" else "Posizione") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                        )
                        ExposedDropdownMenu(
                            expanded = expanded, onDismissRequest = { expanded = false },
                        ) {
                            options.forEach { (id, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = { selectedId = id; expanded = false },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                if (!isLocation) {
                    OutlinedTextField(
                        value = manufacturer, onValueChange = { manufacturer = it },
                        label = { Text("Produttore") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = model, onValueChange = { model = it },
                        label = { Text("Modello") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = serial, onValueChange = { serial = it },
                        label = { Text("Numero seriale") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = price, onValueChange = { price = it },
                        label = { Text("Prezzo d'acquisto") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    DateField(
                        label = "Data acquisto",
                        isoValue = purchaseDate,
                        onChange = { purchaseDate = it ?: "" },
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = purchaseFrom, onValueChange = { purchaseFrom = it },
                        label = { Text("Acquistato da") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Garanzia a vita", modifier = Modifier.weight(1f))
                        Switch(checked = lifetime, onCheckedChange = { lifetime = it })
                    }
                    if (!lifetime) {
                        DateField(
                            label = "Scadenza garanzia",
                            isoValue = warranty,
                            onChange = { warranty = it ?: "" },
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = notes, onValueChange = { notes = it },
                        label = { Text("Note") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    TagSelector(
                        all = tags,
                        selected = selectedTags,
                        onToggle = { id ->
                            selectedTags = if (id in selectedTags) selectedTags - id else selectedTags + id
                        },
                        onCreate = { nm -> viewModel.createTag(nm) { t -> selectedTags = selectedTags + t.id } },
                    )
                    Spacer(Modifier.height(8.dp))
                }

                OutlinedTextField(
                    value = description, onValueChange = { description = it },
                    label = { Text("Descrizione") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val q = quantity.replace(',', '.').toDoubleOrNull()
                    val p = price.replace(',', '.').toDoubleOrNull()
                    val edit = HomeboxRepository.EntityEdit(
                        name = name,
                        description = description,
                        notes = if (isLocation) null else notes,
                        quantity = if (isLocation) null else q,
                        manufacturer = if (isLocation) null else manufacturer,
                        modelNumber = if (isLocation) null else model,
                        serialNumber = if (isLocation) null else serial,
                        purchasePrice = if (isLocation) null else p,
                        purchaseDate = if (isLocation) null else purchaseDate,
                        purchaseFrom = if (isLocation) null else purchaseFrom,
                        warrantyExpires = if (isLocation) null else warranty,
                        lifetimeWarranty = if (isLocation) false else lifetime,
                        locationId = if (isLocation) null else selectedId,
                        parentId = selectedId,
                        tagIds = if (isLocation) emptyList() else selectedTags.toList(),
                    )
                    viewModel.updateEntity(entity.id, edit)
                    // La chiusura avviene su saveSuccess: su errore la scheda resta.
                },
                enabled = name.isNotBlank() && !state.saving,
            ) { Text("Salva") }
        },
        dismissButton = {
            TextButton(onClick = onClose, enabled = !state.saving) { Text("Annulla") }
        },
    )
}
