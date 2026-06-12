package com.puretv.twitch.tv.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/**
 * SECTION 09.1 — TV counterpart of the phone app's `PureTvDatabase`. Same
 * five-table schema (offline browse cache, watch history, search history,
 * emote cache) — duplicated rather than shared because Room database
 * classes/DAOs are generated per-module by KSP and `core` is a pure
 * KMP module without an Android `Context`/Room dependency.
 */

@Entity(tableName = "cached_streams")
data class CachedStream(
    @PrimaryKey val channelLogin: String,
    val channelDisplayName: String,
    val title: String,
    val gameName: String,
    val viewerCount: Long,
    val thumbnailUrl: String,
    val fetchedAtEpochMs: Long,
)

@Entity(tableName = "cached_channels")
data class CachedChannel(
    @PrimaryKey val channelLogin: String,
    val id: String,
    val displayName: String,
    val description: String,
    val profileImageUrl: String,
    val fetchedAtEpochMs: Long,
)

@Entity(tableName = "cached_emotes")
data class CachedEmote(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val scopeKey: String, // "global" or a channel id
    val code: String,
    val imageUrl: String,
    val provider: String,
    val animated: Boolean,
    val fetchedAtEpochMs: Long,
)

@Entity(tableName = "watch_history")
data class WatchHistoryEntry(
    @PrimaryKey val channelLogin: String,
    val channelDisplayName: String,
    val lastWatchedEpochMs: Long,
    val totalWatchTimeMs: Long,
)

@Entity(tableName = "search_history")
data class SearchHistoryEntry(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val query: String,
    val searchedAtEpochMs: Long,
)

@Dao
interface CachedStreamDao {
    @Query("SELECT * FROM cached_streams ORDER BY viewerCount DESC")
    fun observeAll(): Flow<List<CachedStream>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(streams: List<CachedStream>)

    @Query("DELETE FROM cached_streams WHERE fetchedAtEpochMs < :olderThanEpochMs")
    suspend fun pruneStaleEntries(olderThanEpochMs: Long)
}

@Dao
interface CachedChannelDao {
    @Query("SELECT * FROM cached_channels WHERE channelLogin = :login LIMIT 1")
    suspend fun findByLogin(login: String): CachedChannel?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(channel: CachedChannel)
}

@Dao
interface CachedEmoteDao {
    @Query("SELECT * FROM cached_emotes WHERE scopeKey = :scopeKey")
    suspend fun forScope(scopeKey: String): List<CachedEmote>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(emotes: List<CachedEmote>)

    @Query("DELETE FROM cached_emotes WHERE scopeKey = :scopeKey")
    suspend fun clearScope(scopeKey: String)
}

@Dao
interface WatchHistoryDao {
    @Query("SELECT * FROM watch_history ORDER BY lastWatchedEpochMs DESC LIMIT 50")
    fun observeRecent(): Flow<List<WatchHistoryEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: WatchHistoryEntry)

    @Query("UPDATE watch_history SET totalWatchTimeMs = totalWatchTimeMs + :deltaMs, lastWatchedEpochMs = :nowEpochMs WHERE channelLogin = :login")
    suspend fun addWatchTime(login: String, deltaMs: Long, nowEpochMs: Long)
}

@Dao
interface SearchHistoryDao {
    @Query("SELECT * FROM search_history ORDER BY searchedAtEpochMs DESC LIMIT 20")
    fun observeRecent(): Flow<List<SearchHistoryEntry>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: SearchHistoryEntry)

    @Query("DELETE FROM search_history")
    suspend fun clear()
}

@Database(
    entities = [
        CachedStream::class,
        CachedChannel::class,
        CachedEmote::class,
        WatchHistoryEntry::class,
        SearchHistoryEntry::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class PureTvTvDatabase : RoomDatabase() {
    abstract fun cachedStreamDao(): CachedStreamDao
    abstract fun cachedChannelDao(): CachedChannelDao
    abstract fun cachedEmoteDao(): CachedEmoteDao
    abstract fun watchHistoryDao(): WatchHistoryDao
    abstract fun searchHistoryDao(): SearchHistoryDao

    companion object {
        const val DB_NAME = "puretv_tv.db"
    }
}
