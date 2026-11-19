package it.stevej.homeboxdroid.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import it.stevej.homeboxdroid.data.HomeboxRepository
import it.stevej.homeboxdroid.domain.CustomField
import it.stevej.homeboxdroid.domain.EntityDetail
import it.stevej.homeboxdroid.domain.LocationRef
import it.stevej.homeboxdroid.domain.Tag

/**
 * Stato del modulo oggetto, condiviso da creazione e modifica: stessi campi in
 * entrambi i casi. I campi "avanzati" stanno in una sezione a tendina.
 */
@Stable
class ItemFormState(
    name: String = "",
    quantity: String = "1",
    description: String = "",
    locationId: String? = null,
    tagIds: Set<String> = emptySet(),
    manufacturer: String = "",
    model: String = "",
    serial: String = "",
    price: String = "",
    purchaseDate: String = "",
    purchaseFrom: String = "",
    warranty: String = "",
    lifetime: Boolean = false,
    notes: String = "",
    customFields: List<CustomField> = emptyList(),
    advancedOpen: Boolean = false,
) {
    var name by mutableStateOf(name)
    var quantity by mutableStateOf(quantity)
    var description by mutableStateOf(description)
    var locationId by mutableStateOf(locationId)
    var tagIds by mutableStateOf(tagIds)
    var manufacturer by mutableStateOf(manufacturer)
    var model by mutableStateOf(model)
    var serial by mutableStateOf(serial)
    var price by mutableStateOf(price)
    var purchaseDate by mutableStateOf(purchaseDate)
    var purchaseFrom by mutableStateOf(purchaseFrom)
    var warranty by mutableStateOf(warranty)
    var lifetime by mutableStateOf(lifetime)
    var notes by mutableStateOf(notes)
    var customFields by mutableStateOf(customFields)
    var advancedOpen by mutableStateOf(advancedOpen)

    /** Converte in EntityEdit (posizione = genitore, come per gli oggetti). */
    fun toEdit(): HomeboxRepository.EntityEdit = HomeboxRepository.EntityEdit(
        name = name.trim(),
        description = description,
        notes = notes,
        quantity = quantity.replace(',', '.').toDoubleOrNull() ?: 1.0,
        manufacturer = manufacturer,
        modelNumber = model,
        serialNumber = serial,
        purchasePrice = price.replace(',', '.').toDoubleOrNull(),
        purchaseDate = purchaseDate.ifBlank { null },
        purchaseFrom = purchaseFrom,
        warrantyExpires = if (lifetime) null else warranty.ifBlank { null },
        lifetimeWarranty = lifetime,
        locationId = locationId,
        parentId = locationId,
        tagIds = tagIds.toList(),
        customFields = customFields,
    )

    companion object {
        fun fromDetail(e: EntityDetail): ItemFormState = ItemFormState(
            name = e.name,
            quantity = e.quantity?.let { formatQuantity(it) } ?: "1",
            description = e.description ?: "",
            locationId = e.locationId ?: e.parentId,
            tagIds = e.tagIds.toSet(),
            manufacturer = e.manufacturer ?: "",
            model = e.modelNumber ?: "",
            serial = e.serialNumber ?: "",
            price = e.purchasePrice?.let { "%.2f".format(java.util.Locale.US, it) } ?: "",
            purchaseDate = e.purchaseDate ?: "",
            purchaseFrom = e.purchaseFrom ?: "",
            warranty = e.warrantyExpires ?: "",
            lifetime = e.lifetimeWarranty,
            notes = e.notes ?: "",
            customFields = e.customFields,
            // In modifica la sezione avanzata parte aperta se contiene dati.
            advancedOpen = listOf(e.manufacturer, e.modelNumber, e.serialNumber, e.purchaseFrom,
                e.purchaseDate, e.warrantyExpires, e.notes).any { !it.isNullOrBlank() } ||
                e.purchasePrice != null || e.lifetimeWarranty || e.customFields.isNotEmpty(),
        )
    }
}

/** Quantita' leggibile: intera senza decimali. */
fun formatQuantity(q: Double): String =
    if (q == q.toLong().toDouble()) q.toLong().toString() else q.toString()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemFormFields(
    state: ItemFormState,
    locations: List<LocationRef>,
    tags: List<Tag>,
    onCreateTag: (String, (Tag) -> Unit) -> Unit,
) {
    // --- Campi base -----------------------------------------------------------
    OutlinedTextField(
        value = state.name, onValueChange = { state.name = it },
        label = { Text("Nome") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = state.quantity, onValueChange = { state.quantity = it },
        label = { Text("Quantita'") }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = state.description, onValueChange = { state.description = it },
        label = { Text("Descrizione") }, modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))

    if (locations.isNotEmpty()) {
        var expanded by remember { mutableStateOf(false) }
        val selectedLabel = locations.firstOrNull { it.id == state.locationId }?.name ?: "Nessuna"
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = selectedLabel, onValueChange = {}, readOnly = true,
                label = { Text("Posizione") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                locations.forEach { loc ->
                    DropdownMenuItem(
                        text = { Text("  ".repeat(loc.depth) + loc.name) },
                        onClick = { state.locationId = loc.id; expanded = false },
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    TagSelector(
        all = tags,
        selected = state.tagIds,
        onToggle = { id -> state.tagIds = if (id in state.tagIds) state.tagIds - id else state.tagIds + id },
        onCreate = { nm -> onCreateTag(nm) { t -> state.tagIds = state.tagIds + t.id } },
    )
    Spacer(Modifier.height(8.dp))

    // --- Sezione avanzata a tendina -------------------------------------------
    HorizontalDivider()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { state.advancedOpen = !state.advancedOpen }
            .padding(vertical = 10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text("Avanzate", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "Marca, modello, seriale, acquisto, garanzia, note, campi personalizzati",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            if (state.advancedOpen) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
            contentDescription = if (state.advancedOpen) "Chiudi avanzate" else "Apri avanzate",
        )
    }
    AnimatedVisibility(visible = state.advancedOpen) {
        Column {
            OutlinedTextField(
                value = state.manufacturer, onValueChange = { state.manufacturer = it },
                label = { Text("Marca / produttore") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.model, onValueChange = { state.model = it },
                label = { Text("Modello") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.serial, onValueChange = { state.serial = it },
                label = { Text("Numero di serie") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.price, onValueChange = { state.price = it },
                label = { Text("Prezzo d'acquisto") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            DateField(
                label = "Data acquisto",
                isoValue = state.purchaseDate.ifBlank { null },
                onChange = { state.purchaseDate = it ?: "" },
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.purchaseFrom, onValueChange = { state.purchaseFrom = it },
                label = { Text("Acquistato da") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Garanzia a vita", modifier = Modifier.weight(1f))
                Switch(checked = state.lifetime, onCheckedChange = { state.lifetime = it })
            }
            if (!state.lifetime) {
                DateField(
                    label = "Scadenza garanzia",
                    isoValue = state.warranty.ifBlank { null },
                    onChange = { state.warranty = it ?: "" },
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.notes, onValueChange = { state.notes = it },
                label = { Text("Note") }, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            CustomFieldsEditor(fields = state.customFields, onChange = { state.customFields = it })
        }
    }
}
