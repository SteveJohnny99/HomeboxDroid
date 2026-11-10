package it.stevej.homeboxdroid.data.cache

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Nodo dell'albero (flat): parentId null = radice. position preserva l'ordine. */
@Entity(tableName = "nodes")
data class NodeEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val parentId: String?,
    val position: Int,
)

/** Dettaglio entita' serializzato (EntityOutDto come JSON) per il dettaglio offline. */
@Entity(tableName = "details")
data class DetailEntity(
    @PrimaryKey val id: String,
    val json: String,
)

@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey val id: String,
    val name: String,
    val color: String?,
)

@Entity(tableName = "types")
data class TypeEntity(
    @PrimaryKey val id: String,
    val name: String,
    val isLocation: Boolean,
    val icon: String?,
)

/** Statistiche: riga singola con id fisso 0. */
@Entity(tableName = "stats")
data class StatsEntity(
    @PrimaryKey val id: Int = 0,
    val totalItems: Int,
    val totalLocations: Int,
    val totalTags: Int,
    val totalUsers: Int,
    val totalItemPrice: Double,
    val totalWithWarranty: Int,
)
