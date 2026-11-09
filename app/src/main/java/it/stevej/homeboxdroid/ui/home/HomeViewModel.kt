package it.stevej.homeboxdroid.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.stevej.homeboxdroid.data.ApiResult
import it.stevej.homeboxdroid.data.HomeboxRepository
import it.stevej.homeboxdroid.domain.EntityDetail
import it.stevej.homeboxdroid.domain.EntityNode
import it.stevej.homeboxdroid.domain.LocationRef
import it.stevej.homeboxdroid.domain.NodeType
import it.stevej.homeboxdroid.domain.Statistics
import it.stevej.homeboxdroid.domain.Tag
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Una tappa del breadcrumb. id vuoto = radice. */
data class Crumb(val id: String, val name: String)

/** Cerca un nodo per id fra i figli diretti della lista data. */
internal fun findChild(nodes: List<EntityNode>, id: String): EntityNode? =
    nodes.firstOrNull { it.id == id }

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repo: HomeboxRepository,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = false,
        val error: String? = null,
        val tree: List<EntityNode> = emptyList(),
        val crumbs: List<Crumb> = listOf(Crumb("", "Tutto")),
        val stats: Statistics? = null,
        val message: String? = null,
        val saving: Boolean = false,
    ) {
        /** Nodi da mostrare al livello corrente del breadcrumb. */
        val currentChildren: List<EntityNode>
            get() {
                var level = tree
                for (crumb in crumbs.drop(1)) {
                    val next = findChild(level, crumb.id) ?: return emptyList()
                    level = next.children
                }
                return level.sortedWith(
                    compareByDescending<EntityNode> { it.type == NodeType.LOCATION }
                        .thenBy { it.name.lowercase() }
                )
            }

        /** Location corrente (ultimo crumb non radice), per default nelle creazioni. */
        val currentLocationId: String?
            get() = crumbs.lastOrNull()?.id?.ifBlank { null }
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _detail = MutableStateFlow<DetailState>(DetailState.Hidden)
    val detail: StateFlow<DetailState> = _detail.asStateFlow()

    private val _tags = MutableStateFlow<List<Tag>>(emptyList())
    val tags: StateFlow<List<Tag>> = _tags.asStateFlow()

    // Emesso quando una creazione/modifica/eliminazione va a buon fine: le UI
    // (dialog) lo usano per chiudersi SOLO in caso di successo.
    private val _saveSuccess = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val saveSuccess: SharedFlow<Unit> = _saveSuccess.asSharedFlow()

    val locations: List<LocationRef>
        get() = HomeboxRepository.flattenLocations(_state.value.tree)

    init {
        refresh()
    }

    fun refresh() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val r = repo.tree(withItems = true)) {
                is ApiResult.Error -> _state.update { it.copy(loading = false, error = r.message) }
                is ApiResult.Success -> _state.update {
                    // pota i crumb che non esistono piu'
                    val valid = pruneCrumbs(it.crumbs, r.data)
                    it.copy(loading = false, error = null, tree = r.data, crumbs = valid)
                }
            }
            loadStats()
            loadTags()
        }
    }

    private fun loadStats() {
        viewModelScope.launch {
            when (val r = repo.statistics()) {
                is ApiResult.Success -> _state.update { it.copy(stats = r.data) }
                is ApiResult.Error -> Unit // la dashboard mostra "-" in caso di errore
            }
        }
    }

    private fun loadTags() {
        viewModelScope.launch {
            when (val r = repo.tags()) {
                is ApiResult.Success -> _tags.value = r.data
                is ApiResult.Error -> Unit
            }
        }
    }

    fun createTag(name: String, onCreated: (Tag) -> Unit) {
        if (name.isBlank()) return
        viewModelScope.launch {
            when (val r = repo.createTag(name)) {
                is ApiResult.Error -> _state.update { it.copy(message = r.message) }
                is ApiResult.Success -> {
                    _tags.value = (_tags.value + r.data).distinctBy { it.id }
                    onCreated(r.data)
                }
            }
        }
    }

    fun onNodeClick(node: EntityNode) {
        if (node.type == NodeType.LOCATION || node.hasChildren) {
            _state.update { it.copy(crumbs = it.crumbs + Crumb(node.id, node.name)) }
        } else {
            openDetail(node.id)
        }
    }

    fun openDetail(id: String) {
        _detail.value = DetailState.Loading
        viewModelScope.launch {
            when (val r = repo.entity(id)) {
                is ApiResult.Error -> _detail.value = DetailState.Error(r.message)
                is ApiResult.Success -> _detail.value = DetailState.Ready(r.data)
            }
        }
    }

    fun closeDetail() { _detail.value = DetailState.Hidden }

    fun goToCrumb(index: Int) {
        _state.update { it.copy(crumbs = it.crumbs.take(index + 1)) }
    }

    fun createLocation(name: String, description: String?) {
        if (name.isBlank()) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val parent = _state.value.currentLocationId
            when (val r = repo.createLocation(name, description, parent)) {
                is ApiResult.Error -> _state.update { it.copy(saving = false, message = r.message) }
                is ApiResult.Success -> {
                    _state.update { it.copy(saving = false, message = "Luogo creato.") }
                    _saveSuccess.tryEmit(Unit)
                    refresh()
                }
            }
        }
    }

    fun createItem(
        name: String,
        quantity: Double,
        description: String?,
        manufacturer: String?,
        modelNumber: String?,
        locationId: String?,
        tagIds: List<String> = emptyList(),
    ) {
        if (name.isBlank()) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            when (val r = repo.createItem(name, quantity, description, manufacturer, modelNumber, locationId, tagIds)) {
                is ApiResult.Error -> _state.update { it.copy(saving = false, message = r.message) }
                is ApiResult.Success -> {
                    _state.update { it.copy(saving = false, message = "Oggetto creato.") }
                    _saveSuccess.tryEmit(Unit)
                    refresh()
                }
            }
        }
    }

    fun updateEntity(id: String, edit: HomeboxRepository.EntityEdit) {
        if (edit.name.isBlank()) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            when (val r = repo.updateEntity(id, edit)) {
                is ApiResult.Error -> _state.update { it.copy(saving = false, message = r.message) }
                is ApiResult.Success -> {
                    _state.update { it.copy(saving = false, message = "Modifiche salvate.") }
                    _saveSuccess.tryEmit(Unit)
                    closeDetail()
                    refresh()
                }
            }
        }
    }

    fun deleteEntity(id: String) {
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            when (val r = repo.deleteEntity(id)) {
                is ApiResult.Error -> _state.update { it.copy(saving = false, message = r.message) }
                is ApiResult.Success -> {
                    _state.update { it.copy(saving = false, message = "Eliminato.") }
                    closeDetail()
                    refresh()
                }
            }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    sealed interface DetailState {
        data object Hidden : DetailState
        data object Loading : DetailState
        data class Ready(val entity: EntityDetail) : DetailState
        data class Error(val message: String) : DetailState
    }

    private fun pruneCrumbs(crumbs: List<Crumb>, tree: List<EntityNode>): List<Crumb> {
        val result = mutableListOf(crumbs.first()) // radice
        var level = tree
        for (crumb in crumbs.drop(1)) {
            val node = findChild(level, crumb.id) ?: break
            result += crumb
            level = node.children
        }
        return result
    }

}
