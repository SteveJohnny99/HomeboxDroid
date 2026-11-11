package it.stevej.homeboxdroid.data.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface CacheDao {

    // --- Nodi albero --------------------------------------------------------

    @Query("SELECT * FROM nodes ORDER BY position")
    suspend fun allNodes(): List<NodeEntity>

    @Query("DELETE FROM nodes")
    suspend fun clearNodes()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNodes(nodes: List<NodeEntity>)

    @Transaction
    suspend fun replaceNodes(nodes: List<NodeEntity>) {
        clearNodes()
        insertNodes(nodes)
    }

    // --- Dettagli -----------------------------------------------------------

    @Query("SELECT json FROM details WHERE id = :id")
    suspend fun detailJson(id: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putDetail(detail: DetailEntity)

    // --- Tag ----------------------------------------------------------------

    @Query("SELECT * FROM tags ORDER BY name")
    suspend fun allTags(): List<TagEntity>

    @Query("DELETE FROM tags")
    suspend fun clearTags()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTags(tags: List<TagEntity>)

    @Transaction
    suspend fun replaceTags(tags: List<TagEntity>) {
        clearTags()
        insertTags(tags)
    }

    // --- Tipi ---------------------------------------------------------------

    @Query("SELECT * FROM types")
    suspend fun allTypes(): List<TypeEntity>

    @Query("DELETE FROM types")
    suspend fun clearTypes()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTypes(types: List<TypeEntity>)

    @Transaction
    suspend fun replaceTypes(types: List<TypeEntity>) {
        clearTypes()
        insertTypes(types)
    }

    // --- Statistiche --------------------------------------------------------

    @Query("SELECT * FROM stats WHERE id = 0")
    suspend fun stats(): StatsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putStats(stats: StatsEntity)

    // --- Coda scritture offline ---------------------------------------------

    @Query("SELECT * FROM pending_ops ORDER BY seq")
    suspend fun allPending(): List<PendingOpEntity>

    @Query("SELECT COUNT(*) FROM pending_ops")
    suspend fun pendingCount(): Int

    @Query("SELECT COUNT(*) FROM pending_ops")
    fun pendingCountFlow(): kotlinx.coroutines.flow.Flow<Int>

    @Insert
    suspend fun insertPending(op: PendingOpEntity): Long

    @Query("DELETE FROM pending_ops WHERE seq = :seq")
    suspend fun deletePending(seq: Long)

    @Query("UPDATE pending_ops SET lastError = :error WHERE seq = :seq")
    suspend fun setPendingError(seq: Long, error: String?)

    @Query("UPDATE pending_ops SET payload = :payload, tempId = :tempId WHERE seq = :seq")
    suspend fun updatePendingPayload(seq: Long, payload: String, tempId: String?)

    @Query("DELETE FROM pending_ops")
    suspend fun clearPending()

    @Query("UPDATE nodes SET id = :realId WHERE id = :tempId")
    suspend fun remapNodeId(tempId: String, realId: String)

    @Query("UPDATE nodes SET parentId = :realId WHERE parentId = :tempId")
    suspend fun remapNodeParent(tempId: String, realId: String)

    @Query("DELETE FROM nodes WHERE id = :id")
    suspend fun deleteNode(id: String)

    @Query("UPDATE nodes SET name = :name WHERE id = :id")
    suspend fun renameNode(id: String, name: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNode(node: NodeEntity)

    @Query("SELECT COALESCE(MAX(position), 0) FROM nodes")
    suspend fun maxNodePosition(): Int

    // --- Pulizia (al logout) ------------------------------------------------

    @Query("DELETE FROM details")
    suspend fun clearDetails()

    @Query("DELETE FROM stats")
    suspend fun clearStats()

    @Transaction
    suspend fun clearAll() {
        clearNodes()
        clearDetails()
        clearTags()
        clearTypes()
        clearStats()
        clearPending()
    }
}
