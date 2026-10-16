package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audio.CaptureState
import com.example.dsp.FrequencyWeighting
import com.example.dsp.TimeWeighting
import com.example.ui.components.DecibelMeterDisplay
import com.example.ui.components.LiveLevelGraph
import com.example.ui.components.MicActiveIndicator
import com.example.ui.dialogs.CalibrationDialog
import com.example.ui.dialogs.DiagnosticsDialog
import com.example.ui.dialogs.SaveSessionDialog
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CyanAccentDark
import com.example.ui.theme.PurplePrimaryDark
import com.example.ui.theme.RedClipping
import com.example.ui.viewmodel.MeterViewModel
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun MeterScreen(
    viewModel: MeterViewModel,
    onNavigateToDocs: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val captureState by viewModel.captureState.collectAsStateWithLifecycle()
    val reading by viewModel.meterReading.collectAsStateWithLifecycle()
    val history by viewModel.liveHistory.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val diagnostics by viewModel.diagnostics.collectAsStateWithLifecycle()
    val calibrationState by viewModel.calibrationState.collectAsStateWithLifecycle()
    val isMicMuted by viewModel.isMicrophoneMuted.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val calibrationWarning by viewModel.calibrationWarning.collectAsStateWithLifecycle()

    var showCalibrationDialog by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var showDiagnosticsDialog by remember { mutableStateOf(false) }
    var permissionDeniedState by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            permissionDeniedState = false
            viewModel.startMeasurement()
        } else {
            permissionDeniedState = true
        }
    }

    fun checkAndStart() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            viewModel.startMeasurement()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar: Active Indicator and Timer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MicActiveIndicator(
                    captureState = captureState,
                    isMicrophoneMuted = isMicMuted,
                    sourceLabel = diagnostics?.selectedSource,
                    onClickDiagnostics = { showDiagnosticsDialog = true }
                )

                // Captured Duration Badge
                val sec = reading.sessionDurationMs / 1000
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = null,
                            tint = CyanAccentDark,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = String.format(Locale.US, "%02d:%02d", sec / 60, sec % 60),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Permission Denied Warning Banner
            if (permissionDeniedState) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF381D1E))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = RedClipping)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Microphone Permission Required",
                                fontWeight = FontWeight.Bold,
                                color = RedClipping
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Soundglass processes audio completely offline on your device to calculate acoustic levels. Microphone access is required to capture sound.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                            colors = ButtonDefaults.buttonColors(containerColor = RedClipping)
                        ) {
                            Text("Grant Permission")
                        }
                    }
                }
            }

            // Error Message Banner
            if (errorMessage != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF382318))
                ) {
                    Text(
                        text = errorMessage ?: "",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = AmberWarning
                    )
                }
            }

            // Calibration Invalidation Warning Banner
            if (calibrationWarning != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .testTag("calibration_warning_banner"),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF332014))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = AmberWarning)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Calibration Inactive (Reverted to dBFS)",
                                fontWeight = FontWeight.Bold,
                                color = AmberWarning,
                                fontSize = 13.sp
                            )
                            Text(
                                text = calibrationWarning ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // Primary Decibel Meter Display
            DecibelMeterDisplay(
                reading = reading,
                captureState = captureState,
                onOpenCalibration = { showCalibrationDialog = true },
                onToggleTimeWeighting = {
                    val next = if (reading.timeWeighting == TimeWeighting.FAST) TimeWeighting.SLOW else TimeWeighting.FAST
                    viewModel.setTimeWeighting(next)
                },
                onToggleFreqWeighting = {
                    val next = if (reading.frequencyWeighting == FrequencyWeighting.A) FrequencyWeighting.Z else FrequencyWeighting.A
                    viewModel.setFrequencyWeighting(next)
                }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Live Level Rolling Graph
            LiveLevelGraph(
                history = history,
                unit = reading.unit,
                minLevel = reading.minLevelDb,
                maxLevel = reading.maxLevelDb,
                leqLevel = reading.leqDb
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Primary Measurement Controls Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (captureState) {
                    CaptureState.IDLE -> {
                        Button(
                            onClick = { checkAndStart() },
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp)
                                .testTag("start_measurement_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = PurplePrimaryDark)
                        ) {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Start Measurement", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    CaptureState.RECORDING -> {
                        FilledTonalButton(
                            onClick = { viewModel.pauseMeasurement() },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("pause_measurement_button")
                        ) {
                            Icon(imageVector = Icons.Default.Pause, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Pause")
                        }

                        Button(
                            onClick = { showSaveDialog = true },
                            modifier = Modifier
                                .weight(1.2f)
                                .height(50.dp)
                                .testTag("save_session_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = CyanAccentDark)
                        ) {
                            Icon(imageVector = Icons.Default.Bookmark, contentDescription = null, tint = Color(0xFF00363D))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save Session", color = Color(0xFF00363D), fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { viewModel.resetMeasurement() },
                            modifier = Modifier
                                .width(50.dp)
                                .height(50.dp)
                                .testTag("reset_measurement_button")
                        ) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = "Reset", modifier = Modifier.size(18.dp))
                        }
                    }
                    CaptureState.PAUSED -> {
                        Button(
                            onClick = { viewModel.resumeMeasurement() },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("resume_measurement_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = PurplePrimaryDark)
                        ) {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Resume")
                        }

                        Button(
                            onClick = { showSaveDialog = true },
                            modifier = Modifier
                                .weight(1.2f)
                                .height(50.dp)
                                .testTag("save_session_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = CyanAccentDark)
                        ) {
                            Icon(imageVector = Icons.Default.Bookmark, contentDescription = null, tint = Color(0xFF00363D))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save Session", color = Color(0xFF00363D), fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { viewModel.resetMeasurement() },
                            modifier = Modifier
                                .width(50.dp)
                                .height(50.dp)
                                .testTag("reset_measurement_button")
                        ) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = "Reset", modifier = Modifier.size(18.dp))
                        }
                    }
                    CaptureState.ERROR -> {
                        Button(
                            onClick = { checkAndStart() },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AmberWarning)
                        ) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Retry Capture", color = Color.Black)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action links row: Calibration & DSP Docs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutlinedButton(
                    onClick = { showCalibrationDialog = true },
                    modifier = Modifier.testTag("open_calibration_button")
                ) {
                    Text("Calibrate SPL")
                }

                OutlinedButton(
                    onClick = { showDiagnosticsDialog = true },
                    modifier = Modifier.testTag("open_diagnostics_button")
                ) {
                    Text("Diagnostics")
                }

                OutlinedButton(
                    onClick = onNavigateToDocs,
                    modifier = Modifier.testTag("open_docs_button")
                ) {
                    Text("DSP Info")
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

    // Dialogs
    if (showCalibrationDialog) {
        CalibrationDialog(
            calibrationState = calibrationState,
            preferences = preferences,
            activeMicrophoneName = diagnostics?.activeMicrophoneName,
            onStartCalibration = { targetSpl -> viewModel.startCalibration(targetSpl) },
            onApplyCalibration = {
                viewModel.applyCalibration()
                showCalibrationDialog = false
                scope.launch { snackbarHostState.showSnackbar("Microphone calibrated successfully.") }
            },
            onResetCalibration = {
                viewModel.resetCalibrationToDefaults()
                showCalibrationDialog = false
                scope.launch { snackbarHostState.showSnackbar("Calibration reset to uncalibrated dBFS.") }
            },
            onDismiss = {
                viewModel.cancelCalibration()
                showCalibrationDialog = false
            }
        )
    }

    if (showSaveDialog) {
        SaveSessionDialog(
            reading = reading,
            onSave = { title, notes ->
                scope.launch {
                    val id = viewModel.saveSession(title, notes)
                    showSaveDialog = false
                    snackbarHostState.showSnackbar("Saved session #$id to measurement history.")
                }
            },
            onDismiss = { showSaveDialog = false }
        )
    }

    if (showDiagnosticsDialog) {
        DiagnosticsDialog(
            diagnostics = diagnostics,
            onDismiss = { showDiagnosticsDialog = false }
        )
    }
}
