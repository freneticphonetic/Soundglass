package com.example

import com.example.data.entity.SavedSession
import com.example.data.entity.TimeSeriesPoint
import com.example.data.export.SessionExporter
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SessionExportTest {

    @Test
    fun testTimeSeriesJsonSerializationRoundTrip() {
        val points = listOf(
            TimeSeriesPoint(0L, 55.4f, -12.3f),
            TimeSeriesPoint(250L, 58.1f, -10.5f),
            TimeSeriesPoint(500L, 62.0f, -8.2f)
        )

        val json = SessionExporter.pointsToJson(points)
        assertTrue(json.contains("\"t\":0"))
        assertTrue(json.contains("\"l\":55.4"))

        val parsed = SessionExporter.jsonToPoints(json)
        assertEquals(3, parsed.size)
        assertEquals(0L, parsed[0].offsetMs)
        assertEquals(55.4f, parsed[0].levelDb, 0.1f)
        assertEquals(-12.3f, parsed[0].peakDbFS, 0.1f)
    }

    @Test
    fun testCsvExportFormat() {
        val points = listOf(
            TimeSeriesPoint(0L, 65.2f, -15.0f),
            TimeSeriesPoint(500L, 68.4f, -12.1f)
        )
        val json = SessionExporter.pointsToJson(points)

        val session = SavedSession(
            id = 42,
            title = "Acoustic Test Room",
            notes = "Checked background noise",
            timestampMillis = 1700000000000L,
            durationMillis = 1000L,
            sampleRate = 48000,
            captureSource = "UNPROCESSED",
            inputRoute = "Built-in Microphone",
            timeWeighting = "FAST",
            frequencyWeighting = "A",
            isCalibrated = true,
            calibrationOffsetDb = 42.5f,
            minLevelDb = 65.2f,
            maxLevelDb = 68.4f,
            leqDb = 67.1f,
            peakDbFS = -12.1f,
            clippingOccurred = false,
            unit = "dB SPL",
            timeSeriesJson = json
        )

        val csv = SessionExporter.generateCsv(session)

        assertTrue(csv.contains("# Soundglass Measurement Session Export"))
        assertTrue(csv.contains("# Title: Acoustic Test Room"))
        assertTrue(csv.contains("# SampleRate: 48000 Hz"))
        assertTrue(csv.contains("# LeqEquivalent: 67.1 dB SPL"))
        assertTrue(csv.contains("TimestampSeconds,Level_dBSPL,SamplePeak_dBFS"))
        assertTrue(csv.contains("0.00,65.2,-15.0"))
        assertTrue(csv.contains("0.50,68.4,-12.1"))
    }

    @Test
    fun testJsonExportSchemaVersion() {
        val points = listOf(TimeSeriesPoint(0L, 50.0f, -20.0f))
        val jsonStr = SessionExporter.pointsToJson(points)

        val session = SavedSession(
            id = 7,
            title = "Office Noise",
            notes = "",
            timestampMillis = 1700000000000L,
            durationMillis = 5000L,
            sampleRate = 48000,
            captureSource = "UNPROCESSED",
            inputRoute = "Microphone",
            timeWeighting = "SLOW",
            frequencyWeighting = "Z",
            isCalibrated = false,
            calibrationOffsetDb = 0.0f,
            minLevelDb = -45.0f,
            maxLevelDb = -30.0f,
            leqDb = -35.2f,
            peakDbFS = -20.0f,
            clippingOccurred = false,
            unit = "dBFS",
            timeSeriesJson = jsonStr
        )

        val exportedJson = SessionExporter.generateJson(session)
        val root = JSONObject(exportedJson)

        assertEquals(1, root.getInt("schemaVersion"))
        assertEquals("Soundglass", root.getString("application"))

        val sessObj = root.getJSONObject("session")
        assertEquals(7, sessObj.getInt("id"))
        assertEquals("Office Noise", sessObj.getString("title"))
        assertEquals(48000, sessObj.getInt("sampleRate"))

        val timeSeries = sessObj.getJSONArray("timeSeries")
        assertEquals(1, timeSeries.length())
    }
}
