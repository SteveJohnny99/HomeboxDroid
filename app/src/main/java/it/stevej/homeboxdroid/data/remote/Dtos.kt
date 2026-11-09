package it.stevej.homeboxdroid.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * DTO allineati alla spec OpenAPI di Homebox v0.26.x (modello "entities").
 * Solo i campi che servono all'app: Json e' configurato con ignoreUnknownKeys.
 * Tutti i campi sono nullable per robustezza verso versioni leggermente diverse.
 */

// --- Stato / auth -----------------------------------------------------------

@Serializable
data class StatusDto(
    val title: String? = null,
    val message: String? = null,
    val health: Boolean? = null,
    val versions: List<String>? = null,
    val allowRegistration: Boolean? = null,
    val demo: Boolean? = null,
)

@Serializable
data class LoginFormDto(
    val username: String,
    val password: String,
    val stayLoggedIn: Boolean = true,
)

@Serializable
data class TokenResponseDto(
    val token: String? = null,
    val expiresAt: String? = null,
    val attachmentToken: String? = null,
)

@Serializable
data class WrappedUserDto(
    val item: UserOutDto? = null,
)

@Serializable
data class UserOutDto(
    val id: String? = null,
    val name: String? = null,
    val email: String? = null,
    val isSuperuser: Boolean? = null,
    val defaultGroupId: String? = null,
    val groupIds: List<String>? = null,
)

// --- Entities ---------------------------------------------------------------

@Serializable
data class EntityListResultDto(
    val items: List<EntitySummaryDto>? = null,
    val page: Int? = null,
    val pageSize: Int? = null,
    val total: Int? = null,
    val totalPrice: Double? = null,
)

@Serializable
data class EntitySummaryDto(
    val id: String? = null,
    val name: String? = null,
    val description: String? = null,
    val quantity: Double? = null,
    val itemCount: Double? = null,
    val insured: Boolean? = null,
    val archived: Boolean? = null,
    val purchasePrice: Double? = null,
    val imageId: String? = null,
    val thumbnailId: String? = null,
    val entityType: EntityTypeSummaryDto? = null,
    val tags: List<TagSummaryDto>? = null,
)

@Serializable
data class EntityOutDto(
    val id: String? = null,
    val name: String? = null,
    val description: String? = null,
    val notes: String? = null,
    val quantity: Double? = null,
    val itemCount: Double? = null,
    val manufacturer: String? = null,
    val modelNumber: String? = null,
    val serialNumber: String? = null,
    val assetId: String? = null,
    val archived: Boolean? = null,
    val insured: Boolean? = null,
    val lifetimeWarranty: Boolean? = null,
    val warrantyExpires: String? = null,
    val warrantyDetails: String? = null,
    val purchaseFrom: String? = null,
    val purchasePrice: Double? = null,
    val purchaseDate: String? = null,
    val locationId: String? = null,
    val imageId: String? = null,
    val thumbnailId: String? = null,
    val totalPrice: Double? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val entityType: EntityTypeSummaryDto? = null,
    val location: EntitySummaryDto? = null,
    val parent: EntitySummaryDto? = null,
    val children: List<EntitySummaryDto>? = null,
    val tags: List<TagSummaryDto>? = null,
)

@Serializable
data class EntityCreateDto(
    val name: String,
    val description: String? = null,
    val entityTypeId: String? = null,
    val locationId: String? = null,
    val parentId: String? = null,
    val quantity: Double? = null,
    val manufacturer: String? = null,
    val modelNumber: String? = null,
    val tagIds: List<String>? = null,
)

@Serializable
data class TreeItemDto(
    val id: String? = null,
    val name: String? = null,
    val type: String? = null,
    val children: List<TreeItemDto>? = null,
)

// --- Entity types -----------------------------------------------------------

@Serializable
data class EntityTypeSummaryDto(
    val id: String? = null,
    val name: String? = null,
    val icon: String? = null,
    val isLocation: Boolean? = null,
    val description: String? = null,
)

@Serializable
data class EntityTypeCreateDto(
    val name: String,
    val icon: String? = null,
    val isLocation: Boolean? = null,
    val defaultTemplateId: String? = null,
)

// --- Tags -------------------------------------------------------------------

@Serializable
data class TagSummaryDto(
    val id: String? = null,
    val name: String? = null,
    val color: String? = null,
    val icon: String? = null,
    val description: String? = null,
    val parentId: String? = null,
)

@Serializable
data class TagCreateDto(
    val name: String,
    val color: String? = null,
    val icon: String? = null,
    val description: String? = null,
    val parentId: String? = null,
)

// --- Statistiche ------------------------------------------------------------

@Serializable
data class GroupStatisticsDto(
    val totalItems: Int? = null,
    val totalLocations: Int? = null,
    val totalTags: Int? = null,
    val totalUsers: Int? = null,
    val totalItemPrice: Double? = null,
    val totalWithWarranty: Int? = null,
)
