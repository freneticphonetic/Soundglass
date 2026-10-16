package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Data point in a recorded session time-series.
 */
data class TimeSeriesPoint(
    val offsetMs: Long,
    val levelDb: Float,
    val peakDbFS: Float
)

/**
 * Room entity representing a completed or saved sound measurement session.
 */
@Entity(tableName = "saved_sessions")
data class SavedSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val notes: String = "",
    val timestampMillis: Long = System.currentTimeMillis(),
    val durationMillis: Long,
    val sampleRate: Int,
    val captureSource: String,
    val inputRoute: String,
    val timeWeighting: String,      // "FAST" or "SLOW"
    val frequencyWeighting: String, // "A" or "Z"
    val isCalibrated: Boolean,
    val calibrationOffsetDb: Float,
    val minLevelDb: Float,
    val maxLevelDb: Float,
    val leqDb: Float,
    val peakDbFS: Float,
    val clippingOccurred: Boolean,
    val unit: String,               // "dBFS" or "dB SPL"
    val timeSeriesJson: String      // Serialized compact JSON time series
)
