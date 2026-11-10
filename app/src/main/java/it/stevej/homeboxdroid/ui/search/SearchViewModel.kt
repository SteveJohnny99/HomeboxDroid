package it.stevej.homeboxdroid.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.stevej.homeboxdroid.data.ApiResult
import it.stevej.homeboxdroid.data.HomeboxRepository
import it.stevej.homeboxdroid.domain.SearchResult
import it.stevej.homeboxdroid.domain.Tag
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repo: HomeboxRepository,
) : ViewModel() {

    data class UiState(
        val query: String = "",
        val tags: List<Tag> = emptyList(),
        val selectedTags: Set<String> = emptySet(),
        val type: HomeboxRepository.SearchType = HomeboxRepository.SearchType.ALL,
        val matchAll: Boolean = false,
        val loading: Boolean = false,
        val error: String? = null,
        val results: List<SearchResult> = emptyList(),
        val searched: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            when (val r = repo.tags()) {
                is ApiResult.Success -> _state.update { it.copy(tags = r.data) }
                is ApiResult.Error -> Unit
            }
        }
    }

    fun onQuery(v: String) {
        _state.update { it.copy(query = v) }
        scheduleSearch()
    }

    fun toggleTag(id: String) {
        _state.update {
            val sel = if (id in it.selectedTags) it.selectedTags - id else it.selectedTags + id
            it.copy(selectedTags = sel)
        }
        scheduleSearch(immediate = true)
    }

    fun setType(t: HomeboxRepository.SearchType) {
        _state.update { it.copy(type = t) }
        scheduleSearch(immediate = true)
    }

    fun toggleMatchAll() {
        _state.update { it.copy(matchAll = !it.matchAll) }
        scheduleSearch(immediate = true)
    }

    private fun scheduleSearch(immediate: Boolean = false) {
        val s = _state.value
        // Niente ricerca se non c'e' alcun criterio: svuota i risultati.
        if (s.query.isBlank() && s.selectedTags.isEmpty() && s.type == HomeboxRepository.SearchType.ALL) {
            searchJob?.cancel()
            _state.update { it.copy(results = emptyList(), searched = false, loading = false) }
            return
        }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (!immediate) delay(350)
            _state.update { it.copy(loading = true, error = null) }
            when (val r = repo.searchEntities(s.query, s.selectedTags.toList(), s.matchAll, s.type)) {
                is ApiResult.Error -> _state.update { it.copy(loading = false, error = r.message, searched = true) }
                is ApiResult.Success -> _state.update {
                    it.copy(loading = false, results = r.data, searched = true, error = null)
                }
            }
        }
    }
}
