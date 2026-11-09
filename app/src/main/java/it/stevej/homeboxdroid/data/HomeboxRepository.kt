package it.stevej.homeboxdroid.data

import it.stevej.homeboxdroid.data.remote.EntityCreateDto
import it.stevej.homeboxdroid.data.remote.EntityOutDto
import it.stevej.homeboxdroid.data.remote.EntityTypeSummaryDto
import it.stevej.homeboxdroid.data.remote.EntityUpdateDto
import it.stevej.homeboxdroid.data.remote.HomeboxApi
import it.stevej.homeboxdroid.data.remote.LoginFormDto
import it.stevej.homeboxdroid.data.remote.TagCreateDto
import it.stevej.homeboxdroid.data.remote.TreeItemDto
import it.stevej.homeboxdroid.domain.EntityDetail
import it.stevej.homeboxdroid.domain.EntityNode
import it.stevej.homeboxdroid.domain.EntityType
import it.stevej.homeboxdroid.domain.LocationRef
import it.stevej.homeboxdroid.domain.NodeType
import it.stevej.homeboxdroid.domain.Statistics
import it.stevej.homeboxdroid.domain.Tag
import it.stevej.homeboxdroid.domain.UserInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Unica fonte di verita' verso il server Homebox. Online-first in questa prima
 * versione; la cache Room arriva in M2. Gestisce auth (API Key o login),
 * browse ad albero e creazione di luoghi/oggetti.
 */
@Singleton
class HomeboxRepository @Inject constructor(
    private val api: HomeboxApi,
    private val settings: SecureSettings,
) {
    // Cache in memoria dei tipi entita' per risolvere location/item type.
    @Volatile private var cachedTypes: List<EntityType>? = null

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

    suspend fun tree(withItems: Boolean = true): ApiResult<List<EntityNode>> =
        withContext(Dispatchers.IO) {
            when (val r = safeApiCall { api.tree(withItems) }) {
                is ApiResult.Error -> ApiResult.Error(r.message, r.code)
                is ApiResult.Success -> ApiResult.Success(r.data.map { it.toNode() })
            }
        }

    suspend fun entity(id: String): ApiResult<EntityDetail> = withContext(Dispatchers.IO) {
        when (val r = safeApiCall { api.entity(id) }) {
            is ApiResult.Error -> ApiResult.Error(r.message, r.code)
            is ApiResult.Success -> ApiResult.Success(r.data.toDetail())
        }
    }

    suspend fun statistics(): ApiResult<Statistics> = withContext(Dispatchers.IO) {
        when (val r = safeApiCall { api.statistics() }) {
            is ApiResult.Error -> ApiResult.Error(r.message, r.code)
            is ApiResult.Success -> ApiResult.Success(
                Statistics(
                    totalItems = r.data.totalItems ?: 0,
                    totalLocations = r.data.totalLocations ?: 0,
                    totalTags = r.data.totalTags ?: 0,
                    totalUsers = r.data.totalUsers ?: 0,
                    totalItemPrice = r.data.totalItemPrice ?: 0.0,
                    totalWithWarranty = r.data.totalWithWarranty ?: 0,
                )
            )
        }
    }

    // --- Tag ----------------------------------------------------------------

    suspend fun tags(): ApiResult<List<Tag>> = withContext(Dispatchers.IO) {
        when (val r = safeApiCall { api.tags() }) {
            is ApiResult.Error -> ApiResult.Error(r.message, r.code)
            is ApiResult.Success -> ApiResult.Success(
                r.data.mapNotNull { t ->
                    val id = t.id ?: return@mapNotNull null
                    Tag(id = id, name = t.name ?: "(tag)", color = t.color)
                }
            )
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
                is ApiResult.Error -> ApiResult.Error(r.message, r.code)
                is ApiResult.Success -> {
                    val mapped = r.data.map { it.toType() }
                    cachedTypes = mapped
                    ApiResult.Success(mapped)
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

    // --- Creazione ----------------------------------------------------------

    suspend fun createLocation(
        name: String,
        description: String?,
        parentId: String?,
    ): ApiResult<String> = withContext(Dispatchers.IO) {
        val typeId = when (val r = resolveTypeId(location = true)) {
            is ApiResult.Error -> return@withContext ApiResult.Error(r.message, r.code)
            is ApiResult.Success -> r.data
        }
        val body = EntityCreateDto(
            name = name.trim(),
            description = description?.trim()?.ifBlank { null },
            entityTypeId = typeId,
            parentId = parentId,
        )
        when (val r = safeApiCall { api.createEntity(body) }) {
            is ApiResult.Error -> ApiResult.Error(r.message, r.code)
            is ApiResult.Success -> ApiResult.Success(r.data.id ?: "")
        }
    }

    suspend fun createItem(
        name: String,
        quantity: Double,
        description: String?,
        manufacturer: String?,
        modelNumber: String?,
        locationId: String?,
        tagIds: List<String> = emptyList(),
    ): ApiResult<String> = withContext(Dispatchers.IO) {
        val typeId = when (val r = resolveTypeId(location = false)) {
            is ApiResult.Error -> return@withContext ApiResult.Error(r.message, r.code)
            is ApiResult.Success -> r.data
        }
        val body = EntityCreateDto(
            name = name.trim(),
            description = description?.trim()?.ifBlank { null },
            entityTypeId = typeId,
            quantity = quantity,
            manufacturer = manufacturer?.trim()?.ifBlank { null },
            modelNumber = modelNumber?.trim()?.ifBlank { null },
            locationId = locationId,
            parentId = locationId, // compare sotto la location nell'albero
            tagIds = tagIds.ifEmpty { null },
        )
        when (val r = safeApiCall { api.createEntity(body) }) {
            is ApiResult.Error -> ApiResult.Error(r.message, r.code)
            is ApiResult.Success -> ApiResult.Success(r.data.id ?: "")
        }
    }

    // --- Modifica / eliminazione --------------------------------------------

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

    suspend fun updateEntity(id: String, edit: EntityEdit): ApiResult<Unit> =
        withContext(Dispatchers.IO) {
            // Rileggo lo stato attuale per preservare i campi non modificati
            // (entityType, tag, custom fields, insured, archived, sold*, ecc.):
            // il PUT e' un update completo.
            val current = when (val r = safeApiCall { api.entity(id) }) {
                is ApiResult.Error -> return@withContext ApiResult.Error(r.message, r.code)
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
                // Preservati dallo stato attuale:
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
            when (val r = safeApiCall { api.updateEntity(id, body) }) {
                is ApiResult.Error -> ApiResult.Error(r.message, r.code)
                is ApiResult.Success -> ApiResult.Success(Unit)
            }
        }

    suspend fun deleteEntity(id: String): ApiResult<Unit> = withContext(Dispatchers.IO) {
        when (val r = safeApiCall { api.deleteEntity(id) }) {
            is ApiResult.Error -> ApiResult.Error(r.message, r.code)
            is ApiResult.Success -> ApiResult.Success(Unit)
        }
    }

    // --- Mapping ------------------------------------------------------------

    private fun TreeItemDto.toNode(): EntityNode = EntityNode(
        id = id.orEmpty(),
        name = name ?: "(senza nome)",
        type = when (type?.lowercase()) {
            "location" -> NodeType.LOCATION
            "item" -> NodeType.ITEM
            else -> if (!children.isNullOrEmpty()) NodeType.LOCATION else NodeType.OTHER
        },
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
