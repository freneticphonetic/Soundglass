package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.CaptureState
import com.example.dsp.MeasurementUnit
import com.example.dsp.MeterReading
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CyanAccentDark
import com.example.ui.theme.PurplePrimaryDark
import com.example.ui.theme.RedClipping
import java.util.Locale

@Composable
fun DecibelMeterDisplay(
    reading: MeterReading,
    captureState: CaptureState,
    onOpenCalibration: () -> Unit,
    onToggleTimeWeighting: () -> Unit,
    onToggleFreqWeighting: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("decibel_meter_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Badges row: Weighting and Calibration
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Weighting toggles (Frequency & Time)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        modifier = Modifier
                            .testTag("freq_weighting_badge")
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onToggleFreqWeighting() },
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "${reading.frequencyWeighting.shortName}-WEIGHTED",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = PurplePrimaryDark
                        )
                    }

                    Surface(
                        modifier = Modifier
                            .testTag("time_weighting_badge")
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onToggleTimeWeighting() },
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (reading.timeWeighting.name == "FAST") "FAST (125ms)" else "SLOW (1.0s)",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Calibration badge
                Surface(
                    modifier = Modifier
                        .testTag("calibration_badge")
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onOpenCalibration() },
                    color = if (reading.isCalibrated) Color(0xFF1B2F2A) else Color(0xFF2E2718),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Calibration Settings",
                            tint = if (reading.isCalibrated) CyanAccentDark else AmberWarning,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (reading.isCalibrated) {
                                String.format(Locale.US, "CAL (%+.1f dB)", reading.calibrationOffsetDb)
                            } else {
                                "UNCALIBRATED"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (reading.isCalibrated) CyanAccentDark else AmberWarning
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Primary Large Decibel Readout
            val displayValue = if (captureState != CaptureState.IDLE) {
                String.format(Locale.US, "%5.1f", reading.currentLevelDb).trim()
            } else {
                "--.-"
            }

            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = displayValue,
                    modifier = Modifier.testTag("primary_level_readout"),
                    fontSize = 72.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = (-2).sp,
                    color = if (reading.isCurrentlyClipping) RedClipping else MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.padding(bottom = 12.dp)) {
                    Text(
                        text = reading.unit.symbol,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (reading.unit == MeasurementUnit.DBSPL) CyanAccentDark else PurplePrimaryDark
                    )
                    Text(
                        text = if (reading.unit == MeasurementUnit.DBSPL) "ESTIMATED" else "DIGITAL PCM",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Clipping Alert Banner
            AnimatedVisibility(visible = reading.isCurrentlyClipping || reading.sessionClippingCount > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (reading.isCurrentlyClipping) Color(0xFF42151B) else Color(0xFF2B1C1E))
                        .border(1.dp, RedClipping, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Input Clipping Alert",
                            tint = RedClipping,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (reading.isCurrentlyClipping) {
                                "INPUT CLIPPING ACTIVE (Microphone saturated)"
                            } else {
                                "Clipping occurred during session (${reading.sessionClippingCount} samples)"
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = RedClipping
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Measurement statistics cards: MIN, LEQ, MAX, PEAK
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatCard(
                    title = "L_MIN",
                    value = if (captureState != CaptureState.IDLE) String.format(Locale.US, "%.1f", reading.minLevelDb) else "--",
                    unit = reading.unit.symbol,
                    color = AmberWarning,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "L_EQ",
                    value = if (captureState != CaptureState.IDLE) String.format(Locale.US, "%.1f", reading.leqDb) else "--",
                    unit = reading.unit.symbol,
                    color = CyanAccentDark,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "L_MAX",
                    value = if (captureState != CaptureState.IDLE) String.format(Locale.US, "%.1f", reading.maxLevelDb) else "--",
                    unit = reading.unit.symbol,
                    color = RedClipping,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "PEAK",
                    value = if (captureState != CaptureState.IDLE) String.format(Locale.US, "%.1f", reading.samplePeakDbFS) else "--",
                    unit = "dBFS",
                    color = PurplePrimaryDark,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    unit: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = unit,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
