package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.entity.SavedSession
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Query("SELECT * FROM saved_sessions ORDER BY timestampMillis DESC")
    fun getAllSessions(): Flow<List<SavedSession>>

    @Query("SELECT * FROM saved_sessions WHERE id = :id")
    fun getSessionById(id: Long): Flow<SavedSession?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SavedSession): Long

    @Update
    suspend fun updateSession(session: SavedSession)

    @Query("DELETE FROM saved_sessions WHERE id = :id")
    suspend fun deleteSessionById(id: Long)

    @Query("DELETE FROM saved_sessions")
    suspend fun deleteAllSessions()
}
