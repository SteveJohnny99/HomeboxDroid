package it.stevej.homeboxdroid.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import it.stevej.homeboxdroid.data.HomeboxRepository
import it.stevej.homeboxdroid.domain.SearchResult
import it.stevej.homeboxdroid.ui.home.HomeViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    homeViewModel: HomeViewModel,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val s by viewModel.state.collectAsState()

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = s.query,
            onValueChange = viewModel::onQuery,
            label = { Text("Cerca per nome, seriale, note...") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(8.dp))
        // Filtro tipo.
        Row {
            TypeChip("Tutto", s.type == HomeboxRepository.SearchType.ALL) {
                viewModel.setType(HomeboxRepository.SearchType.ALL)
            }
            Spacer(Modifier.width(8.dp))
            TypeChip("Luoghi", s.type == HomeboxRepository.SearchType.LOCATIONS) {
                viewModel.setType(HomeboxRepository.SearchType.LOCATIONS)
            }
            Spacer(Modifier.width(8.dp))
            TypeChip("Oggetti", s.type == HomeboxRepository.SearchType.ITEMS) {
                viewModel.setType(HomeboxRepository.SearchType.ITEMS)
            }
        }

        if (s.tags.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("Tag", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow {
                s.tags.forEach { tag ->
                    FilterChip(
                        selected = tag.id in s.selectedTags,
                        onClick = { viewModel.toggleTag(tag.id) },
                        label = { Text(tag.name) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }
            if (s.selectedTags.size > 1) {
                FilterChip(
                    selected = s.matchAll,
                    onClick = { viewModel.toggleMatchAll() },
                    label = { Text(if (s.matchAll) "Tutti i tag (AND)" else "Qualsiasi tag (OR)") },
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Box(Modifier.fillMaxSize()) {
            when {
                s.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                s.error != null -> Text(
                    s.error!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center),
                )
                s.searched && s.results.isEmpty() -> Text(
                    "Nessun risultato.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )
                !s.searched -> Text(
                    "Scrivi qualcosa o scegli un filtro.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
                ) {
                    items(s.results, key = { it.id }) { res ->
                        ResultRow(res) { homeViewModel.openDetail(res.id) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TypeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@Composable
private fun ResultRow(res: SearchResult, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (res.isLocation) Icons.Outlined.Folder else Icons.Outlined.Inventory2,
                contentDescription = null,
                tint = if (res.isLocation) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(res.name, style = MaterialTheme.typography.bodyLarge)
                val sub = buildString {
                    res.typeName?.let { append(it) }
                    if (res.tags.isNotEmpty()) {
                        if (isNotEmpty()) append(" · ")
                        append(res.tags.joinToString(", "))
                    }
                }
                if (sub.isNotBlank()) {
                    Text(
                        sub,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
