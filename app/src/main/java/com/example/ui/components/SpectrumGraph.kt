package com.example.ui.components

import android.graphics.Color as AndroidColor
import android.graphics.Paint as AndroidPaint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dsp.FftAnalysisFrame
import com.example.ui.theme.CyanAccentDark
import com.example.ui.theme.PurplePrimaryDark
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

@Composable
fun SpectrumGraph(
    fftFrame: FftAnalysisFrame?,
    inspectedFreqHz: Float?,
    inspectedMagDbFS: Float?,
    onInspect: (freqHz: Float?, magDbFS: Float?) -> Unit,
    modifier: Modifier = Modifier
) {
    val minFreq = 20.0f
    val maxFreq = 20000.0f
    val logMin = log10(minFreq)
    val logMax = log10(maxFreq)
    val logRange = logMax - logMin

    val minDb = -100.0f
    val maxDb = 0.0f
    val dbRange = maxDb - minDb

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp)
    ) {
        // Dominant Frequency & Note Header Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Dominant Tone Badge
            if (fftFrame?.peakFrequencyHz != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF1F2B38)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = "Peak Tone",
                            tint = CyanAccentDark,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = String.format(Locale.US, "%.1f Hz", fftFrame.peakFrequencyHz),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = CyanAccentDark
                        )
                        if (fftFrame.musicalNote != null) {
                            val centsStr = if (fftFrame.centsOffset != null && fftFrame.centsOffset != 0) {
                                String.format(Locale.US, " (%+d ct)", fftFrame.centsOffset)
                            } else ""
                            Text(
                                text = "  |  ${fftFrame.musicalNote}$centsStr",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = "No stable tone detected",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Inspected frequency readout if cursor is active
            if (inspectedFreqHz != null && inspectedMagDbFS != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF2C243B)
                ) {
                    Text(
                        text = String.format(Locale.US, "Cursor: %.1f Hz  %.1f dBFS", inspectedFreqHz, inspectedMagDbFS),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = PurplePrimaryDark,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Spectrum Canvas
        val textPaint = remember {
            AndroidPaint().apply {
                isAntiAlias = true
                textSize = 24f
                color = AndroidColor.parseColor("#736C82")
                textAlign = AndroidPaint.Align.CENTER
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .testTag("spectrum_canvas_container")
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 28.dp, end = 8.dp, top = 4.dp, bottom = 18.dp)
                    .pointerInput(fftFrame) {
                        detectTapGestures { offset ->
                            val frame = fftFrame ?: return@detectTapGestures
                            val frac = (offset.x / size.width).coerceIn(0f, 1f)
                            val fHz = 10f.pow(logMin + frac * logRange)
                            val bin = (fHz / frame.sampleRate * frame.fftSize).toInt().coerceIn(0, frame.magnitudesDbFS.size - 1)
                            val mag = frame.magnitudesDbFS[bin]
                            onInspect(fHz, mag)
                        }
                    }
                    .pointerInput(fftFrame) {
                        detectDragGestures { change, _ ->
                            val frame = fftFrame ?: return@detectDragGestures
                            val frac = (change.position.x / size.width).coerceIn(0f, 1f)
                            val fHz = 10f.pow(logMin + frac * logRange)
                            val bin = (fHz / frame.sampleRate * frame.fftSize).toInt().coerceIn(0, frame.magnitudesDbFS.size - 1)
                            val mag = frame.magnitudesDbFS[bin]
                            onInspect(fHz, mag)
                        }
                    }
            ) {
                val w = size.width
                val h = size.height

                // Draw Horizontal dB grid lines (-80, -60, -40, -20, 0 dBFS)
                val dbTicks = listOf(-80f, -60f, -40f, -20f, 0f)
                for (db in dbTicks) {
                    val y = h - ((db - minDb) / dbRange) * h
                    drawLine(
                        color = Color(0xFF262335),
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 1f
                    )
                    drawIntoCanvas { canvas ->
                        textPaint.textAlign = AndroidPaint.Align.RIGHT
                        canvas.nativeCanvas.drawText("${db.toInt()}", -6f, y + 8f, textPaint)
                    }
                }

                // Draw Logarithmic Frequency Grid Lines (50, 100, 200, 500, 1k, 2k, 5k, 10k, 20k Hz)
                val freqTicks = listOf(
                    50f to "50",
                    100f to "100",
                    200f to "200",
                    500f to "500",
                    1000f to "1k",
                    2000f to "2k",
                    5000f to "5k",
                    10000f to "10k",
                    20000f to "20k"
                )

                for ((freq, label) in freqTicks) {
                    val frac = (log10(freq) - logMin) / logRange
                    if (frac in 0f..1f) {
                        val x = frac * w
                        drawLine(
                            color = Color(0xFF262335),
                            start = Offset(x, 0f),
                            end = Offset(x, h),
                            strokeWidth = 1f
                        )
                        drawIntoCanvas { canvas ->
                            textPaint.textAlign = AndroidPaint.Align.CENTER
                            canvas.nativeCanvas.drawText(label, x, h + 22f, textPaint)
                        }
                    }
                }

                // Plot FFT Spectrum curve
                if (fftFrame != null && fftFrame.magnitudesDbFS.isNotEmpty()) {
                    val mags = fftFrame.magnitudesDbFS
                    val freqs = fftFrame.frequencies
                    val linePath = Path()
                    val fillPath = Path()

                    var isFirst = true

                    for (i in 1 until mags.size) {
                        val f = freqs[i]
                        if (f < minFreq || f > maxFreq) continue

                        val fracX = (log10(f) - logMin) / logRange
                        val x = fracX * w
                        val mag = mags[i].coerceIn(minDb, maxDb)
                        val y = h - ((mag - minDb) / dbRange) * h

                        if (isFirst) {
                            linePath.moveTo(x, y)
                            fillPath.moveTo(x, h)
                            fillPath.lineTo(x, y)
                            isFirst = false
                        } else {
                            linePath.lineTo(x, y)
                            fillPath.lineTo(x, y)
                        }
                    }

                    fillPath.lineTo(w, h)
                    fillPath.close()

                    // Gradient fill
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                CyanAccentDark.copy(alpha = 0.20f),
                                Color.Transparent
                            )
                        )
                    )

                    // Line
                    drawPath(
                        path = linePath,
                        color = CyanAccentDark,
                        style = Stroke(width = 2.5f)
                    )

                    // Draw peak frequency indicator
                    if (fftFrame.peakFrequencyHz != null && fftFrame.peakMagnitudeDbFS != null) {
                        val pf = fftFrame.peakFrequencyHz
                        if (pf in minFreq..maxFreq) {
                            val px = ((log10(pf) - logMin) / logRange) * w
                            val py = h - ((fftFrame.peakMagnitudeDbFS.coerceIn(minDb, maxDb) - minDb) / dbRange) * h
                            drawCircle(
                                color = PurplePrimaryDark,
                                radius = 5f,
                                center = Offset(px, py)
                            )
                        }
                    }

                    // Draw touch cursor line
                    if (inspectedFreqHz != null) {
                        val cx = ((log10(inspectedFreqHz) - logMin) / logRange) * w
                        if (cx in 0f..w) {
                            drawLine(
                                color = PurplePrimaryDark,
                                start = Offset(cx, 0f),
                                end = Offset(cx, h),
                                strokeWidth = 1.5f
                            )
                        }
                    }
                }
            }
        }
    }
}
