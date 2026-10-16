package com.example.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.data.preferences.SoundglassPreferences
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CyanAccentDark
import com.example.ui.theme.GreenActive
import com.example.ui.theme.PurplePrimaryDark
import com.example.ui.viewmodel.CalibrationState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CalibrationDialog(
    calibrationState: CalibrationState,
    preferences: SoundglassPreferences?,
    activeMicrophoneName: String?,
    onStartCalibration: (targetSpl: Float) -> Unit,
    onApplyCalibration: () -> Unit,
    onResetCalibration: () -> Unit,
    onDismiss: () -> Unit
) {
    var referenceSplInput by remember { mutableStateOf("94.0") }
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = PurplePrimaryDark
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Microphone Calibration",
                    style = MaterialTheme.typography.titleLarge
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Info callout on calibration and hardware limits
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = CyanAccentDark,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Calibrating computes a fixed level offset (dB SPL = dBFS + offset) using a certified 94.0 dB calibrator or reference meter. This provides estimated SPL but does not correct the microphone's hardware frequency response curve.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Current Calibration Status
                if (preferences?.isCalibrated == true) {
                    val dateStr = if (preferences.calibrationDateMs > 0) {
                        dateFormat.format(Date(preferences.calibrationDateMs))
                    } else "Unknown"

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF162520))
                            .padding(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = GreenActive
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Currently Calibrated",
                                fontWeight = FontWeight.Bold,
                                color = GreenActive,
                                fontSize = 13.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Offset: ${String.format(Locale.US, "%+.1f dB", preferences.calibrationOffsetDb)} | Device: ${preferences.calibratedDeviceName}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Calibrated on: $dateStr",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (activeMicrophoneName != null && activeMicrophoneName != preferences.calibratedDeviceName) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Warning: Active microphone ($activeMicrophoneName) differs from calibration profile.",
                                fontSize = 11.sp,
                                color = AmberWarning
                            )
                        }
                    }
                } else {
                    Text(
                        text = "Status: Uncalibrated (showing native dBFS)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AmberWarning,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Reference SPL Input Field
                OutlinedTextField(
                    value = referenceSplInput,
                    onValueChange = { referenceSplInput = it },
                    label = { Text("Reference Meter Reading (dB SPL)") },
                    supportingText = { Text("Standard sound calibrator is 94.0 dB SPL at 1 kHz") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    enabled = !calibrationState.isCalibrating,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("reference_spl_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Progress Indicator during calibration
                if (calibrationState.isCalibrating) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Sampling acoustic reference... ${(calibrationState.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = CyanAccentDark
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { calibrationState.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = CyanAccentDark
                        )
                    }
                }

                // Completed state: show calculated offset
                if (calibrationState.isComplete && calibrationState.calculatedOffset != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF241C33))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "Calculated Offset: ${String.format(Locale.US, "%+.1f dB", calibrationState.calculatedOffset)}",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = PurplePrimaryDark,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Estimated SPL = Measured dBFS + ${String.format(Locale.US, "%.1f", calibrationState.calculatedOffset)}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (calibrationState.isComplete) {
                Button(
                    onClick = onApplyCalibration,
                    modifier = Modifier.testTag("apply_calibration_button")
                ) {
                    Text("Apply Calibration")
                }
            } else if (!calibrationState.isCalibrating) {
                Button(
                    onClick = {
                        val ref = referenceSplInput.toFloatOrNull() ?: 94.0f
                        onStartCalibration(ref)
                    },
                    modifier = Modifier.testTag("start_calibration_button")
                ) {
                    Text("Start Calibration")
                }
            }
        },
        dismissButton = {
            Row {
                if (preferences?.isCalibrated == true && !calibrationState.isCalibrating) {
                    TextButton(
                        onClick = onResetCalibration,
                        colors = ButtonDefaults.textButtonColors(contentColor = AmberWarning)
                    ) {
                        Icon(imageVector = Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                        Text("Reset to Uncalibrated")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    )
}
