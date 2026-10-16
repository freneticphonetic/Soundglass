package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SpectrogramView(
    historyRows: List<FloatArray>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SCROLLING SPECTROGRAM (WATERFALL)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF100E17))
                .testTag("spectrogram_canvas_box")
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                val numRows = historyRows.size
                if (numRows > 0) {
                    val rowHeight = h / numRows.coerceAtLeast(1)

                    for (r in 0 until numRows) {
                        val rowMags = historyRows[r]
                        val numBins = rowMags.size
                        val binWidth = w / numBins.coerceAtLeast(1)
                        val y = (numRows - 1 - r) * rowHeight // newest at top

                        for (b in 0 until numBins) {
                            val mag = rowMags[b]
                            val x = b * binWidth
                            val color = dbToSpectrogramColor(mag)

                            drawRect(
                                color = color,
                                topLeft = Offset(x, y),
                                size = Size(binWidth + 0.5f, rowHeight + 0.5f)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Color Legend
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "-90 dB", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFF12111A),
                                Color(0xFF381460),
                                Color(0xFF8A2BE2),
                                Color(0xFF00E5FF),
                                Color(0xFFFFD700),
                                Color(0xFFFFFFFF)
                            )
                        )
                    )
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = "0 dBFS", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Maps decibel magnitude [-90 dBFS, 0 dBFS] to multi-stage heatmap color.
 */
private fun dbToSpectrogramColor(db: Float): Color {
    val norm = ((db + 90f) / 90f).coerceIn(0f, 1f)
    return when {
        norm < 0.20f -> {
            val t = norm / 0.20f
            Color(
                red = 0.07f + 0.15f * t,
                green = 0.07f + 0.01f * t,
                blue = 0.10f + 0.28f * t
            )
        }
        norm < 0.45f -> {
            val t = (norm - 0.20f) / 0.25f
            Color(
                red = 0.22f + 0.32f * t,
                green = 0.08f + 0.09f * t,
                blue = 0.38f + 0.51f * t
            )
        }
        norm < 0.75f -> {
            val t = (norm - 0.45f) / 0.30f
            Color(
                red = 0.54f - 0.54f * t,
                green = 0.17f + 0.73f * t,
                blue = 0.89f + 0.11f * t
            )
        }
        norm < 0.90f -> {
            val t = (norm - 0.75f) / 0.15f
            Color(
                red = 1.0f * t,
                green = 0.90f - 0.06f * t,
                blue = 1.0f - 1.0f * t
            )
        }
        else -> {
            val t = (norm - 0.90f) / 0.10f
            Color(
                red = 1.0f,
                green = 0.84f + 0.16f * t,
                blue = 1.0f * t
            )
        }
    }
}
