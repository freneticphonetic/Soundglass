package com.example.ui.screens

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dsp.WaveformType
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CyanAccentDark
import com.example.ui.theme.PurplePrimaryDark
import com.example.ui.theme.RedClipping
import com.example.ui.viewmodel.GeneratorViewModel
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

@Composable
fun GeneratorScreen(
    viewModel: GeneratorViewModel,
    modifier: Modifier = Modifier
) {
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val selectedWaveform by viewModel.selectedWaveform.collectAsStateWithLifecycle()
    val frequencyHz by viewModel.frequencyHz.collectAsStateWithLifecycle()
    val amplitudeDbFS by viewModel.amplitudeDbFS.collectAsStateWithLifecycle()
    val currentSweepFreq by viewModel.currentSweepFrequencyHz.collectAsStateWithLifecycle()
    val sweepProgress by viewModel.sweepProgress.collectAsStateWithLifecycle()

    val sweepStartHz by viewModel.sweepStartHz.collectAsStateWithLifecycle()
    val sweepEndHz by viewModel.sweepEndHz.collectAsStateWithLifecycle()
    val sweepDurationSec by viewModel.sweepDurationSec.collectAsStateWithLifecycle()

    var freqInputText by remember(frequencyHz) {
        mutableStateOf(String.format(Locale.US, "%.1f", frequencyHz))
    }

    var sweepStartText by remember(sweepStartHz) {
        mutableStateOf(String.format(Locale.US, "%.1f", sweepStartHz))
    }
    var sweepEndText by remember(sweepEndHz) {
        mutableStateOf(String.format(Locale.US, "%.1f", sweepEndHz))
    }
    var sweepDurationText by remember(sweepDurationSec) {
        mutableStateOf(String.format(Locale.US, "%.1f", sweepDurationSec))
    }

    val playbackError by viewModel.errorMessage.collectAsStateWithLifecycle()
    val isSweep = (selectedWaveform == WaveformType.SWEEP)
    val isNoise = (selectedWaveform == WaveformType.WHITE_NOISE || selectedWaveform == WaveformType.PINK_NOISE)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        playbackError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        // Mutual Exclusivity Notice Callout
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = CyanAccentDark,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Acoustic safety: Tone generator and microphone capture are mutually exclusive. Playing audio will pause measurement capture.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Waveform Selector Cards
        Text(
            text = "SYNTHESIS WAVEFORM",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.Start)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            for (wf in listOf(WaveformType.SINE, WaveformType.TRIANGLE, WaveformType.SQUARE, WaveformType.SAWTOOTH)) {
                val isSelected = (selectedWaveform == wf)
                FilterChip(
                    selected = isSelected,
                    onClick = { viewModel.setWaveform(wf) },
                    label = { Text(wf.name.substring(0, 3), fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                    modifier = Modifier.weight(1f).testTag("waveform_${wf.name}"),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PurplePrimaryDark.copy(alpha = 0.25f),
                        selectedLabelColor = PurplePrimaryDark
                    )
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            for (wf in listOf(WaveformType.WHITE_NOISE, WaveformType.PINK_NOISE, WaveformType.SWEEP)) {
                val isSelected = (selectedWaveform == wf)
                val label = when (wf) {
                    WaveformType.WHITE_NOISE -> "WHITE"
                    WaveformType.PINK_NOISE -> "PINK (1/f)"
                    WaveformType.SWEEP -> "SWEEP"
                    else -> wf.name
                }
                FilterChip(
                    selected = isSelected,
                    onClick = { viewModel.setWaveform(wf) },
                    label = { Text(label, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                    modifier = Modifier.weight(1f).testTag("waveform_${wf.name}"),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CyanAccentDark.copy(alpha = 0.20f),
                        selectedLabelColor = CyanAccentDark
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Frequency Control Card (Hidden if Noise)
        if (!isNoise && !isSweep) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "FREQUENCY",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = String.format(Locale.US, "%.1f Hz", frequencyHz),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace,
                            color = CyanAccentDark
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Logarithmic frequency slider: 20 Hz (log=1.301) to 20000 Hz (log=4.301)
                    val logMin = log10(20.0)
                    val logMax = log10(20000.0)
                    val logVal = log10(frequencyHz.coerceIn(20.0, 20000.0))

                    Slider(
                        value = logVal.toFloat(),
                        onValueChange = { newLog ->
                            val newFreq = 10.0.pow(newLog.toDouble())
                            viewModel.setFrequency(newFreq)
                        },
                        valueRange = logMin.toFloat()..logMax.toFloat(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("frequency_slider"),
                        colors = SliderDefaults.colors(
                            thumbColor = CyanAccentDark,
                            activeTrackColor = CyanAccentDark
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Direct Numeric Entry & Presets
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = freqInputText,
                            onValueChange = { txt ->
                                freqInputText = txt
                                val parsed = txt.toDoubleOrNull()
                                if (parsed != null && parsed in 10.0..20000.0) {
                                    viewModel.setFrequency(parsed)
                                }
                            },
                            label = { Text("Exact Hz") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("frequency_numeric_input")
                        )

                        // Quick Presets
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            for ((f, label) in listOf(440.0 to "A4", 1000.0 to "1k", 10000.0 to "10k")) {
                                OutlinedButton(
                                    onClick = { viewModel.setFrequency(f) },
                                    modifier = Modifier.testTag("freq_preset_$label")
                                ) {
                                    Text(label, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Sweep Configuration Card
        if (isSweep) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "LOGARITHMIC SINE SWEEP PARAMETERS",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = sweepStartText,
                            onValueChange = {
                                sweepStartText = it
                                it.toDoubleOrNull()?.let { start ->
                                    viewModel.setSweepParameters(start, sweepEndHz, sweepDurationSec)
                                }
                            },
                            label = { Text("Start Hz") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f).testTag("sweep_start_input")
                        )

                        OutlinedTextField(
                            value = sweepEndText,
                            onValueChange = {
                                sweepEndText = it
                                it.toDoubleOrNull()?.let { end ->
                                    viewModel.setSweepParameters(sweepStartHz, end, sweepDurationSec)
                                }
                            },
                            label = { Text("End Hz") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f).testTag("sweep_end_input")
                        )

                        OutlinedTextField(
                            value = sweepDurationText,
                            onValueChange = {
                                sweepDurationText = it
                                it.toDoubleOrNull()?.let { dur ->
                                    viewModel.setSweepParameters(sweepStartHz, sweepEndHz, dur)
                                }
                            },
                            label = { Text("Duration (s)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f).testTag("sweep_duration_input")
                        )
                    }

                    if (isPlaying) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Instantaneous: ${String.format(Locale.US, "%.1f Hz", currentSweepFreq)}",
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = CyanAccentDark
                            )
                            Text(
                                text = "${(sweepProgress * 100).toInt()}%",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { sweepProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = CyanAccentDark
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Output Amplitude (Digital Output Level dBFS) Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.VolumeUp, contentDescription = null, tint = PurplePrimaryDark)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "DIGITAL OUTPUT LEVEL",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = String.format(Locale.US, "%.1f dBFS", amplitudeDbFS),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = if (amplitudeDbFS > -6.0f) RedClipping else PurplePrimaryDark
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Slider(
                    value = amplitudeDbFS,
                    onValueChange = { viewModel.setAmplitudeDbFS(it) },
                    valueRange = -60.0f..0.0f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("amplitude_slider"),
                    colors = SliderDefaults.colors(
                        thumbColor = if (amplitudeDbFS > -6.0f) RedClipping else PurplePrimaryDark,
                        activeTrackColor = if (amplitudeDbFS > -6.0f) RedClipping else PurplePrimaryDark
                    )
                )

                // High Volume Warning
                AnimatedVisibility(visible = amplitudeDbFS > -6.0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF381B20))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = RedClipping, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "High output level! Reduce volume to protect headphones and hearing.",
                                fontSize = 11.sp,
                                color = RedClipping
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Big Primary Play / Stop Button
        Button(
            onClick = { viewModel.togglePlayback() },
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .testTag("generator_play_stop_button"),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isPlaying) RedClipping else PurplePrimaryDark
            )
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = if (isPlaying) "STOP GENERATOR" else "START TONE GENERATOR",
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
