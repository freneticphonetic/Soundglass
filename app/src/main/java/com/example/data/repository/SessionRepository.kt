package com.example.data.repository

import com.example.data.dao.SessionDao
import com.example.data.entity.SavedSession
import kotlinx.coroutines.flow.Flow

class SessionRepository(private val sessionDao: SessionDao) {
    val allSessions: Flow<List<SavedSession>> = sessionDao.getAllSessions()

    fun getSessionById(id: Long): Flow<SavedSession?> = sessionDao.getSessionById(id)

    suspend fun saveSession(session: SavedSession): Long = sessionDao.insertSession(session)

    suspend fun updateSession(session: SavedSession) = sessionDao.updateSession(session)

    suspend fun deleteSession(id: Long) = sessionDao.deleteSessionById(id)

    suspend fun deleteAllSessions() = sessionDao.deleteAllSessions()
}
