package it.stevej.homeboxdroid.data

import it.stevej.homeboxdroid.data.remote.EntityCreateDto
import it.stevej.homeboxdroid.data.remote.EntityOutDto
import it.stevej.homeboxdroid.data.remote.EntityTypeSummaryDto
import it.stevej.homeboxdroid.data.remote.EntityUpdateDto
import it.stevej.homeboxdroid.data.remote.HomeboxApi
import it.stevej.homeboxdroid.data.remote.LoginFormDto
import it.stevej.homeboxdroid.data.remote.TagCreateDto
import it.stevej.homeboxdroid.data.remote.TreeItemDto
import it.stevej.homeboxdroid.data.cache.CacheDao
import it.stevej.homeboxdroid.data.cache.DetailEntity
import it.stevej.homeboxdroid.data.cache.NodeEntity
import it.stevej.homeboxdroid.data.cache.StatsEntity
import it.stevej.homeboxdroid.data.cache.TagEntity
import it.stevej.homeboxdroid.data.cache.TypeEntity
import it.stevej.homeboxdroid.domain.Attachment
import it.stevej.homeboxdroid.domain.BarcodeResult
import it.stevej.homeboxdroid.domain.EntityDetail
import it.stevej.homeboxdroid.domain.EntityNode
import it.stevej.homeboxdroid.domain.EntityType
import it.stevej.homeboxdroid.domain.LocationRef
import it.stevej.homeboxdroid.domain.NodeType
import it.stevej.homeboxdroid.domain.Statistics
import it.stevej.homeboxdroid.domain.Tag
import it.stevej.homeboxdroid.domain.UserInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Unica fonte di verita' verso il server Homebox. Network-first con fallback
 * alla cache Room per le letture (albero, dettaglio, tag, tipi, statistiche):
 * se la rete non risponde, mostra gli ultimi dati noti e segnala lo stato
 * offline. Le scritture richiedono ancora la rete (coda offline: blocco futuro).
 */
