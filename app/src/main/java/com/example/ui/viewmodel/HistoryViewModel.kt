package com.example.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.entity.SavedSession
import com.example.data.export.SessionExporter
import com.example.data.repository.SessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

class HistoryViewModel(
    private val sessionRepository: SessionRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedSession = MutableStateFlow<SavedSession?>(null)
    val selectedSession: StateFlow<SavedSession?> = _selectedSession.asStateFlow()

    private val _exportMessage = MutableStateFlow<String?>(null)
    val exportMessage: StateFlow<String?> = _exportMessage.asStateFlow()

    val sessions: StateFlow<List<SavedSession>> = combine(
        sessionRepository.allSessions,
        _searchQuery
    ) { all, query ->
        if (query.isBlank()) {
            all
        } else {
            all.filter {
                it.title.contains(query, ignoreCase = true) ||
                it.notes.contains(query, ignoreCase = true)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectSession(session: SavedSession?) {
        _selectedSession.value = session
    }

    fun updateSessionDetails(sessionId: Long, newTitle: String, newNotes: String) {
        viewModelScope.launch {
            val current = _selectedSession.value
            if (current != null && current.id == sessionId) {
                val updated = current.copy(
                    title = newTitle.trim(),
                    notes = newNotes.trim()
                )
                sessionRepository.updateSession(updated)
                _selectedSession.value = updated
            }
        }
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            sessionRepository.deleteSession(sessionId)
            if (_selectedSession.value?.id == sessionId) {
                _selectedSession.value = null
            }
        }
    }

    fun deleteAllSessions() {
        viewModelScope.launch {
            sessionRepository.deleteAllSessions()
            _selectedSession.value = null
        }
    }

    fun exportToUri(context: Context, session: SavedSession, uri: Uri, format: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    when (format.lowercase()) {
                        "csv" -> SessionExporter.exportCsvToStream(session, out)
                        "json" -> SessionExporter.exportJsonToStream(session, out)
                        "png" -> SessionExporter.exportPngToStream(session, out)
                    }
                }
                _exportMessage.value = "Exported successfully as ${format.uppercase()}"
            } catch (e: Exception) {
                _exportMessage.value = "Export failed: ${e.localizedMessage ?: "Unknown error"}"
            }
        }
    }

    fun shareSession(context: Context, session: SavedSession, format: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cacheDir = File(context.cacheDir, "exports")
                if (!cacheDir.exists()) cacheDir.mkdirs()

                val cleanTitle = session.title.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
                val filename = "Soundglass_${cleanTitle}_${session.id}.${format.lowercase()}"
                val file = File(cacheDir, filename)

                FileOutputStream(file).use { out ->
                    when (format.lowercase()) {
                        "csv" -> SessionExporter.exportCsvToStream(session, out)
                        "json" -> SessionExporter.exportJsonToStream(session, out)
                        "png" -> SessionExporter.exportPngToStream(session, out)
                    }
                }

                val contentUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )

                val mimeType = when (format.lowercase()) {
                    "csv" -> "text/csv"
                    "json" -> "application/json"
                    "png" -> "image/png"
                    else -> "*/*"
                }

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = mimeType
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    putExtra(Intent.EXTRA_SUBJECT, "Soundglass Measurement: ${session.title}")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                withContext(Dispatchers.Main) {
                    val chooser = Intent.createChooser(shareIntent, "Share Measurement Session")
                    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(chooser)
                }
            } catch (e: Exception) {
                _exportMessage.value = "Share failed: ${e.localizedMessage ?: "Unknown error"}"
            }
        }
    }

    fun clearExportMessage() {
        _exportMessage.value = null
    }

    class Factory(
        private val sessionRepository: SessionRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HistoryViewModel(sessionRepository) as T
        }
    }
}
