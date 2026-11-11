package it.stevej.homeboxdroid.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import it.stevej.homeboxdroid.domain.CustomField
import it.stevej.homeboxdroid.domain.MaintenanceEntry

/** Valore leggibile di un campo personalizzato. */
fun customFieldValue(f: CustomField): String = when (f.type.lowercase()) {
    "boolean" -> if (f.booleanValue == true) "Si" else "No"
    "number" -> (f.numberValue ?: 0).toString()
    "time" -> f.textValue?.take(10) ?: ""
    else -> f.textValue ?: ""
}

// ---------------------------------------------------------------------------
// Editor campi personalizzati (usato nel dialog di modifica)
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomFieldsEditor(
    fields: List<CustomField>,
    onChange: (List<CustomField>) -> Unit,
) {
    var newName by remember { mutableStateOf("") }
    var newType by remember { mutableStateOf("text") }
    var typeExpanded by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth()) {
        Text(
            "Campi personalizzati",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        fields.forEachIndexed { index, f ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    when (f.type.lowercase()) {
                        "boolean" -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(f.name, modifier = Modifier.weight(1f))
                            Switch(
                                checked = f.booleanValue == true,
                                onCheckedChange = { v ->
                                    onChange(fields.toMutableList().also { it[index] = f.copy(booleanValue = v) })
                                },
                            )
                        }
                        "number" -> OutlinedTextField(
                            value = f.numberValue?.toString() ?: "",
                            onValueChange = { v ->
                                val n = v.toIntOrNull()
                                onChange(fields.toMutableList().also { it[index] = f.copy(numberValue = n) })
                            },
                            label = { Text(f.name) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        else -> OutlinedTextField(
                            value = f.textValue ?: "",
                            onValueChange = { v ->
                                onChange(fields.toMutableList().also { it[index] = f.copy(textValue = v) })
                            },
                            label = { Text(f.name) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                IconButton(onClick = { onChange(fields.toMutableList().also { it.removeAt(index) }) }) {
                    Icon(Icons.Outlined.Close, contentDescription = "Rimuovi campo")
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        // Aggiunta di un nuovo campo.
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text("Nuovo campo") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            ExposedDropdownMenuBox(
                expanded = typeExpanded,
                onExpandedChange = { typeExpanded = it },
                modifier = Modifier.width(130.dp),
            ) {
                OutlinedTextField(
                    value = typeLabel(newType),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Tipo") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                )
                androidx.compose.material3.ExposedDropdownMenu(
                    expanded = typeExpanded,
                    onDismissRequest = { typeExpanded = false },
                ) {
                    listOf("text", "number", "boolean").forEach { t ->
                        DropdownMenuItem(
                            text = { Text(typeLabel(t)) },
                            onClick = { newType = t; typeExpanded = false },
                        )
                    }
                }
            }
        }
        OutlinedButton(
            onClick = {
                if (newName.isNotBlank()) {
                    onChange(fields + CustomField(null, newName.trim(), newType, if (newType == "text") "" else null, if (newType == "number") 0 else null, if (newType == "boolean") false else null))
                    newName = ""
                    newType = "text"
                }
            },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null)
            Text("  Aggiungi campo")
        }
    }
}

private fun typeLabel(t: String): String = when (t.lowercase()) {
    "number" -> "Numero"
    "boolean" -> "Si/No"
    "time" -> "Data"
    else -> "Testo"
}

// ---------------------------------------------------------------------------
// Sezione manutenzione (nel dettaglio di un oggetto)
// ---------------------------------------------------------------------------

@Composable
fun MaintenanceSection(viewModel: HomeViewModel, entityId: String) {
    val entries by viewModel.maintenance.collectAsState()
    var showAdd by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                "Manutenzione",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { showAdd = true }) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Text(" Aggiungi")
            }
        }
        if (entries.isEmpty()) {
            Text(
                "Nessuna voce di manutenzione.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            entries.forEach { e ->
                MaintenanceRow(e) { viewModel.deleteMaintenance(e.id, entityId) }
                Divider()
            }
        }
    }

    if (showAdd) {
        AddMaintenanceDialog(
            onDismiss = { showAdd = false },
            onConfirm = { name, desc, cost, scheduled, completed ->
                viewModel.addMaintenance(entityId, name, desc, cost, scheduled, completed)
                showAdd = false
            },
        )
    }
}

@Composable
private fun MaintenanceRow(e: MaintenanceEntry, onDelete: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.weight(1f)) {
            Text(e.name, style = MaterialTheme.typography.bodyLarge)
            val sub = buildString {
                e.completedDate?.let { append("Completata: ${formatIsoToDisplay(it)}") }
                e.scheduledDate?.let {
                    if (isNotEmpty()) append(" · ")
                    append("Prevista: ${formatIsoToDisplay(it)}")
                }
                e.cost?.let {
                    if (isNotEmpty()) append(" · ")
                    append("€ $it")
                }
            }
            if (sub.isNotBlank()) {
                Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            e.description?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Outlined.Delete, contentDescription = "Elimina", tint = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun AddMaintenanceDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String?, String?, String?, String?) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf("") }
    var completed by remember { mutableStateOf("") }
    var scheduled by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuova manutenzione") },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Nome") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                DateField("Data completamento", completed.ifBlank { null }, { completed = it ?: "" })
                Spacer(Modifier.height(8.dp))
                DateField("Data prevista", scheduled.ifBlank { null }, { scheduled = it ?: "" })
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = cost, onValueChange = { cost = it },
                    label = { Text("Costo") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = description, onValueChange = { description = it },
                    label = { Text("Descrizione") }, modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        name,
                        description.ifBlank { null },
                        cost.ifBlank { null },
                        scheduled.ifBlank { null },
                        completed.ifBlank { null },
                    )
                },
                enabled = name.isNotBlank(),
            ) { Text("Aggiungi") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )
}
