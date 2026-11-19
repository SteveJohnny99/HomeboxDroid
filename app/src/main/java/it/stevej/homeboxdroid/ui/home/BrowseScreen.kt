package it.stevej.homeboxdroid.ui.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.text.KeyboardOptions
import androidx.core.content.FileProvider
import java.io.File
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.stevej.homeboxdroid.domain.EntityNode
import it.stevej.homeboxdroid.domain.NodeType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(viewModel: HomeViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    // Foto rapida: scatta e carica direttamente sull'oggetto, senza aprirlo.
    var pendingPhoto by remember { mutableStateOf<Triple<String, Uri, File>?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val p = pendingPhoto
        if (ok && p != null) {
            val raw = runCatching { p.third.readBytes() }.getOrNull()
            val bytes = raw?.let { downscaleJpeg(it) ?: it }
            if (bytes != null) {
                viewModel.uploadAttachment(p.first, bytes, p.third.name, "image/jpeg", openAfter = false)
            }
        }
        pendingPhoto = null
    }
    fun takePhotoFor(id: String) {
        val file = createImageFile(context)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        pendingPhoto = Triple(id, uri, file)
        cameraLauncher.launch(uri)
    }

    // Quantita' rapida.
    var qtyTarget by remember { mutableStateOf<EntityNode?>(null) }
    qtyTarget?.let { node ->
        QuantityDialog(
            node = node,
            onDismiss = { qtyTarget = null },
            onConfirm = { q -> viewModel.setQuantity(node.id, q); qtyTarget = null },
        )
    }

    PullToRefreshBox(
        isRefreshing = state.loading,
        onRefresh = { viewModel.refresh() },
        modifier = modifier.fillMaxSize(),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.weight(1f)) {
                    if (state.crumbs.size > 1) Breadcrumbs(viewModel)
                }
                SortMenu(viewModel)
            }

            when {
                state.error != null -> ErrorState(state.error!!) { viewModel.refresh() }
                !state.loading && state.currentChildren.isEmpty() -> EmptyState()
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.currentChildren, key = { it.id }) { node ->
                        NodeRow(
                            node = node,
                            onClick = { viewModel.onNodeClick(node) },
                            onInfo = { viewModel.openDetail(node.id) },
                            onQuantity = { qtyTarget = node },
                            onPhoto = { takePhotoFor(node.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Breadcrumbs(viewModel: HomeViewModel) {
    val state by viewModel.state.collectAsState()
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        state.crumbs.forEachIndexed { index, crumb ->
            if (index > 0) {
                Text(
                    "  /  ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = crumb.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (index == state.crumbs.lastIndex) FontWeight.SemiBold else FontWeight.Normal,
                color = if (index == state.crumbs.lastIndex) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { viewModel.goToCrumb(index) },
            )
        }
    }
}

@Composable
private fun NodeRow(
    node: EntityNode,
    onClick: () -> Unit,
    onInfo: () -> Unit,
    onQuantity: () -> Unit,
    onPhoto: () -> Unit,
) {
    val isLocation = node.type == NodeType.LOCATION
    Surface(
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 1.dp,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (isLocation) Icons.Outlined.Folder else Icons.Outlined.Inventory2,
                contentDescription = null,
                tint = if (isLocation) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.tertiary,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(node.name, style = MaterialTheme.typography.bodyLarge)
                if (isLocation && node.hasChildren) {
                    Text(
                        "${node.children.size} elementi",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (isLocation) {
                IconButton(onClick = onInfo) {
                    Icon(Icons.Outlined.Info, contentDescription = "Dettagli", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                // Azioni rapide: quantita' e foto.
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    onClick = onQuantity,
                ) {
                    Text(
                        node.quantity?.let { "x" + formatQuantity(it) } ?: "Qta'",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
                IconButton(onClick = onPhoto) {
                    Icon(Icons.Outlined.PhotoCamera, contentDescription = "Scatta foto", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                Icons.Outlined.Folder,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(16.dp))
            Text("Ancora vuoto qui", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                "Usa il pulsante Aggiungi per creare il primo luogo o oggetto.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRetry) { Text("Riprova") }
        }
    }
}

@Composable
private fun LoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}


@Composable
private fun SortMenu(viewModel: HomeViewModel) {
    val state by viewModel.state.collectAsState()
    val sort = state.sort
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) {
            Icon(Icons.Outlined.SwapVert, contentDescription = null)
            Text(" " + sort.field.label)
            Icon(
                if (sort.asc) Icons.Outlined.ArrowUpward else Icons.Outlined.ArrowDownward,
                contentDescription = if (sort.asc) "crescente" else "decrescente",
                modifier = Modifier.size(16.dp),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Text(
                "Ordina per (tocca di nuovo per invertire)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
            SortField.entries.forEach { f ->
                val selected = f == sort.field
                DropdownMenuItem(
                    text = { Text(f.label) },
                    onClick = { viewModel.setSort(f) },
                    leadingIcon = {
                        if (selected) Icon(Icons.Outlined.Check, contentDescription = null)
                        else Spacer(Modifier.size(24.dp))
                    },
                    trailingIcon = {
                        if (selected) Icon(
                            if (sort.asc) Icons.Outlined.ArrowUpward else Icons.Outlined.ArrowDownward,
                            contentDescription = null,
                        )
                    },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Crescente") },
                onClick = { viewModel.setSortAsc(true) },
                leadingIcon = { Icon(Icons.Outlined.ArrowUpward, contentDescription = null) },
                trailingIcon = { if (sort.asc) Icon(Icons.Outlined.Check, contentDescription = null) },
            )
            DropdownMenuItem(
                text = { Text("Decrescente") },
                onClick = { viewModel.setSortAsc(false) },
                leadingIcon = { Icon(Icons.Outlined.ArrowDownward, contentDescription = null) },
                trailingIcon = { if (!sort.asc) Icon(Icons.Outlined.Check, contentDescription = null) },
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Luoghi in cima") },
                onClick = { viewModel.setLocationsFirst(!sort.locationsFirst) },
                leadingIcon = {
                    Checkbox(checked = sort.locationsFirst, onCheckedChange = { viewModel.setLocationsFirst(it) })
                },
            )
        }
    }
}

@Composable
private fun QuantityDialog(node: EntityNode, onDismiss: () -> Unit, onConfirm: (Double) -> Unit) {
    var text by remember { mutableStateOf(node.quantity?.let { formatQuantity(it) } ?: "1") }
    val value = text.replace(',', '.').toDoubleOrNull()
    fun step(delta: Double) {
        val next = ((value ?: 0.0) + delta).coerceAtLeast(0.0)
        text = formatQuantity(next)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(node.name) },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                FilledTonalIconButton(onClick = { step(-1.0) }) {
                    Icon(Icons.Outlined.Remove, contentDescription = "Diminuisci")
                }
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Quantita'") },
                    singleLine = true,
                    isError = value == null || value < 0,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = MaterialTheme.typography.titleLarge.copy(textAlign = TextAlign.Center),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                FilledTonalIconButton(onClick = { step(1.0) }) {
                    Icon(Icons.Outlined.Add, contentDescription = "Aumenta")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { value?.let(onConfirm) },
                enabled = value != null && value >= 0,
            ) { Text("Conferma") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )
}
