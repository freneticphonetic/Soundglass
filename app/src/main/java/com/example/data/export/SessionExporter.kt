package com.example.data.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.example.data.entity.SavedSession
import com.example.data.entity.TimeSeriesPoint
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SessionExporter {
    private val ISO_DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
    private val READABLE_DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    /**
     * Converts a list of TimeSeriesPoints to compact JSON string for storage.
     */
    fun pointsToJson(points: List<TimeSeriesPoint>): String {
        val array = JSONArray()
        for (p in points) {
            val obj = JSONObject()
            obj.put("t", p.offsetMs)
            obj.put("l", (p.levelDb * 10).toInt() / 10.0) // 1 decimal place
            obj.put("p", (p.peakDbFS * 10).toInt() / 10.0)
            array.put(obj)
        }
        return array.toString()
    }

    /**
     * Parses stored JSON string into list of TimeSeriesPoint.
     */
    fun jsonToPoints(jsonStr: String): List<TimeSeriesPoint> {
        val list = mutableListOf<TimeSeriesPoint>()
        if (jsonStr.isBlank()) return list
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val t = obj.getLong("t")
                val l = obj.getDouble("l").toFloat()
                val p = obj.getDouble("p").toFloat()
                list.add(TimeSeriesPoint(t, l, p))
            }
        } catch (_: Exception) {}
        return list
    }

    /**
     * Generates RFC 4180 compliant CSV content.
     */
    fun generateCsv(session: SavedSession): String {
        val points = jsonToPoints(session.timeSeriesJson)
        val sb = StringBuilder()

        val dateStr = READABLE_DATE_FORMAT.format(Date(session.timestampMillis))

        // Metadata header comments
        sb.append("# Soundglass Measurement Session Export\n")
        sb.append("# Title: ").append(session.title.replace("\n", " ")).append("\n")
        sb.append("# Date: ").append(dateStr).append("\n")
        sb.append("# DurationSeconds: ").append(session.durationMillis / 1000.0).append("\n")
        sb.append("# SampleRate: ").append(session.sampleRate).append(" Hz\n")
        sb.append("# InputRoute: ").append(session.inputRoute).append("\n")
        sb.append("# CaptureSource: ").append(session.captureSource).append("\n")
        sb.append("# TimeWeighting: ").append(session.timeWeighting).append("\n")
        sb.append("# FrequencyWeighting: ").append(session.frequencyWeighting).append("\n")
        sb.append("# Calibrated: ").append(session.isCalibrated).append("\n")
        sb.append("# CalibrationOffsetDb: ").append(session.calibrationOffsetDb).append("\n")
        sb.append("# MinLevel: ").append(session.minLevelDb).append(" ").append(session.unit).append("\n")
        sb.append("# MaxLevel: ").append(session.maxLevelDb).append(" ").append(session.unit).append("\n")
        sb.append("# LeqEquivalent: ").append(session.leqDb).append(" ").append(session.unit).append("\n")
        sb.append("# PeakDbFS: ").append(session.peakDbFS).append(" dBFS\n")
        sb.append("# ClippingOccurred: ").append(session.clippingOccurred).append("\n")
        if (session.notes.isNotBlank()) {
            sb.append("# Notes: ").append(session.notes.replace("\n", " ")).append("\n")
        }
        sb.append("#\n")

        // Columns
        sb.append("TimestampSeconds,Level_").append(session.unit.replace(" ", "")).append(",SamplePeak_dBFS\n")

        // Data rows
        for (pt in points) {
            val sec = String.format(Locale.US, "%.2f", pt.offsetMs / 1000.0)
            val level = String.format(Locale.US, "%.1f", pt.levelDb)
            val peak = String.format(Locale.US, "%.1f", pt.peakDbFS)
            sb.append(sec).append(",").append(level).append(",").append(peak).append("\n")
        }

        return sb.toString()
    }

    /**
     * Generates structured JSON export with explicit schemaVersion.
     */
    fun generateJson(session: SavedSession): String {
        val root = JSONObject()
        root.put("schemaVersion", 1)
        root.put("application", "Soundglass")
        root.put("version", "1.0")

        val sessionObj = JSONObject()
        sessionObj.put("id", session.id)
        sessionObj.put("title", session.title)
        sessionObj.put("notes", session.notes)
        sessionObj.put("timestampIso", ISO_DATE_FORMAT.format(Date(session.timestampMillis)))
        sessionObj.put("timestampMillis", session.timestampMillis)
        sessionObj.put("durationSeconds", session.durationMillis / 1000.0)
        sessionObj.put("sampleRate", session.sampleRate)
        sessionObj.put("captureSource", session.captureSource)
        sessionObj.put("inputRoute", session.inputRoute)

        val weightingObj = JSONObject()
        weightingObj.put("time", session.timeWeighting)
        weightingObj.put("frequency", session.frequencyWeighting)
        sessionObj.put("weighting", weightingObj)

        val calObj = JSONObject()
        calObj.put("isCalibrated", session.isCalibrated)
        calObj.put("offsetDb", session.calibrationOffsetDb)
        sessionObj.put("calibration", calObj)

        val statsObj = JSONObject()
        statsObj.put("minLevel", session.minLevelDb)
        statsObj.put("maxLevel", session.maxLevelDb)
        statsObj.put("leqEquivalent", session.leqDb)
        statsObj.put("samplePeakDbFS", session.peakDbFS)
        statsObj.put("unit", session.unit)
        statsObj.put("clippingOccurred", session.clippingOccurred)
        sessionObj.put("metrics", statsObj)

        val points = jsonToPoints(session.timeSeriesJson)
        val pointsArray = JSONArray()
        for (p in points) {
            val ptObj = JSONObject()
            ptObj.put("timeSeconds", p.offsetMs / 1000.0)
            ptObj.put("level", p.levelDb)
            ptObj.put("peakDbFS", p.peakDbFS)
            pointsArray.put(ptObj)
        }
        sessionObj.put("timeSeries", pointsArray)

        root.put("session", sessionObj)
        return root.toString(2)
    }

    /**
     * Renders a high-resolution PNG chart with labeled axes, gridlines,
     * time-series graph, and min/max/Leq reference lines.
     */
    fun generateChartBitmap(session: SavedSession): Bitmap {
        val width = 1280
        val height = 800
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Dark charcoal background
        canvas.drawColor(Color.parseColor("#12111A"))

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Header card
        paint.color = Color.parseColor("#1B1926")
        canvas.drawRoundRect(RectF(40f, 30f, width - 40f, 130f), 16f, 16f, paint)

        // App & Session Title
        paint.color = Color.parseColor("#BB86FC")
        paint.textSize = 34f
        paint.isFakeBoldText = true
        canvas.drawText("SOUNDGLASS: ${session.title}", 65f, 75f, paint)

        // Date, Duration, and Weighting
        paint.color = Color.parseColor("#A59EB5")
        paint.textSize = 20f
        paint.isFakeBoldText = false
        val dateStr = READABLE_DATE_FORMAT.format(Date(session.timestampMillis))
        val durSec = session.durationMillis / 1000
        val metaStr = "$dateStr | Duration: ${durSec / 60}m ${durSec % 60}s | ${session.frequencyWeighting}-${session.timeWeighting} | Unit: ${session.unit}"
        canvas.drawText(metaStr, 65f, 110f, paint)

        // Chart plot area bounds
        val plotLeft = 100f
        val plotRight = width - 60f
        val plotTop = 170f
        val plotBottom = height - 120f
        val plotWidth = plotRight - plotLeft
        val plotHeight = plotBottom - plotTop

        // Plot background
        paint.color = Color.parseColor("#181722")
        canvas.drawRect(plotLeft, plotTop, plotRight, plotBottom, paint)

        // Plot border
        paint.color = Color.parseColor("#38344D")
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        canvas.drawRect(plotLeft, plotTop, plotRight, plotBottom, paint)

        val points = jsonToPoints(session.timeSeriesJson)

        // Determine Y range (dB)
        var minY = session.minLevelDb
        var maxY = session.maxLevelDb
        if (minY >= maxY) {
            minY -= 10f
            maxY += 10f
        }
        val yMargin = (maxY - minY) * 0.15f
        minY = (minY - yMargin).coerceAtLeast(-100f)
        maxY = (maxY + yMargin).coerceAtMost(140f)
        val yRange = maxY - minY

        // Draw horizontal grid lines and dB labels
        paint.style = Paint.Style.STROKE
        paint.color = Color.parseColor("#262335")
        paint.strokeWidth = 1.5f

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        textPaint.color = Color.parseColor("#736C82")
        textPaint.textSize = 18f
        textPaint.textAlign = Paint.Align.RIGHT

        val numYDivisions = 6
        for (i in 0..numYDivisions) {
            val yVal = minY + (i.toFloat() / numYDivisions) * yRange
            val py = plotBottom - ((yVal - minY) / yRange) * plotHeight
            canvas.drawLine(plotLeft, py, plotRight, py, paint)
            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(String.format(Locale.US, "%.0f", yVal), plotLeft - 10f, py + 6f, textPaint)
        }

        // Draw vertical grid lines and time labels
        val maxDurationMs = if (session.durationMillis > 0) session.durationMillis.toFloat() else 1000f
        val numXDivisions = 5
        textPaint.textAlign = Paint.Align.CENTER
        for (i in 0..numXDivisions) {
            val tMs = (i.toFloat() / numXDivisions) * maxDurationMs
            val px = plotLeft + (tMs / maxDurationMs) * plotWidth
            canvas.drawLine(px, plotTop, px, plotBottom, paint)
            val sec = (tMs / 1000).toInt()
            val timeLabel = "${sec / 60}:${String.format(Locale.US, "%02d", sec % 60)}"
            canvas.drawText(timeLabel, px, plotBottom + 28f, textPaint)
        }

        // Draw Leq reference line (dashed cyan)
        val leqY = plotBottom - ((session.leqDb - minY) / yRange) * plotHeight
        if (leqY in plotTop..plotBottom) {
            val dashPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            dashPaint.color = Color.parseColor("#00E5FF")
            dashPaint.strokeWidth = 2.5f
            dashPaint.style = Paint.Style.STROKE
            dashPaint.pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f)
            canvas.drawLine(plotLeft, leqY, plotRight, leqY, dashPaint)
        }

        // Draw Min & Max reference lines (subtle dashed)
        val maxYLine = plotBottom - ((session.maxLevelDb - minY) / yRange) * plotHeight
        if (maxYLine in plotTop..plotBottom) {
            val dashPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            dashPaint.color = Color.parseColor("#FF5252")
            dashPaint.strokeWidth = 2f
            dashPaint.style = Paint.Style.STROKE
            dashPaint.pathEffect = DashPathEffect(floatArrayOf(8f, 6f), 0f)
            canvas.drawLine(plotLeft, maxYLine, plotRight, maxYLine, dashPaint)
        }

        // Draw time-series graph line
        if (points.isNotEmpty()) {
            val linePaint = Paint(Paint.ANTI_ALIAS_FLAG)
            linePaint.color = Color.parseColor("#BB86FC")
            linePaint.strokeWidth = 3f
            linePaint.style = Paint.Style.STROKE

            val path = Path()
            for (i in points.indices) {
                val pt = points[i]
                val px = plotLeft + (pt.offsetMs.toFloat() / maxDurationMs) * plotWidth
                val py = plotBottom - ((pt.levelDb - minY) / yRange) * plotHeight
                val clampedPy = py.coerceIn(plotTop, plotBottom)
                if (i == 0) {
                    path.moveTo(px, clampedPy)
                } else {
                    path.lineTo(px, clampedPy)
                }
            }
            canvas.drawPath(path, linePaint)
        }

        // Footer summary card
        paint.color = Color.parseColor("#1B1926")
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(RectF(plotLeft, height - 80f, plotRight, height - 20f), 12f, 12f, paint)

        val statTextPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        statTextPaint.textSize = 22f
        statTextPaint.isFakeBoldText = true

        // Min
        statTextPaint.color = Color.parseColor("#FFB300")
        canvas.drawText(String.format(Locale.US, "MIN: %.1f %s", session.minLevelDb, session.unit), plotLeft + 30f, height - 42f, statTextPaint)

        // Leq
        statTextPaint.color = Color.parseColor("#00E5FF")
        canvas.drawText(String.format(Locale.US, "LEQ: %.1f %s", session.leqDb, session.unit), plotLeft + (plotWidth * 0.35f), height - 42f, statTextPaint)

        // Max
        statTextPaint.color = Color.parseColor("#FF5252")
        canvas.drawText(String.format(Locale.US, "MAX: %.1f %s", session.maxLevelDb, session.unit), plotLeft + (plotWidth * 0.70f), height - 42f, statTextPaint)

        return bitmap
    }

    /**
     * Writes CSV bytes directly to output stream.
     */
    fun exportCsvToStream(session: SavedSession, out: OutputStream) {
        val csv = generateCsv(session)
        out.write(csv.toByteArray(Charsets.UTF_8))
        out.flush()
    }

    /**
     * Writes JSON bytes directly to output stream.
     */
    fun exportJsonToStream(session: SavedSession, out: OutputStream) {
        val json = generateJson(session)
        out.write(json.toByteArray(Charsets.UTF_8))
        out.flush()
    }

    /**
     * Compresses bitmap to PNG and writes directly to output stream.
     */
    fun exportPngToStream(session: SavedSession, out: OutputStream) {
        val bitmap = generateChartBitmap(session)
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        out.flush()
    }
}
