package it.stevej.homeboxdroid.data.cache

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        NodeEntity::class,
        DetailEntity::class,
        TagEntity::class,
        TypeEntity::class,
        StatsEntity::class,
        PendingOpEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class HomeboxDatabase : RoomDatabase() {
    abstract fun cacheDao(): CacheDao
}
