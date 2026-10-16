package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.entity.SavedSession
import com.example.data.export.SessionExporter
import com.example.ui.components.LiveLevelGraph
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CyanAccentDark
import com.example.ui.theme.PurplePrimaryDark
import com.example.ui.theme.RedClipping
import com.example.ui.viewmodel.HistoryViewModel
import com.example.ui.viewmodel.LiveGraphPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedSession by viewModel.selectedSession.collectAsStateWithLifecycle()
    val exportMessage by viewModel.exportMessage.collectAsStateWithLifecycle()

    var showDeleteAllDialog by remember { mutableStateOf(false) }

    LaunchedEffect(exportMessage) {
        exportMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearExportMessage()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (selectedSession != null) {
            // Detail View
            SessionDetailView(
                session = selectedSession!!,
                onBack = { viewModel.selectSession(null) },
                onUpdate = { id, title, notes -> viewModel.updateSessionDetails(id, title, notes) },
                onDelete = { id -> viewModel.deleteSession(id) },
                onExport = { s, uri, format -> viewModel.exportToUri(context, s, uri, format) },
                onShare = { s, format -> viewModel.shareSession(context, s, format) }
            )
        } else {
            // Sessions List
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header with Delete All
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SAVED MEASUREMENTS (${sessions.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (sessions.isNotEmpty()) {
                        IconButton(
                            onClick = { showDeleteAllDialog = true },
                            modifier = Modifier.testTag("delete_all_sessions_button")
                        ) {
                            Icon(imageVector = Icons.Default.DeleteSweep, contentDescription = "Delete All", tint = RedClipping)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("history_search_input"),
                    placeholder = { Text("Filter by title or notes...") },
                    leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (sessions.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                modifier = Modifier.size(54.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (searchQuery.isNotBlank()) "No measurements matching filter" else "No saved measurement sessions yet",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Measurements saved from the Meter screen appear here.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(sessions, key = { it.id }) { session ->
                            SessionListItem(
                                session = session,
                                onClick = { viewModel.selectSession(session) }
                            )
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        )
    }

    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text("Delete All Measurements?") },
            text = { Text("Are you sure you want to permanently delete all ${sessions.size} saved measurement sessions? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteAllSessions()
                        showDeleteAllDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RedClipping)
                ) {
                    Text("Delete All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SessionListItem(
    session: SavedSession,
    onClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    val dateStr = dateFormat.format(Date(session.timestampMillis))
    val durSec = session.durationMillis / 1000

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("session_item_${session.id}")
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = session.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (session.isCalibrated) Color(0xFF1B2F2A) else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = if (session.isCalibrated) "CALIBRATED" else "UNCALIBRATED",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (session.isCalibrated) CyanAccentDark else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "$dateStr | Duration: ${durSec / 60}m ${durSec % 60}s | ${session.frequencyWeighting}-${session.timeWeighting}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatPill("Leq", String.format(Locale.US, "%.1f %s", session.leqDb, session.unit), CyanAccentDark)
                StatPill("Min", String.format(Locale.US, "%.1f", session.minLevelDb), AmberWarning)
                StatPill("Max", String.format(Locale.US, "%.1f", session.maxLevelDb), RedClipping)
                StatPill("Peak", String.format(Locale.US, "%.1f dBFS", session.peakDbFS), PurplePrimaryDark)
            }
        }
    }
}

@Composable
private fun StatPill(label: String, value: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = "$label: ", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = color)
    }
}

@Composable
private fun SessionDetailView(
    session: SavedSession,
    onBack: () -> Unit,
    onUpdate: (id: Long, title: String, notes: String) -> Unit,
    onDelete: (id: Long) -> Unit,
    onExport: (session: SavedSession, uri: Uri, format: String) -> Unit,
    onShare: (session: SavedSession, format: String) -> Unit
) {
    var editMode by remember { mutableStateOf(false) }
    var titleText by remember(session.title) { mutableStateOf(session.title) }
    var notesText by remember(session.notes) { mutableStateOf(session.notes) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // SAF Document Creation Launchers
    var pendingExportFormat by remember { mutableStateOf("csv") }
    val createDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            onExport(session, uri, pendingExportFormat)
        }
    }

    val createJsonDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            onExport(session, uri, "json")
        }
    }

    val createPngDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/png")
    ) { uri ->
        if (uri != null) {
            onExport(session, uri, "png")
        }
    }

    val points = remember(session.timeSeriesJson) {
        SessionExporter.jsonToPoints(session.timeSeriesJson)
    }

    val graphPoints = remember(points) {
        points.map { pt ->
            LiveGraphPoint(pt.offsetMs, pt.levelDb, pt.peakDbFS)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Top Back Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("detail_back_button")) {
                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Session Detail",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { editMode = !editMode }) {
                Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit Title/Notes")
            }
            IconButton(onClick = { showDeleteDialog = true }, modifier = Modifier.testTag("delete_single_session_button")) {
                Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete Session", tint = RedClipping)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Title and notes
        if (editMode) {
            OutlinedTextField(
                value = titleText,
                onValueChange = { titleText = it },
                label = { Text("Session Title") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = notesText,
                onValueChange = { notesText = it },
                label = { Text("Notes") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    onUpdate(session.id, titleText, notesText)
                    editMode = false
                }
            ) {
                Text("Save Changes")
            }
            Spacer(modifier = Modifier.height(12.dp))
        } else {
            Text(
                text = session.title,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (session.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = session.notes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Chart Preview
        if (graphPoints.isNotEmpty()) {
            LiveLevelGraph(
                history = graphPoints,
                unit = if (session.unit.contains("SPL")) com.example.dsp.MeasurementUnit.DBSPL else com.example.dsp.MeasurementUnit.DBFS,
                minLevel = session.minLevelDb,
                maxLevel = session.maxLevelDb,
                leqLevel = session.leqDb
            )
            Spacer(modifier = Modifier.height(14.dp))
        }

        // Metrics breakdown card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "MEASUREMENT SUMMARY",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                DetailRow("Leq (Equivalent Average)", String.format(Locale.US, "%.1f %s", session.leqDb, session.unit), CyanAccentDark)
                DetailRow("Minimum Level (L_min)", String.format(Locale.US, "%.1f %s", session.minLevelDb, session.unit), AmberWarning)
                DetailRow("Maximum Level (L_max)", String.format(Locale.US, "%.1f %s", session.maxLevelDb, session.unit), RedClipping)
                DetailRow("Sample Peak", String.format(Locale.US, "%.1f dBFS", session.peakDbFS), PurplePrimaryDark)
                DetailRow("Duration", "${session.durationMillis / 1000} seconds")
                DetailRow("Weighting", "${session.frequencyWeighting}-Weighting, ${session.timeWeighting}")
                DetailRow("Calibration", if (session.isCalibrated) "Calibrated (${String.format(Locale.US, "%+.1f dB", session.calibrationOffsetDb)})" else "Uncalibrated (dBFS)")
                DetailRow("Sample Rate", "${session.sampleRate} Hz")
                DetailRow("Capture Source", session.captureSource)
                DetailRow("Input Route", session.inputRoute)
                DetailRow("Input Clipping", if (session.clippingOccurred) "Clipping occurred" else "None detected")
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Export Actions Section
        Text(
            text = "EXPORT MEASUREMENT DATA",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    pendingExportFormat = "csv"
                    createDocLauncher.launch("Soundglass_${session.title.replace(" ", "_")}_${session.id}.csv")
                },
                modifier = Modifier.weight(1f).testTag("export_csv_button")
            ) {
                Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("CSV")
            }

            OutlinedButton(
                onClick = {
                    createJsonDocLauncher.launch("Soundglass_${session.title.replace(" ", "_")}_${session.id}.json")
                },
                modifier = Modifier.weight(1f).testTag("export_json_button")
            ) {
                Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("JSON")
            }

            OutlinedButton(
                onClick = {
                    createPngDocLauncher.launch("Soundglass_${session.title.replace(" ", "_")}_${session.id}.png")
                },
                modifier = Modifier.weight(1f).testTag("export_png_button")
            ) {
                Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("PNG Chart")
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Direct Share Button
        FilledTonalButton(
            onClick = { onShare(session, "csv") },
            modifier = Modifier.fillMaxWidth().testTag("share_session_button")
        ) {
            Icon(imageVector = Icons.Default.Share, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Share CSV File")
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Session?") },
            text = { Text("Permanently delete measurement '${session.title}'?") },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete(session.id)
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RedClipping)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String, valueColor: Color = Color.Unspecified) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = if (valueColor != Color.Unspecified) valueColor else MaterialTheme.colorScheme.onSurface
        )
    }
}
