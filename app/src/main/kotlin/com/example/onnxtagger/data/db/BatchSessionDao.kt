package com.example.onnxtagger.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BatchSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: BatchSessionEntity)

    @Query("SELECT * FROM batch_sessions ORDER BY timestamp DESC LIMIT :limit")
    fun queryRecent(limit: Int = 20): Flow<List<BatchSessionEntity>>

    @Query("SELECT * FROM batch_sessions WHERE isComplete = 0 ORDER BY timestamp DESC")
    fun queryResumable(): Flow<List<BatchSessionEntity>>

    @Query("DELETE FROM batch_sessions WHERE id = :id")
    suspend fun delete(id: String)

    @Query("""
        DELETE FROM batch_sessions
        WHERE id NOT IN (SELECT id FROM batch_sessions ORDER BY timestamp DESC LIMIT 20)
    """)
    suspend fun pruneOldSessions()
}
