package com.example.ui.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.AudioHardwareDiagnostics
import com.example.ui.theme.CyanAccentDark
import com.example.ui.theme.GreenActive
import com.example.ui.theme.PurplePrimaryDark

@Composable
fun DiagnosticsDialog(
    diagnostics: AudioHardwareDiagnostics?,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Analytics,
                    contentDescription = null,
                    tint = CyanAccentDark
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text("Audio Engine Diagnostics", style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .testTag("diagnostics_dialog_content")
            ) {
                Text(
                    text = "Real-time hardware pipeline status and audio preprocessing inspection:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                DiagRow("Input Route", diagnostics?.activeMicrophoneName ?: "Default Microphone")
                DiagRow("Capture Source", diagnostics?.selectedSource ?: "UNPROCESSED")
                DiagRow("Sample Rate", "${diagnostics?.sampleRate ?: 48000} Hz")
                DiagRow("Channel Config", diagnostics?.channelConfig ?: "CHANNEL_IN_MONO")
                DiagRow("Encoding", diagnostics?.encoding ?: "PCM 16-BIT")

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Acoustic Pre-Processing Inspection",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = PurplePrimaryDark
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Precision sound measurement requires disabling non-linear phone filters (AGC, Noise Suppression, Echo Cancellation) to preserve true signal dynamics.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                val agcText = when {
                    diagnostics == null -> "Inspecting..."
                    !diagnostics.agcAvailable -> "Not active on device (Clean)"
                    diagnostics.agcDisabled -> "Disabled on session (Verified)"
                    else -> "Active"
                }
                DiagRow("Auto Gain Control (AGC)", agcText, isGood = diagnostics?.agcDisabled != false)

                val nsText = when {
                    diagnostics == null -> "Inspecting..."
                    !diagnostics.nsAvailable -> "Not active on device (Clean)"
                    diagnostics.nsDisabled -> "Disabled on session (Verified)"
                    else -> "Active"
                }
                DiagRow("Noise Suppressor (NS)", nsText, isGood = diagnostics?.nsDisabled != false)

                val aecText = when {
                    diagnostics == null -> "Inspecting..."
                    !diagnostics.aecAvailable -> "Not active on device (Clean)"
                    diagnostics.aecDisabled -> "Disabled on session (Verified)"
                    else -> "Active"
                }
                DiagRow("Echo Canceler (AEC)", aecText, isGood = diagnostics?.aecDisabled != false)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun DiagRow(label: String, value: String, isGood: Boolean = true) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1.1f)
        )
        Text(
            text = value,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            color = if (isGood) GreenActive else CyanAccentDark,
            modifier = Modifier.weight(1.3f)
        )
    }
}
