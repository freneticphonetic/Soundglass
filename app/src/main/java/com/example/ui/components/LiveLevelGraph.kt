package com.example.ui.components

import android.graphics.Color as AndroidColor
import android.graphics.DashPathEffect
import android.graphics.Paint as AndroidPaint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dsp.MeasurementUnit
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CyanAccentDark
import com.example.ui.theme.PurplePrimaryDark
import com.example.ui.theme.RedClipping
import com.example.ui.viewmodel.LiveGraphPoint
import java.util.Locale

@Composable
fun LiveLevelGraph(
    history: List<LiveGraphPoint>,
    unit: MeasurementUnit,
    minLevel: Float,
    maxLevel: Float,
    leqLevel: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .testTag("live_level_graph")
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(8.dp)
    ) {
        if (history.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Awaiting measurement data...",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
        }

        val textPaint = remember {
            AndroidPaint().apply {
                isAntiAlias = true
                textSize = 28f
                color = AndroidColor.parseColor("#736C82")
                textAlign = AndroidPaint.Align.RIGHT
            }
        }

        Canvas(modifier = Modifier.fillMaxSize().padding(start = 32.dp, end = 8.dp, top = 8.dp, bottom = 18.dp)) {
            val w = size.width
            val h = size.height

            // Determine dynamic dB axis range
            val isSpl = (unit == MeasurementUnit.DBSPL)
            val rangeMin = if (isSpl) 30f else -90f
            val rangeMax = if (isSpl) 120f else 0f
            val rangeDb = rangeMax - rangeMin

            // Draw horizontal grid lines & labels
            val stepDb = if (isSpl) 20f else 20f
            var dbTick = rangeMin
            while (dbTick <= rangeMax) {
                val y = h - ((dbTick - rangeMin) / rangeDb) * h
                drawLine(
                    color = Color(0xFF262335),
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = 1f
                )

                val label = String.format(Locale.US, "%.0f", dbTick)
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawText(label, -8f, y + 8f, textPaint)
                }

                dbTick += stepDb
            }

            if (history.size >= 2) {
                // Plot dashed lines for Min, Max, Leq
                val leqY = h - ((leqLevel - rangeMin) / rangeDb) * h
                if (leqY in 0f..h) {
                    drawLine(
                        color = CyanAccentDark.copy(alpha = 0.7f),
                        start = Offset(0f, leqY),
                        end = Offset(w, leqY),
                        strokeWidth = 2f
                    )
                }

                val maxY = h - ((maxLevel - rangeMin) / rangeDb) * h
                if (maxY in 0f..h) {
                    drawLine(
                        color = RedClipping.copy(alpha = 0.5f),
                        start = Offset(0f, maxY),
                        end = Offset(w, maxY),
                        strokeWidth = 1.5f
                    )
                }

                val minY = h - ((minLevel - rangeMin) / rangeDb) * h
                if (minY in 0f..h) {
                    drawLine(
                        color = AmberWarning.copy(alpha = 0.5f),
                        start = Offset(0f, minY),
                        end = Offset(w, minY),
                        strokeWidth = 1.5f
                    )
                }

                // Plot live graph line and fill path
                val linePath = Path()
                val fillPath = Path()

                val pointCount = history.size
                for (i in 0 until pointCount) {
                    val pt = history[i]
                    val x = (i.toFloat() / (pointCount - 1).coerceAtLeast(1)) * w
                    val y = (h - ((pt.levelDb - rangeMin) / rangeDb) * h).coerceIn(0f, h)

                    if (i == 0) {
                        linePath.moveTo(x, y)
                        fillPath.moveTo(x, h)
                        fillPath.lineTo(x, y)
                    } else {
                        linePath.lineTo(x, y)
                        fillPath.lineTo(x, y)
                    }
                }
                fillPath.lineTo(w, h)
                fillPath.close()

                // Draw gradient fill
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            PurplePrimaryDark.copy(alpha = 0.25f),
                            Color.Transparent
                        )
                    )
                )

                // Draw graph line
                drawPath(
                    path = linePath,
                    color = PurplePrimaryDark,
                    style = Stroke(width = 3f)
                )

                // Current point cursor
                val lastPt = history.last()
                val lastX = w
                val lastY = (h - ((lastPt.levelDb - rangeMin) / rangeDb) * h).coerceIn(0f, h)
                drawCircle(
                    color = CyanAccentDark,
                    radius = 4.5f,
                    center = Offset(lastX, lastY)
                )
            }
        }
    }
}
