package it.stevej.homeboxdroid.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import it.stevej.homeboxdroid.domain.Tag
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val DISPLAY_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")

/** "yyyy-MM-dd" -> "GG-MM-AAAA" per la visualizzazione. */
fun formatIsoToDisplay(iso: String): String =
    runCatching { LocalDate.parse(iso.take(10)).format(DISPLAY_FMT) }.getOrDefault(iso)

private fun isoToMillis(iso: String): Long? =
    runCatching {
        LocalDate.parse(iso.take(10)).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }.getOrNull()

private fun millisToIso(millis: Long?): String? =
    millis?.let {
        Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
            .format(DateTimeFormatter.ISO_LOCAL_DATE)
    }

/**
 * Campo data con selettore a calendario (niente testo libero: nessuna data
 * malformata). Valore interno in "yyyy-MM-dd", mostrato come GG-MM-AAAA.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    label: String,
    isoValue: String?,
    onChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var show by remember { mutableStateOf(false) }
    val display = isoValue?.takeIf { it.isNotBlank() }?.let { formatIsoToDisplay(it) } ?: ""

    Box(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = display,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            placeholder = { Text("GG-MM-AAAA") },
            trailingIcon = { Icon(Icons.Outlined.CalendarMonth, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
        )
        // Overlay trasparente: tutto il campo apre il calendario.
        Box(Modifier.matchParentSize().clickable { show = true })
    }

    if (show) {
        val stateDp = rememberDatePickerState(initialSelectedDateMillis = isoValue?.let { isoToMillis(it) })
        DatePickerDialog(
            onDismissRequest = { show = false },
            confirmButton = {
                TextButton(onClick = {
                    onChange(millisToIso(stateDp.selectedDateMillis))
                    show = false
                }) { Text("OK") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { onChange(null); show = false }) { Text("Cancella") }
                    TextButton(onClick = { show = false }) { Text("Annulla") }
                }
            },
        ) {
            DatePicker(state = stateDp)
        }
    }
}

/**
 * Selettore tag: chip selezionabili su quelli esistenti + creazione rapida.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagSelector(
    all: List<Tag>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onCreate: (String) -> Unit,
) {
    var newTag by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth()) {
        Text(
            "Tag",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (all.isNotEmpty()) {
            FlowRow {
                all.forEach { tag ->
                    FilterChip(
                        selected = tag.id in selected,
                        onClick = { onToggle(tag.id) },
                        label = { Text(tag.name) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = newTag,
                onValueChange = { newTag = it },
                label = { Text("Nuovo tag") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = {
                    if (newTag.isNotBlank()) { onCreate(newTag.trim()); newTag = "" }
                },
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "Crea tag")
            }
        }
    }
}
