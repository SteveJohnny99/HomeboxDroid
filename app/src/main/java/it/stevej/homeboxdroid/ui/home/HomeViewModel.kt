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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Una tappa del breadcrumb. id vuoto = radice. */
data class Crumb(val id: String, val name: String)

/** Cerca un nodo per id fra i figli diretti della lista data. */
internal fun findChild(nodes: List<EntityNode>, id: String): EntityNode? =
    nodes.firstOrNull { it.id == id }

/** Criteri di ordinamento della lista. */
enum class SortField(val label: String) {
    NAME("Nome"), CREATED("Data creazione"), QUANTITY("Quantita'"), PRICE("Prezzo")
}

data class SortOptions(
    val field: SortField = SortField.NAME,
    val asc: Boolean = true,
    val locationsFirst: Boolean = true,
)

/** Confronto con i valori mancanti sempre in fondo, a prescindere dalla direzione. */
private fun <T : Comparable<T>> cmpNullsLast(a: T?, b: T?, asc: Boolean): Int = when {
    a == null && b == null -> 0
    a == null -> 1
    b == null -> -1
    asc -> a.compareTo(b)
    else -> b.compareTo(a)
}

internal fun sortNodes(nodes: List<EntityNode>, s: SortOptions): List<EntityNode> {
    val byName = Comparator<EntityNode> { a, b -> a.name.lowercase().compareTo(b.name.lowercase()) }
    val main = Comparator<EntityNode> { a, b ->
        val r = when (s.field) {
            SortField.NAME -> if (s.asc) byName.compare(a, b) else byName.compare(b, a)
            SortField.CREATED -> cmpNullsLast(a.createdAt, b.createdAt, s.asc)
            SortField.QUANTITY -> cmpNullsLast(a.quantity, b.quantity, s.asc)
            SortField.PRICE -> cmpNullsLast(a.price, b.price, s.asc)
        }
        if (r != 0) r else byName.compare(a, b)
    }
    val cmp = if (s.locationsFirst) {
        compareByDescending<EntityNode> { it.type == NodeType.LOCATION }.then(main)
    } else main
    return nodes.sortedWith(cmp)
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repo: HomeboxRepository,
    private val settings: it.stevej.homeboxdroid.data.SecureSettings,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = false,
        val error: String? = null,
        val tree: List<EntityNode> = emptyList(),
        val crumbs: List<Crumb> = listOf(Crumb("", "Tutto")),
        val stats: Statistics? = null,
        val message: String? = null,
        val saving: Boolean = false,
        val sort: SortOptions = SortOptions(),
    ) {
        /** Nodi da mostrare al livello corrente del breadcrumb. */
        val currentChildren: List<EntityNode>
            get() {
                var level = tree
                for (crumb in crumbs.drop(1)) {
                    val next = findChild(level, crumb.id) ?: return emptyList()
                    level = next.children
                }
                return sortNodes(level, sort)
            }

        /** Location corrente (ultimo crumb non radice), per default nelle creazioni. */
        val currentLocationId: String?
            get() = crumbs.lastOrNull()?.id?.ifBlank { null }
    }

    private val _state = MutableStateFlow(
        UiState(
            sort = SortOptions(
                field = runCatching { SortField.valueOf(settings.sortField) }.getOrDefault(SortField.NAME),
                asc = settings.sortAsc,
                locationsFirst = settings.sortLocationsFirst,
            )
        )
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun setSort(field: SortField) {
        _state.update {
            // Ri-selezionare lo stesso criterio inverte la direzione; un criterio
            // nuovo parte da una direzione sensata (date: piu' recenti prima).
            val asc = if (it.sort.field == field) !it.sort.asc else field == SortField.NAME
            it.copy(sort = it.sort.copy(field = field, asc = asc))
        }
        persistSort()
    }

    fun setSortAsc(asc: Boolean) {
        _state.update { it.copy(sort = it.sort.copy(asc = asc)) }
        persistSort()
    }

    fun setLocationsFirst(v: Boolean) {
        _state.update { it.copy(sort = it.sort.copy(locationsFirst = v)) }
        persistSort()
    }

    private fun persistSort() {
        val s = _state.value.sort
        settings.sortField = s.field.name
        settings.sortAsc = s.asc
        settings.sortLocationsFirst = s.locationsFirst
    }

    private val _detail = MutableStateFlow<DetailState>(DetailState.Hidden)
    val detail: StateFlow<DetailState> = _detail.asStateFlow()

    private val _tags = MutableStateFlow<List<Tag>>(emptyList())
    val tags: StateFlow<List<Tag>> = _tags.asStateFlow()

    /** true quando i dati mostrati vengono dalla cache (rete non raggiungibile). */
    val offline: StateFlow<Boolean> = repo.offline

    /** Numero di scritture in coda (offline) da sincronizzare. */
    val pendingCount: StateFlow<Int> = repo.pendingCountFlow()
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, 0)

    /**
     * Gestione comune dell'esito di una scrittura.
     * Ritorna true se eseguita o messa in coda (chiude il dialog), false su errore.
     */
    private fun handleWrite(
        r: HomeboxRepository.WriteResult,
        doneMsg: String,
    ): Boolean = when (r) {
        is HomeboxRepository.WriteResult.Done -> {
            _state.update { it.copy(saving = false, message = doneMsg) }; true
        }
        is HomeboxRepository.WriteResult.Queued -> {
            _state.update { it.copy(saving = false, message = "$doneMsg (in coda, offline)") }; true
        }
        is HomeboxRepository.WriteResult.Partial -> {
            _state.update { it.copy(saving = false, message = r.message) }; true
        }
        is HomeboxRepository.WriteResult.Error -> {
            _state.update { it.copy(saving = false, message = r.message) }; false
        }
    }

    // Dialog "nuovo oggetto" e scanner barcode gestiti qui, per poter chiudere
    // il dialog durante la scansione e riaprirlo precompilato.
    private val _showCreateItem = MutableStateFlow(false)
    val showCreateItem: StateFlow<Boolean> = _showCreateItem.asStateFlow()

    private val _scannerForItem = MutableStateFlow(false)
    val scannerForItem: StateFlow<Boolean> = _scannerForItem.asStateFlow()

    /** Precompilazione da barcode, consumata all'apertura del dialog. */
    @Volatile private var pendingPrefill: it.stevej.homeboxdroid.domain.BarcodeResult? = null
    fun consumeItemPrefill(): it.stevej.homeboxdroid.domain.BarcodeResult? =
        pendingPrefill.also { pendingPrefill = null }

    // Bozza del modulo conservata mentre il dialog e' chiuso per la scansione.
    @Volatile private var itemDraft: ItemFormState? = null
    fun consumeItemDraft(): ItemFormState? = itemDraft.also { itemDraft = null }

    fun openCreateItem() { pendingPrefill = null; itemDraft = null; _showCreateItem.value = true }
    fun closeCreateItem() { _showCreateItem.value = false }

    fun startItemBarcodeScan(draft: ItemFormState? = null) {
        itemDraft = draft
        _showCreateItem.value = false
        _scannerForItem.value = true
    }

    fun cancelScan() {
        _scannerForItem.value = false
        _showCreateItem.value = true
    }

    fun onItemBarcode(code: String) {
        _scannerForItem.value = false
        viewModelScope.launch {
            when (val r = repo.searchBarcode(code)) {
                is ApiResult.Error -> _state.update { it.copy(message = r.message) }
                is ApiResult.Success -> {
                    if (r.data == null) {
                        _state.update { it.copy(message = "Nessun prodotto trovato per questo codice.") }
                    } else {
                        pendingPrefill = r.data
                    }
                }
            }
            _showCreateItem.value = true
        }
    }

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
            // Prova a sincronizzare la coda prima di ricaricare (best-effort).
            if (repo.pendingCount() > 0) repo.syncPending()
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

    fun syncNow() {
        viewModelScope.launch {
            repo.syncPending()
            refresh()
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
        _maintenance.value = emptyList()
        viewModelScope.launch {
            when (val r = repo.entity(id)) {
                is ApiResult.Error -> _detail.value = DetailState.Error(r.message)
                is ApiResult.Success -> {
                    _detail.value = DetailState.Ready(r.data)
                    if (!r.data.isLocation && !id.startsWith("tmp_")) loadMaintenance(id)
                }
            }
        }
    }

    fun closeDetail() { _detail.value = DetailState.Hidden; _maintenance.value = emptyList() }

    // --- Manutenzione -------------------------------------------------------

    private val _maintenance = MutableStateFlow<List<it.stevej.homeboxdroid.domain.MaintenanceEntry>>(emptyList())
    val maintenance: StateFlow<List<it.stevej.homeboxdroid.domain.MaintenanceEntry>> = _maintenance.asStateFlow()

    private fun loadMaintenance(id: String) {
        viewModelScope.launch {
            when (val r = repo.maintenance(id)) {
                is ApiResult.Success -> _maintenance.value = r.data
                is ApiResult.Error -> Unit // manutenzione non disponibile offline: lista vuota
            }
        }
    }

    fun addMaintenance(
        entityId: String, name: String, description: String?, cost: String?,
        scheduledDate: String?, completedDate: String?,
    ) {
        if (name.isBlank()) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            when (val r = repo.createMaintenance(entityId, name, description, cost, scheduledDate, completedDate)) {
                is ApiResult.Error -> _state.update { it.copy(saving = false, message = r.message) }
                is ApiResult.Success -> {
                    _state.update { it.copy(saving = false, message = "Voce di manutenzione aggiunta.") }
                    loadMaintenance(entityId)
                }
            }
        }
    }

    fun deleteMaintenance(maintenanceId: String, entityId: String) {
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            when (val r = repo.deleteMaintenance(maintenanceId)) {
                is ApiResult.Error -> _state.update { it.copy(saving = false, message = r.message) }
                is ApiResult.Success -> {
                    _state.update { it.copy(saving = false, message = "Voce eliminata.") }
                    loadMaintenance(entityId)
                }
            }
        }
    }

    fun goToCrumb(index: Int) {
        _state.update { it.copy(crumbs = it.crumbs.take(index + 1)) }
    }

    fun createLocation(name: String, description: String?) {
        if (name.isBlank()) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val parent = _state.value.currentLocationId
            if (handleWrite(repo.createLocation(name, description, parent), "Luogo creato.")) {
                _saveSuccess.tryEmit(Unit)
                refresh()
            }
        }
    }

    fun createItem(edit: HomeboxRepository.EntityEdit) {
        if (edit.name.isBlank()) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            if (handleWrite(repo.createItem(edit), "Oggetto creato.")) {
                _saveSuccess.tryEmit(Unit)
                refresh()
            }
        }
    }

    /** Azione rapida dalla lista: imposta la quantita' (PATCH). */
    fun setQuantity(id: String, quantity: Double) {
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            if (handleWrite(repo.patchQuantity(id, quantity), "Quantita' aggiornata.")) refresh()
        }
    }

    fun updateEntity(id: String, edit: HomeboxRepository.EntityEdit) {
        if (edit.name.isBlank()) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            if (handleWrite(repo.updateEntity(id, edit), "Modifiche salvate.")) {
                _saveSuccess.tryEmit(Unit)
                closeDetail()
                refresh()
            }
        }
    }

    fun deleteEntity(id: String) {
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            if (handleWrite(repo.deleteEntity(id), "Eliminato.")) {
                closeDetail()
                refresh()
            }
        }
    }

    // --- Allegati -----------------------------------------------------------

    fun attachmentUrl(entityId: String, attachmentId: String): String =
        repo.attachmentUrl(entityId, attachmentId)

    /** openAfter=false per la foto rapida dalla lista (non apre il dettaglio). */
    fun uploadAttachment(
        entityId: String, bytes: ByteArray, filename: String, mime: String, openAfter: Boolean = true,
    ) {
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val ok = handleWrite(
                repo.uploadAttachment(entityId, bytes, filename, mime, primary = false),
                if (openAfter) "Allegato caricato." else "Foto caricata.",
            )
            if (ok && openAfter) openDetail(entityId) // ricarica il dettaglio con il nuovo allegato
        }
    }

    fun deleteAttachment(entityId: String, attachmentId: String) {
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            when (val r = repo.deleteAttachment(entityId, attachmentId)) {
                is ApiResult.Error -> _state.update { it.copy(saving = false, message = r.message) }
                is ApiResult.Success -> {
                    _state.update { it.copy(saving = false, message = "Allegato eliminato.") }
                    openDetail(entityId)
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