@Singleton
class HomeboxRepository @Inject constructor(
    private val api: HomeboxApi,
    private val settings: SecureSettings,
    private val cache: CacheDao,
    private val json: Json,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
) {
    // Cache in memoria dei tipi entita' per risolvere location/item type.
    @Volatile private var cachedTypes: List<EntityType>? = null

    // true quando l'ultima lettura ha usato la cache perche' la rete non
    // rispondeva. La UI mostra un indicatore.
    private val _offline = MutableStateFlow(false)
    val offline: StateFlow<Boolean> = _offline.asStateFlow()

    /** Numero di operazioni di scrittura in coda (offline). */
    fun pendingCountFlow(): kotlinx.coroutines.flow.Flow<Int> = cache.pendingCountFlow()

    suspend fun pendingCount(): Int = withContext(Dispatchers.IO) { cache.pendingCount() }

    suspend fun clearPendingQueue() = withContext(Dispatchers.IO) { cache.clearPending() }

    /** Esito di una scrittura: eseguita, messa in coda (offline), o errore. */
    sealed interface WriteResult {
        data object Done : WriteResult
        data object Queued : WriteResult
        data class Error(val message: String) : WriteResult
    }

    private fun newTempId(): String = "tmp_" + java.util.UUID.randomUUID().toString()

    // --- Auth ---------------------------------------------------------------

    suspend fun loginWithApiKey(
        url: String,
        apiKey: String,
        allowSelfSigned: Boolean,
    ): ApiResult<Unit> = withContext(Dispatchers.IO) {
        settings.allowSelfSigned = allowSelfSigned
        settings.serverUrl = normalizeUrl(url)
        settings.authMode = SecureSettings.MODE_APIKEY
        settings.credential = normalizeCredential(apiKey)
        verifySession()
    }

    suspend fun loginWithPassword(
        url: String,
        email: String,
        password: String,
        allowSelfSigned: Boolean,
    ): ApiResult<Unit> = withContext(Dispatchers.IO) {
        settings.allowSelfSigned = allowSelfSigned
        settings.serverUrl = normalizeUrl(url)
        settings.authMode = SecureSettings.MODE_PASSWORD
        settings.credential = null // il login deve poter partire senza Bearer

        when (val status = safeApiCall { api.status() }) {
            is ApiResult.Error -> return@withContext ApiResult.Error(status.message, status.code)
            is ApiResult.Success -> Unit
        }

        when (val login = safeApiCall {
            api.login(LoginFormDto(username = email.trim(), password = password, stayLoggedIn = true))
        }) {
            is ApiResult.Error -> {
                clearTentative()
                return@withContext ApiResult.Error(login.message, login.code)
            }
            is ApiResult.Success -> {
                val token = login.data.token
                if (token.isNullOrBlank()) {
                    clearTentative()
                    return@withContext ApiResult.Error("Il server non ha restituito un token valido.")
                }
                settings.credential = normalizeCredential(token)
                settings.attachmentToken = login.data.attachmentToken
                verifySession()
            }
        }
    }

    /** Verifica la credenziale con /status e /users/self; su errore pulisce. */
    private suspend fun verifySession(): ApiResult<Unit> {
        when (val status = safeApiCall { api.status() }) {
            is ApiResult.Error -> { clearTentative(); return ApiResult.Error(status.message, status.code) }
            is ApiResult.Success -> Unit
        }
        return when (val self = safeApiCall { api.self() }) {
            is ApiResult.Error -> { clearTentative(); ApiResult.Error(self.message, self.code) }
            is ApiResult.Success -> {
                self.data.item?.let {
                    settings.userName = it.name
                    settings.userEmail = it.email
                }
                ApiResult.Success(Unit)
            }
        }
    }

    private fun clearTentative() {
        settings.clearSession()
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        // best-effort: invalida lato server se possibile, poi pulisce locale.
        if (settings.authMode == SecureSettings.MODE_PASSWORD) {
            runCatching { api.logout() }
        }
        cachedTypes = null
        _offline.value = false
        runCatching { cache.clearAll() }
        settings.clearSession()
    }

    fun currentUser(): UserInfo = UserInfo(
        name = settings.userName,
        email = settings.userEmail,
        superuser = false,
    )

    suspend fun testConnection(): ApiResult<String> = withContext(Dispatchers.IO) {
        when (val r = safeApiCall { api.status() }) {
            is ApiResult.Error -> ApiResult.Error(r.message, r.code)
            is ApiResult.Success -> {
                val v = r.data.versions?.firstOrNull() ?: r.data.message ?: "ok"
                ApiResult.Success(v)
            }
        }
    }

    // --- Browse -------------------------------------------------------------

    /** Un errore di sola connettivita' (timeout/host irraggiungibile) ha code null. */
    private fun isConnectivity(err: ApiResult.Error): Boolean = err.code == null

    suspend fun tree(withItems: Boolean = true): ApiResult<List<EntityNode>> =
        withContext(Dispatchers.IO) {
            when (val r = safeApiCall { api.tree(withItems) }) {
                is ApiResult.Success -> {
                    cache.replaceNodes(flattenForCache(r.data))
                    _offline.value = false
                    ApiResult.Success(r.data.map { it.toNode() })
                }
                is ApiResult.Error -> {
                    if (isConnectivity(r)) {
                        val cached = cache.allNodes()
                        if (cached.isNotEmpty()) {
                            _offline.value = true
                            return@withContext ApiResult.Success(rebuildTree(cached))
                        }
                    }
                    ApiResult.Error(r.message, r.code)
                }
            }
        }

    suspend fun entity(id: String): ApiResult<EntityDetail> = withContext(Dispatchers.IO) {
        when (val r = safeApiCall { api.entity(id) }) {
            is ApiResult.Success -> {
                runCatching {
                    cache.putDetail(DetailEntity(id, json.encodeToString(EntityOutDto.serializer(), r.data)))
                }
                _offline.value = false
                ApiResult.Success(r.data.toDetail())
            }
            is ApiResult.Error -> {
                if (isConnectivity(r)) {
                    val cachedJson = cache.detailJson(id)
                    if (cachedJson != null) {
                        val dto = runCatching {
                            json.decodeFromString(EntityOutDto.serializer(), cachedJson)
                        }.getOrNull()
                        if (dto != null) {
                            _offline.value = true
                            return@withContext ApiResult.Success(dto.toDetail())
                        }
                    }
                }
                ApiResult.Error(r.message, r.code)
            }
        }
    }

    suspend fun statistics(): ApiResult<Statistics> = withContext(Dispatchers.IO) {
        when (val r = safeApiCall { api.statistics() }) {
            is ApiResult.Success -> {
                val stats = Statistics(
                    totalItems = r.data.totalItems ?: 0,
                    totalLocations = r.data.totalLocations ?: 0,
                    totalTags = r.data.totalTags ?: 0,
                    totalUsers = r.data.totalUsers ?: 0,
                    totalItemPrice = r.data.totalItemPrice ?: 0.0,
                    totalWithWarranty = r.data.totalWithWarranty ?: 0,
                )
                cache.putStats(
                    StatsEntity(0, stats.totalItems, stats.totalLocations, stats.totalTags,
                        stats.totalUsers, stats.totalItemPrice, stats.totalWithWarranty)
                )
                ApiResult.Success(stats)
            }
            is ApiResult.Error -> {
                if (isConnectivity(r)) {
                    cache.stats()?.let {
                        return@withContext ApiResult.Success(
                            Statistics(it.totalItems, it.totalLocations, it.totalTags,
                                it.totalUsers, it.totalItemPrice, it.totalWithWarranty)
                        )
                    }
                }
                ApiResult.Error(r.message, r.code)
            }
        }
    }

    // --- Tag ----------------------------------------------------------------

    suspend fun tags(): ApiResult<List<Tag>> = withContext(Dispatchers.IO) {
        when (val r = safeApiCall { api.tags() }) {
            is ApiResult.Success -> {
                val tags = r.data.mapNotNull { t ->
                    val id = t.id ?: return@mapNotNull null
                    Tag(id = id, name = t.name ?: "(tag)", color = t.color)
                }
                cache.replaceTags(tags.map { TagEntity(it.id, it.name, it.color) })
                ApiResult.Success(tags)
            }
            is ApiResult.Error -> {
                if (isConnectivity(r)) {
                    val cached = cache.allTags()
                    if (cached.isNotEmpty()) {
                        return@withContext ApiResult.Success(cached.map { Tag(it.id, it.name, it.color) })
                    }
                }
                ApiResult.Error(r.message, r.code)
            }
        }
    }

    suspend fun createTag(name: String): ApiResult<Tag> = withContext(Dispatchers.IO) {
        when (val r = safeApiCall { api.createTag(TagCreateDto(name = name.trim())) }) {
            is ApiResult.Error -> ApiResult.Error(r.message, r.code)
            is ApiResult.Success -> ApiResult.Success(
                Tag(id = r.data.id ?: "", name = r.data.name ?: name.trim(), color = r.data.color)
            )
        }
    }

    // --- Tipi entita' -------------------------------------------------------

    suspend fun entityTypes(force: Boolean = false): ApiResult<List<EntityType>> =
        withContext(Dispatchers.IO) {
            cachedTypes?.let { if (!force) return@withContext ApiResult.Success(it) }
            when (val r = safeApiCall { api.entityTypes() }) {
                is ApiResult.Success -> {
                    val mapped = r.data.map { it.toType() }
                    cachedTypes = mapped
                    cache.replaceTypes(mapped.map { TypeEntity(it.id, it.name, it.isLocation, it.icon) })
                    ApiResult.Success(mapped)
                }
                is ApiResult.Error -> {
                    if (isConnectivity(r)) {
                        val cached = cache.allTypes()
                        if (cached.isNotEmpty()) {
                            val mapped = cached.map { EntityType(it.id, it.name, it.isLocation, it.icon) }
                            cachedTypes = mapped
                            return@withContext ApiResult.Success(mapped)
                        }
                    }
                    ApiResult.Error(r.message, r.code)
                }
            }
        }

    private suspend fun resolveTypeId(location: Boolean): ApiResult<String> {
        val types = when (val r = entityTypes()) {
            is ApiResult.Error -> return ApiResult.Error(r.message, r.code)
            is ApiResult.Success -> r.data
        }
        val match = types.firstOrNull { it.isLocation == location }
        return if (match != null) ApiResult.Success(match.id)
        else ApiResult.Error(
            if (location) "Nessun tipo 'location' trovato sul server."
            else "Nessun tipo 'oggetto' trovato sul server."
        )
    }

    // --- Creazione / modifica / eliminazione (con coda offline) -------------

    /** Campi modificabili dall'utente. Le date sono "yyyy-MM-dd" o null. */
    data class EntityEdit(
        val name: String,
        val description: String?,
        val notes: String?,
        val quantity: Double?,
        val manufacturer: String?,
        val modelNumber: String?,
        val serialNumber: String?,
        val purchasePrice: Double?,
        val purchaseDate: String?,
        val purchaseFrom: String?,
        val warrantyExpires: String?,
        val lifetimeWarranty: Boolean,
        val locationId: String?,
        val parentId: String?,
        val tagIds: List<String>,
    )

    suspend fun createLocation(name: String, description: String?, parentId: String?): WriteResult =
        withContext(Dispatchers.IO) {
            val typeRes = resolveTypeId(location = true)
            if (typeRes is ApiResult.Error && !isConnectivity(typeRes)) {
                return@withContext WriteResult.Error(typeRes.message)
            }
            if (typeRes is ApiResult.Success) {
                val body = EntityCreateDto(
                    name = name.trim(),
                    description = description?.trim()?.ifBlank { null },
                    entityTypeId = typeRes.data,
                    parentId = parentId,
                )
                when (val r = netCreateEntity(body)) {
                    is ApiResult.Success -> { _offline.value = false; return@withContext WriteResult.Done }
                    is ApiResult.Error -> if (!isConnectivity(r)) return@withContext WriteResult.Error(r.message)
                }
            }
            // Offline: metti in coda + inserimento ottimistico.
            val tempId = newTempId()
            val payload = CreateLocationPayload(tempId, name.trim(), description?.trim()?.ifBlank { null }, parentId)
            cache.insertPending(
                PendingOpEntity(
                    type = PendingType.CREATE_LOCATION,
                    payload = json.encodeToString(CreateLocationPayload.serializer(), payload),
                    tempId = tempId, createdAt = System.currentTimeMillis(), lastError = null,
                )
            )
            optimisticCreateNode(tempId, name.trim(), isLocation = true, parentId = parentId, quantity = null)
            WriteResult.Queued
        }

    suspend fun createItem(
        name: String,
        quantity: Double,
        description: String?,
        manufacturer: String?,
        modelNumber: String?,
        locationId: String?,
        tagIds: List<String> = emptyList(),
    ): WriteResult = withContext(Dispatchers.IO) {
        val typeRes = resolveTypeId(location = false)
        if (typeRes is ApiResult.Error && !isConnectivity(typeRes)) {
            return@withContext WriteResult.Error(typeRes.message)
        }
        if (typeRes is ApiResult.Success) {
            val body = EntityCreateDto(
                name = name.trim(),
                description = description?.trim()?.ifBlank { null },
                entityTypeId = typeRes.data,
                quantity = quantity,
                manufacturer = manufacturer?.trim()?.ifBlank { null },
                modelNumber = modelNumber?.trim()?.ifBlank { null },
                locationId = locationId,
                parentId = locationId,
                tagIds = tagIds.ifEmpty { null },
            )
            when (val r = netCreateEntity(body)) {
                is ApiResult.Success -> { _offline.value = false; return@withContext WriteResult.Done }
                is ApiResult.Error -> if (!isConnectivity(r)) return@withContext WriteResult.Error(r.message)
            }
        }
        val tempId = newTempId()
        val payload = CreateItemPayload(
            tempId, name.trim(), quantity, description?.trim()?.ifBlank { null },
            manufacturer?.trim()?.ifBlank { null }, modelNumber?.trim()?.ifBlank { null },
            locationId, tagIds,
        )
        cache.insertPending(
            PendingOpEntity(
                type = PendingType.CREATE_ITEM,
                payload = json.encodeToString(CreateItemPayload.serializer(), payload),
                tempId = tempId, createdAt = System.currentTimeMillis(), lastError = null,
            )
        )
        optimisticCreateNode(tempId, name.trim(), isLocation = false, parentId = locationId, quantity = quantity)
        WriteResult.Queued
    }

    suspend fun updateEntity(id: String, edit: EntityEdit): WriteResult = withContext(Dispatchers.IO) {
        when (val r = netUpdate(id, edit)) {
            is ApiResult.Success -> { _offline.value = false; return@withContext WriteResult.Done }
            is ApiResult.Error -> if (!isConnectivity(r)) return@withContext WriteResult.Error(r.message)
        }
        // Offline: coda + patch ottimistica.
        val payload = UpdatePayload(
            id, edit.name.trim(), edit.description, edit.notes, edit.quantity, edit.manufacturer,
            edit.modelNumber, edit.serialNumber, edit.purchasePrice, edit.purchaseDate, edit.purchaseFrom,
            edit.warrantyExpires, edit.lifetimeWarranty, edit.locationId, edit.parentId, edit.tagIds,
        )
        cache.insertPending(
            PendingOpEntity(
                type = PendingType.UPDATE,
                payload = json.encodeToString(UpdatePayload.serializer(), payload),
                tempId = null, createdAt = System.currentTimeMillis(), lastError = null,
            )
        )
        cache.renameNode(id, edit.name.trim())
        patchCachedDetail(id, edit)
        WriteResult.Queued
    }

    suspend fun deleteEntity(id: String): WriteResult = withContext(Dispatchers.IO) {
        // Se e' un'entita' creata offline e non ancora sincronizzata, annulla
        // localmente le operazioni in coda che la riguardano (no-op lato server).
        if (id.startsWith("tmp_")) {
            val ops = cache.allPending()
            val related = ops.filter { it.tempId == id || it.payload.contains(id) }
            if (related.isNotEmpty()) {
                related.forEach { cache.deletePending(it.seq) }
                cache.deleteNode(id)
                return@withContext WriteResult.Queued
            }
        }
        when (val r = netDelete(id)) {
            is ApiResult.Success -> { _offline.value = false; return@withContext WriteResult.Done }
            is ApiResult.Error -> if (!isConnectivity(r)) return@withContext WriteResult.Error(r.message)
        }
        cache.insertPending(
            PendingOpEntity(
                type = PendingType.DELETE,
                payload = json.encodeToString(DeletePayload.serializer(), DeletePayload(id)),
                tempId = null, createdAt = System.currentTimeMillis(), lastError = null,
            )
        )
        cache.deleteNode(id)
        WriteResult.Queued
    }

    // Chiamate di rete "pure", condivise da percorso online e replay.
    private suspend fun netCreateEntity(body: EntityCreateDto): ApiResult<String> =
        when (val r = safeApiCall { api.createEntity(body) }) {
            is ApiResult.Error -> ApiResult.Error(r.message, r.code)
            is ApiResult.Success -> ApiResult.Success(r.data.id ?: "")
        }

    private suspend fun netUpdate(id: String, edit: EntityEdit): ApiResult<Unit> {
        val current = when (val r = safeApiCall { api.entity(id) }) {
            is ApiResult.Error -> return ApiResult.Error(r.message, r.code)
            is ApiResult.Success -> r.data
        }
        val body = EntityUpdateDto(
            id = id,
            name = edit.name.trim(),
            description = edit.description?.trim()?.ifBlank { null },
            notes = edit.notes?.trim()?.ifBlank { null },
            quantity = edit.quantity,
            manufacturer = edit.manufacturer?.trim()?.ifBlank { null },
            modelNumber = edit.modelNumber?.trim()?.ifBlank { null },
            serialNumber = edit.serialNumber?.trim()?.ifBlank { null },
            purchasePrice = edit.purchasePrice,
            purchaseDate = toRfc3339(edit.purchaseDate),
            purchaseFrom = edit.purchaseFrom?.trim()?.ifBlank { null },
            lifetimeWarranty = edit.lifetimeWarranty,
            warrantyExpires = if (edit.lifetimeWarranty) null else toRfc3339(edit.warrantyExpires),
            locationId = edit.locationId,
            parentId = edit.parentId,
            entityTypeId = current.entityType?.id,
            assetId = current.assetId,
            archived = current.archived,
            insured = current.insured,
            warrantyDetails = current.warrantyDetails,
            soldDate = current.soldDate,
            soldPrice = current.soldPrice,
            soldTo = current.soldTo,
            soldNotes = current.soldNotes,
            tagIds = edit.tagIds,
            fields = current.fields,
        )
        return when (val r = safeApiCall { api.updateEntity(id, body) }) {
            is ApiResult.Error -> ApiResult.Error(r.message, r.code)
            is ApiResult.Success -> ApiResult.Success(Unit)
        }
    }

    private suspend fun netDelete(id: String): ApiResult<Unit> =
        when (val r = safeApiCall { api.deleteEntity(id) }) {
            is ApiResult.Error -> ApiResult.Error(r.message, r.code)
            is ApiResult.Success -> ApiResult.Success(Unit)
        }

    // Inserimento ottimistico di un nodo creato offline (+ dettaglio minimo).
    private suspend fun optimisticCreateNode(
        tempId: String, name: String, isLocation: Boolean, parentId: String?, quantity: Double?,
    ) {
        val pos = cache.maxNodePosition() + 1
        cache.upsertNode(
            NodeEntity(
                id = tempId, name = name,
                type = (if (isLocation) NodeType.LOCATION else NodeType.ITEM).name,
                parentId = parentId, position = pos,
            )
        )
        val dto = EntityOutDto(id = tempId, name = name, quantity = quantity, locationId = parentId)
        runCatching {
            cache.putDetail(DetailEntity(tempId, json.encodeToString(EntityOutDto.serializer(), dto)))
        }
    }

    // Applica i campi modificati al dettaglio in cache (se presente).
    private suspend fun patchCachedDetail(id: String, edit: EntityEdit) {
        val cachedJson = cache.detailJson(id) ?: return
        val dto = runCatching { json.decodeFromString(EntityOutDto.serializer(), cachedJson) }.getOrNull() ?: return
        val patched = dto.copy(
            name = edit.name.trim(),
            description = edit.description,
            notes = edit.notes,
            quantity = edit.quantity,
            manufacturer = edit.manufacturer,
            modelNumber = edit.modelNumber,
            serialNumber = edit.serialNumber,
            purchasePrice = edit.purchasePrice,
            purchaseDate = toRfc3339(edit.purchaseDate),
            purchaseFrom = edit.purchaseFrom,
            lifetimeWarranty = edit.lifetimeWarranty,
            warrantyExpires = if (edit.lifetimeWarranty) null else toRfc3339(edit.warrantyExpires),
        )
        runCatching {
            cache.putDetail(DetailEntity(id, json.encodeToString(EntityOutDto.serializer(), patched)))
        }
    }

    // --- Replay della coda --------------------------------------------------

    /**
     * Esegue le operazioni in coda in ordine FIFO. Si ferma al primo errore
     * (connettivita' = ritenta dopo; errore server = registrato e stop) per non
     * propagare inconsistenze. I temp-id dei CREATE vengono rimappati sugli id
     * reali, anche nelle operazioni successive (payload riscritti e persistiti).
     */
    suspend fun syncPending(): Unit = withContext(Dispatchers.IO) {
        val ops = cache.allPending()
        if (ops.isEmpty()) return@withContext
        for (op in ops) {
            val outcome: ApiResult<*> = when (op.type) {
                PendingType.CREATE_LOCATION -> {
                    val p = json.decodeFromString(CreateLocationPayload.serializer(), op.payload)
                    val typeId = when (val t = resolveTypeId(true)) {
                        is ApiResult.Error -> { recordStop(op, t); return@withContext }
                        is ApiResult.Success -> t.data
                    }
                    val res = netCreateEntity(
                        EntityCreateDto(name = p.name, description = p.description, entityTypeId = typeId, parentId = p.parentId)
                    )
                    if (res is ApiResult.Success) onCreateSynced(p.tempId, res.data)
                    res
                }
                PendingType.CREATE_ITEM -> {
                    val p = json.decodeFromString(CreateItemPayload.serializer(), op.payload)
                    val typeId = when (val t = resolveTypeId(false)) {
                        is ApiResult.Error -> { recordStop(op, t); return@withContext }
                        is ApiResult.Success -> t.data
                    }
                    val res = netCreateEntity(
                        EntityCreateDto(
                            name = p.name, description = p.description, entityTypeId = typeId,
                            quantity = p.quantity, manufacturer = p.manufacturer, modelNumber = p.modelNumber,
                            locationId = p.locationId, parentId = p.locationId, tagIds = p.tagIds.ifEmpty { null },
                        )
                    )
                    if (res is ApiResult.Success) onCreateSynced(p.tempId, res.data)
                    res
                }
                PendingType.UPDATE -> {
                    val p = json.decodeFromString(UpdatePayload.serializer(), op.payload)
                    netUpdate(
                        p.targetId,
                        EntityEdit(
                            p.name, p.description, p.notes, p.quantity, p.manufacturer, p.modelNumber,
                            p.serialNumber, p.purchasePrice, p.purchaseDate, p.purchaseFrom, p.warrantyExpires,
                            p.lifetimeWarranty, p.locationId, p.parentId, p.tagIds,
                        ),
                    )
                }
                PendingType.DELETE -> {
                    val p = json.decodeFromString(DeletePayload.serializer(), op.payload)
                    netDelete(p.targetId)
                }
                PendingType.UPLOAD -> {
                    val p = json.decodeFromString(UploadPayload.serializer(), op.payload)
                    val file = java.io.File(p.filePath)
                    val bytes = runCatching { file.readBytes() }.getOrNull()
                    if (bytes == null) ApiResult.Success(Unit) // file sparito: salta
                    else netUpload(p.entityId, bytes, p.filename, p.mime, primary = false)
                        .also { if (it is ApiResult.Success) runCatching { file.delete() } }
                }
                else -> ApiResult.Success(Unit)
            }
            when (outcome) {
                is ApiResult.Success -> cache.deletePending(op.seq)
                is ApiResult.Error -> {
                    if (isConnectivity(outcome)) return@withContext // offline: riprova dopo
                    recordStop(op, outcome); return@withContext // errore server: ferma
                }
            }
        }
    }

    private suspend fun recordStop(op: PendingOpEntity, err: ApiResult.Error) {
        cache.setPendingError(op.seq, err.message)
    }

    /** Dopo un CREATE andato a buon fine: aggiorna cache e riscrive i riferimenti al temp-id. */
    private suspend fun onCreateSynced(tempId: String, realId: String) {
        cache.remapNodeParent(tempId, realId)
        cache.remapNodeId(tempId, realId)
        // Sposta il dettaglio in cache dal temp all'id reale.
        cache.detailJson(tempId)?.let { cache.putDetail(DetailEntity(realId, it)) }
        // Riscrive i payload delle operazioni ancora in coda che citano il temp-id.
        cache.allPending().forEach { op ->
            if (op.payload.contains(tempId)) {
                val newPayload = op.payload.replace(tempId, realId)
                val newTemp = if (op.tempId == tempId) realId else op.tempId
                cache.updatePendingPayload(op.seq, newPayload, newTemp)
            }
        }
    }

    // --- Allegati -----------------------------------------------------------

    /** URL assoluto per scaricare un allegato (usato da Coil con auth). */
    fun attachmentUrl(entityId: String, attachmentId: String): String {
        val base = settings.serverUrl?.trimEnd('/') ?: ""
        return "$base/api/v1/entities/$entityId/attachments/$attachmentId"
    }

    suspend fun uploadAttachment(
        entityId: String,
        bytes: ByteArray,
        filename: String,
        mime: String,
        primary: Boolean,
    ): WriteResult = withContext(Dispatchers.IO) {
        when (val r = netUpload(entityId, bytes, filename, mime, primary)) {
            is ApiResult.Success -> { _offline.value = false; return@withContext WriteResult.Done }
            is ApiResult.Error -> if (!isConnectivity(r)) return@withContext WriteResult.Error(r.message)
        }
        // Offline: salva i byte su file e metti in coda.
        val dir = java.io.File(context.filesDir, "pending_uploads").apply { mkdirs() }
        val file = java.io.File(dir, "${System.currentTimeMillis()}_$filename")
        val saved = runCatching { file.writeBytes(bytes); true }.getOrDefault(false)
        if (!saved) return@withContext WriteResult.Error("Impossibile salvare l'allegato in coda.")
        cache.insertPending(
            PendingOpEntity(
                type = PendingType.UPLOAD,
                payload = json.encodeToString(
                    UploadPayload.serializer(),
                    UploadPayload(entityId, file.absolutePath, filename, mime),
                ),
                tempId = null, createdAt = System.currentTimeMillis(), lastError = null,
            )
        )
        WriteResult.Queued
    }

    private suspend fun netUpload(
        entityId: String, bytes: ByteArray, filename: String, mime: String, primary: Boolean,
    ): ApiResult<Unit> {
        val media = mime.toMediaTypeOrNull()
        val filePart = MultipartBody.Part.createFormData(
            name = "file", filename = filename, body = bytes.toRequestBody(media),
        )
        val text = "text/plain".toMediaTypeOrNull()
        val type = if (mime.startsWith("image/")) "photo" else "attachment"
        return when (val r = safeApiCall {
            api.uploadAttachment(
                id = entityId,
                file = filePart,
                name = filename.toRequestBody(text),
                type = type.toRequestBody(text),
                primary = primary.toString().toRequestBody(text),
            )
        }) {
            is ApiResult.Error -> ApiResult.Error(r.message, r.code)
            is ApiResult.Success -> ApiResult.Success(Unit)
        }
    }

    suspend fun deleteAttachment(entityId: String, attachmentId: String): ApiResult<Unit> =
        withContext(Dispatchers.IO) {
            when (val r = safeApiCall { api.deleteAttachment(entityId, attachmentId) }) {
                is ApiResult.Error -> ApiResult.Error(r.message, r.code)
                is ApiResult.Success -> ApiResult.Success(Unit)
            }
        }

    // --- Ricerca ------------------------------------------------------------

    suspend fun searchEntities(
        query: String,
        tagIds: List<String>,
        matchAllTags: Boolean,
        onlyType: SearchType,
    ): ApiResult<List<it.stevej.homeboxdroid.domain.SearchResult>> = withContext(Dispatchers.IO) {
        // Risolve gli eventuali tipi (location/item) in entityTypeIds.
        val typeIds: List<String>? = when (onlyType) {
            SearchType.ALL -> null
            SearchType.LOCATIONS, SearchType.ITEMS -> {
                val types = when (val r = entityTypes()) {
                    is ApiResult.Error -> return@withContext ApiResult.Error(r.message, r.code)
                    is ApiResult.Success -> r.data
                }
                val wantLocation = onlyType == SearchType.LOCATIONS
                types.filter { it.isLocation == wantLocation }.map { it.id }.ifEmpty { null }
            }
        }
        when (val r = safeApiCall {
            api.entities(
                q = query.trim().ifBlank { null },
                pageSize = 100,
                tags = tagIds.ifEmpty { null },
                matchAllTags = if (tagIds.isEmpty()) null else matchAllTags,
                entityTypeIds = typeIds,
                orderBy = "name",
            )
        }) {
            is ApiResult.Error -> ApiResult.Error(r.message, r.code)
            is ApiResult.Success -> ApiResult.Success(
                (r.data.items ?: emptyList()).mapNotNull { s ->
                    val id = s.id ?: return@mapNotNull null
                    it.stevej.homeboxdroid.domain.SearchResult(
                        id = id,
                        name = s.name ?: "(senza nome)",
                        isLocation = s.entityType?.isLocation ?: false,
                        typeName = s.entityType?.name,
                        quantity = s.quantity,
                        tags = s.tags?.mapNotNull { t -> t.name } ?: emptyList(),
                    )
                }
            )
        }
    }

    enum class SearchType { ALL, LOCATIONS, ITEMS }

    // --- Barcode ------------------------------------------------------------

    suspend fun searchBarcode(code: String): ApiResult<BarcodeResult?> =
        withContext(Dispatchers.IO) {
            when (val r = safeApiCall { api.searchBarcode(code) }) {
                is ApiResult.Error -> ApiResult.Error(r.message, r.code)
                is ApiResult.Success -> {
                    val p = r.data?.firstOrNull()
                    ApiResult.Success(
                        p?.let {
                            BarcodeResult(
                                name = it.item?.name?.ifBlank { null },
                                manufacturer = it.manufacturer?.ifBlank { null },
                                modelNumber = it.modelNumber?.ifBlank { null },
                                notes = it.notes?.ifBlank { null },
                            )
                        }
                    )
                }
            }
        }

    // --- Cache albero -------------------------------------------------------

    private fun nodeTypeOf(dto: TreeItemDto): NodeType = when (dto.type?.lowercase()) {
        "location" -> NodeType.LOCATION
        "item" -> NodeType.ITEM
        else -> if (!dto.children.isNullOrEmpty()) NodeType.LOCATION else NodeType.OTHER
    }

    /** Appiattisce l'albero in righe (DFS), position = ordine di visita. */
    private fun flattenForCache(items: List<TreeItemDto>): List<NodeEntity> {
        val out = mutableListOf<NodeEntity>()
        fun walk(list: List<TreeItemDto>, parentId: String?) {
            for (it in list) {
                val nid = it.id.orEmpty()
                if (nid.isEmpty()) continue
                out += NodeEntity(nid, it.name ?: "(senza nome)", nodeTypeOf(it).name, parentId, out.size)
                it.children?.let { c -> walk(c, nid) }
            }
        }
        walk(items, null)
        return out
    }

    /** Ricostruisce l'albero dalle righe in cache. */
    private fun rebuildTree(rows: List<NodeEntity>): List<EntityNode> {
        val byParent = rows.sortedBy { it.position }.groupBy { it.parentId }
        fun build(parentId: String?): List<EntityNode> =
            (byParent[parentId] ?: emptyList()).map { n ->
                EntityNode(
                    id = n.id,
                    name = n.name,
                    type = runCatching { NodeType.valueOf(n.type) }.getOrDefault(NodeType.OTHER),
                    children = build(n.id),
                )
            }
        return build(null)
    }

    // --- Mapping ------------------------------------------------------------

    private fun TreeItemDto.toNode(): EntityNode = EntityNode(
        id = id.orEmpty(),
        name = name ?: "(senza nome)",
        type = nodeTypeOf(this),
        children = children?.map { it.toNode() } ?: emptyList(),
    )

    private fun EntityTypeSummaryDto.toType(): EntityType = EntityType(
        id = id.orEmpty(),
        name = name ?: "(tipo)",
        isLocation = isLocation ?: false,
        icon = icon,
    )

    private fun EntityOutDto.toDetail(): EntityDetail = EntityDetail(
        id = id.orEmpty(),
        name = name ?: "(senza nome)",
        description = description?.ifBlank { null },
        notes = notes?.ifBlank { null },
        quantity = quantity,
        itemCount = itemCount,
        manufacturer = manufacturer?.ifBlank { null },
        modelNumber = modelNumber?.ifBlank { null },
        serialNumber = serialNumber?.ifBlank { null },
        assetId = assetId?.ifBlank { null },
        purchasePrice = purchasePrice,
        purchaseDate = purchaseDate?.ifBlank { null }?.take(10),
        purchaseFrom = purchaseFrom?.ifBlank { null },
        warrantyExpires = warrantyExpires?.ifBlank { null }?.take(10),
        lifetimeWarranty = lifetimeWarranty ?: false,
        locationName = location?.name,
        typeName = entityType?.name,
        isLocation = entityType?.isLocation ?: false,
        tags = tags?.mapNotNull { it.name } ?: emptyList(),
        tagIds = tags?.mapNotNull { it.id } ?: emptyList(),
        childrenCount = children?.size ?: 0,
        locationId = locationId ?: location?.id,
        parentId = parent?.id,
        attachments = attachments?.mapNotNull { a ->
            val aid = a.id ?: return@mapNotNull null
            Attachment(
                id = aid,
                title = a.title ?: a.path?.substringAfterLast('/') ?: "allegato",
                mimeType = a.mimeType,
                primary = a.primary ?: false,
            )
        }?.sortedByDescending { it.primary } ?: emptyList(),
    )

    // --- Util ---------------------------------------------------------------

    private fun normalizeUrl(raw: String): String {
        var u = raw.trim()
        if (!u.startsWith("http://") && !u.startsWith("https://")) u = "https://$u"
        while (u.endsWith("/")) u = u.dropLast(1)
        return u
    }

    private fun normalizeCredential(raw: String): String {
        var c = raw.trim()
        if (c.startsWith("Bearer ", ignoreCase = true)) c = c.substring(7).trim()
        return c
    }

    /** "yyyy-MM-dd" -> "yyyy-MM-ddT00:00:00Z"; stringhe gia' complete restano tali. */
    private fun toRfc3339(date: String?): String? {
        val d = date?.trim()?.ifBlank { null } ?: return null
        return if (d.length == 10 && d[4] == '-' && d[7] == '-') "${d}T00:00:00Z" else d
    }

    companion object {
        /** Appiattisce l'albero nelle sole location, per i selettori. */
        fun flattenLocations(nodes: List<EntityNode>, depth: Int = 0): List<LocationRef> {
            val out = mutableListOf<LocationRef>()
            for (n in nodes) {
                if (n.type == NodeType.LOCATION) {
                    out += LocationRef(n.id, n.name, depth)
                    out += flattenLocations(n.children, depth + 1)
                }
            }
            return out
        }
    }
}
