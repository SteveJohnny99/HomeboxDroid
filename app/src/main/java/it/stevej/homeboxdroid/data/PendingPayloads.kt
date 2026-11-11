package it.stevej.homeboxdroid.data

import it.stevej.homeboxdroid.data.remote.EntityFieldDataDto
import kotlinx.serialization.Serializable

/** Tipi di operazione in coda. */
object PendingType {
    const val CREATE_LOCATION = "create_location"
    const val CREATE_ITEM = "create_item"
    const val UPDATE = "update"
    const val DELETE = "delete"
    const val UPLOAD = "upload"
}

@Serializable
data class CreateLocationPayload(
    val tempId: String,
    val name: String,
    val description: String?,
    val parentId: String?,
)

@Serializable
data class CreateItemPayload(
    val tempId: String,
    val name: String,
    val quantity: Double,
    val description: String?,
    val manufacturer: String?,
    val modelNumber: String?,
    val locationId: String?,
    val tagIds: List<String>,
)

@Serializable
data class UpdatePayload(
    val targetId: String,
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
    val customFields: List<EntityFieldDataDto>? = null,
)

@Serializable
data class DeletePayload(val targetId: String)

@Serializable
data class UploadPayload(
    val entityId: String,
    val filePath: String,
    val filename: String,
    val mime: String,
)
