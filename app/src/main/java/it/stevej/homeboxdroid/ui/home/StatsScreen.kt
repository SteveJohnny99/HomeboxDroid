package it.stevej.homeboxdroid.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.stevej.homeboxdroid.domain.Statistics

@Composable
fun StatsScreen(viewModel: HomeViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsState()
    val stats = state.stats

    val cards = buildCards(stats)

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(cards) { card -> StatCard(card.value, card.label) }
    }
}

private data class StatCardData(val value: String, val label: String)

private fun buildCards(s: Statistics?): List<StatCardData> = listOf(
    StatCardData(s?.totalItems?.toString() ?: "-", "Oggetti"),
    StatCardData(s?.totalLocations?.toString() ?: "-", "Luoghi"),
    StatCardData(s?.totalTags?.toString() ?: "-", "Tag"),
    StatCardData(s?.let { "%.0f".format(it.totalItemPrice) } ?: "-", "Valore totale"),
    StatCardData(s?.totalWithWarranty?.toString() ?: "-", "In garanzia"),
    StatCardData(s?.totalUsers?.toString() ?: "-", "Utenti"),
)

@Composable
private fun StatCard(value: String, label: String) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
