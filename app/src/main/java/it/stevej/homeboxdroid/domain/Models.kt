package it.stevej.homeboxdroid.domain

/** Tipo di nodo nell'albero. */
enum class NodeType { LOCATION, ITEM, OTHER }

/** Nodo dell'albero location/item (da /entities/tree). */
data class EntityNode(
    val id: String,
    val name: String,
    val type: NodeType,
    val children: List<EntityNode>,
) {
    val isLocation: Boolean get() = type == NodeType.LOCATION
    val hasChildren: Boolean get() = children.isNotEmpty()
}

/** Tipo di entita' (location vs item). */
data class EntityType(
    val id: String,
    val name: String,
    val isLocation: Boolean,
    val icon: String?,
)

/** Dettaglio completo di un'entita'. */
data class EntityDetail(
    val id: String,
    val name: String,
    val description: String?,
    val notes: String?,
    val quantity: Double?,
    val itemCount: Double?,
    val manufacturer: String?,
    val modelNumber: String?,
    val serialNumber: String?,
    val assetId: String?,
    val purchasePrice: Double?,
    val purchaseDate: String?,
    val purchaseFrom: String?,
    val warrantyExpires: String?,
    val lifetimeWarranty: Boolean,
    val locationName: String?,
    val typeName: String?,
    val isLocation: Boolean,
    val tags: List<String>,
    val tagIds: List<String>,
    val childrenCount: Int,
    val locationId: String?,
    val parentId: String?,
)

/** Statistiche di gruppo per la dashboard. */
data class Statistics(
    val totalItems: Int,
    val totalLocations: Int,
    val totalTags: Int,
    val totalUsers: Int,
    val totalItemPrice: Double,
    val totalWithWarranty: Int,
)

/** Profilo utente minimo. */
data class UserInfo(
    val name: String?,
    val email: String?,
    val superuser: Boolean,
)

/** Riferimento a una location per i selettori (es. in creazione oggetto). */
data class LocationRef(
    val id: String,
    val name: String,
    val depth: Int,
)

/** Tag/etichetta. */
data class Tag(
    val id: String,
    val name: String,
    val color: String?,
)
