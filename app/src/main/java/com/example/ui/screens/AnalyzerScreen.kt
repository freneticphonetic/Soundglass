package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audio.CaptureState
import com.example.dsp.SpectrumSmoothing
import com.example.ui.components.MicActiveIndicator
import com.example.ui.components.SpectrogramView
import com.example.ui.components.SpectrumGraph
import com.example.ui.components.WaveformView
import com.example.ui.theme.CyanAccentDark
import com.example.ui.theme.PurplePrimaryDark
import com.example.ui.viewmodel.AnalyzerViewModel

@Composable
fun AnalyzerScreen(
    viewModel: AnalyzerViewModel,
    modifier: Modifier = Modifier
) {
    val captureState by viewModel.captureState.collectAsStateWithLifecycle()
    val waveform by viewModel.waveform.collectAsStateWithLifecycle()
    val fftFrame by viewModel.activeFftFrame.collectAsStateWithLifecycle()
    val isFrozen by viewModel.isFrozen.collectAsStateWithLifecycle()
    val selectedFftSize by viewModel.selectedFftSize.collectAsStateWithLifecycle()
    val selectedSmoothing by viewModel.selectedSmoothing.collectAsStateWithLifecycle()
    val inspectedFreq by viewModel.inspectedFrequencyHz.collectAsStateWithLifecycle()
    val inspectedMag by viewModel.inspectedMagnitudeDbFS.collectAsStateWithLifecycle()
    val spectrogramRows by viewModel.spectrogramHistory.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val captureError by viewModel.errorMessage.collectAsStateWithLifecycle()
    var permissionDenied by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionDenied = !granted
        if (granted) viewModel.ensureCaptureRunning()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(onClick = {
            if (captureState == CaptureState.RECORDING) viewModel.pauseCapture()
            else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) viewModel.ensureCaptureRunning()
            else permission.launch(Manifest.permission.RECORD_AUDIO)
        }, modifier = Modifier.fillMaxWidth()) {
            Text(if (captureState == CaptureState.RECORDING) "Pause Microphone" else "Start / Resume Analyzer")
        }
        if (permissionDenied) Text("Microphone permission is required. Enable it in Android app settings if the permission prompt no longer appears.", color = MaterialTheme.colorScheme.error)
        captureError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(8.dp))
        // Top Toolbar: Freeze button & FFT size chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Freeze / Resume Toggle
            OutlinedButton(
                onClick = { viewModel.toggleFreeze() },
                modifier = Modifier.testTag("freeze_toggle_button"),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (isFrozen) CyanAccentDark else MaterialTheme.colorScheme.onSurface
                )
            ) {
                Icon(
                    imageVector = if (isFrozen) Icons.Default.PlayArrow else Icons.Default.AcUnit,
                    contentDescription = null,
                    tint = if (isFrozen) CyanAccentDark else PurplePrimaryDark
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isFrozen) "Resume Live" else "Freeze Spectrum",
                    fontWeight = FontWeight.Bold
                )
            }

            // FFT Size Chips
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (size in listOf(2048, 4096, 8192)) {
                    val isSelected = (selectedFftSize == size)
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.setFftSize(size) },
                        label = { Text("$size", fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                        modifier = Modifier.testTag("fft_size_$size"),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PurplePrimaryDark.copy(alpha = 0.25f),
                            selectedLabelColor = PurplePrimaryDark
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Spectrum Smoothing Mode Selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Smoothing: ",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (smoothing in SpectrumSmoothing.values()) {
                    val isSelected = (selectedSmoothing == smoothing)
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.setSmoothing(smoothing) },
                        label = { Text(smoothing.displayName.substringBefore(" "), fontSize = 10.sp) },
                        modifier = Modifier.testTag("smoothing_${smoothing.name}"),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyanAccentDark.copy(alpha = 0.20f),
                            selectedLabelColor = CyanAccentDark
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // FFT Spectrum Display
        SpectrumGraph(
            fftFrame = fftFrame,
            inspectedFreqHz = inspectedFreq,
            inspectedMagDbFS = inspectedMag,
            onInspect = { f, m -> viewModel.setInspectionPoint(f, m) }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Scrolling Spectrogram (Waterfall)
        SpectrogramView(
            historyRows = spectrogramRows
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Live Oscilloscope Waveform
        WaveformView(
            waveform = waveform
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}
