package com.example.dsp

import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Snapshot of sound level meter measurements for UI and recording.
 */
data class MeterReading(
    val currentLevelDb: Float,          // Time-weighted level (dBFS or estimated dB SPL if calibrated)
    val rmsLevelDbFS: Float,            // Raw block RMS in dBFS
    val samplePeakDbFS: Float,          // Maximum instantaneous sample magnitude in dBFS
    val minLevelDb: Float,              // Minimum time-weighted level during session
    val maxLevelDb: Float,              // Maximum time-weighted level during session
    val leqDb: Float,                   // Energy-equivalent average level (L_eq)
    val isCalibrated: Boolean,
    val calibrationOffsetDb: Float,
    val timeWeighting: TimeWeighting,
    val frequencyWeighting: FrequencyWeighting,
    val unit: MeasurementUnit,
    val isCurrentlyClipping: Boolean,
    val sessionClippingCount: Long,
    val sessionDurationMs: Long,
    val totalCapturedSamples: Long,
    val sampleRate: Int
)

/**
 * DSP engine for sound level measurements.
 * Thread-safe and independent of Android UI code for clean unit and Robolectric testing.
 */
class LevelMeterDsp(
    val sampleRate: Int,
    initialTimeWeighting: TimeWeighting = TimeWeighting.FAST,
    initialFrequencyWeighting: FrequencyWeighting = FrequencyWeighting.A,
    initialCalibrationOffset: Float? = null
) {
    private val lock = Any()

    var timeWeighting: TimeWeighting = initialTimeWeighting
        private set

    var frequencyWeighting: FrequencyWeighting = initialFrequencyWeighting
        private set

    var isCalibrated: Boolean = initialCalibrationOffset != null
        private set

    var calibrationOffsetDb: Float = initialCalibrationOffset ?: 0.0f
        private set

    private val aFilter = AWeightingFilter(sampleRate)

    // Exponential time-weighted mean-square power
    private var weightedMeanSquare: Double = 1e-12

    // Session statistics
    private var minLevelDb: Float = Float.MAX_VALUE
    private var maxLevelDb: Float = -Float.MAX_VALUE

    // Energy accumulation for L_eq calculation (sum of meanSquare * dt)
    private var accumulatedEnergySeconds: Double = 0.0
    private var totalActiveSeconds: Double = 0.0
    private var totalCapturedSamples: Long = 0L

    // Clipping detection
    private var sessionClippingCount: Long = 0L
    private var clippingHoldUntilMs: Long = 0L

    // Temporary working buffer for filtered samples
    private var filteredBuffer: FloatArray = FloatArray(4096)

    fun setTimeWeighting(weighting: TimeWeighting) {
        synchronized(lock) {
            if (this.timeWeighting != weighting) {
                this.timeWeighting = weighting
            }
        }
    }

    /**
     * Updates frequency weighting.
     * If changed while an active session exists, resets session statistics to prevent
     * corrupting energy-average Leq with mixed A/Z weighting.
     */
    fun setFrequencyWeighting(weighting: FrequencyWeighting): Boolean {
        synchronized(lock) {
            if (this.frequencyWeighting == weighting) return false
            this.frequencyWeighting = weighting
            aFilter.reset()
            if (totalCapturedSamples > 0L) {
                resetSessionStats()
            }
            return true
        }
    }

    /**
     * Applies calibration offset only when value actually changes.
     * Prevents wiping session statistics during routine settings checks.
     */
    fun setCalibrationOffset(offsetDb: Float?): Boolean {
        synchronized(lock) {
            val newIsCalibrated = offsetDb != null
            val newOffset = offsetDb ?: 0.0f
            if (this.isCalibrated == newIsCalibrated && kotlin.math.abs(this.calibrationOffsetDb - newOffset) < 1e-4f) {
                return false // No change, do not reset session stats
            }
            this.isCalibrated = newIsCalibrated
            this.calibrationOffsetDb = newOffset
            if (totalCapturedSamples > 0L) {
                resetSessionStats()
            }
            return true
        }
    }

    /**
     * Resets session stats (Min, Max, Leq, duration, clipping count)
     * without resetting current time-weighted baseline.
     */
    fun resetSessionStats() {
        synchronized(lock) {
            minLevelDb = Float.MAX_VALUE
            maxLevelDb = -Float.MAX_VALUE
            accumulatedEnergySeconds = 0.0
            totalActiveSeconds = 0.0
            totalCapturedSamples = 0L
            sessionClippingCount = 0L
            clippingHoldUntilMs = 0L
        }
    }

    /**
     * Fully resets the DSP filter delay lines, power state, and session statistics.
     */
    fun resetAll() {
        synchronized(lock) {
            aFilter.reset()
            weightedMeanSquare = 1e-12
            resetSessionStats()
        }
    }

    /**
     * Processes a block of PCM float samples (normalized -1.0 to +1.0).
     */
    fun processBlock(rawSamples: FloatArray, count: Int, currentTimeMs: Long): MeterReading {
        synchronized(lock) {
            if (count <= 0) {
                return currentReading(currentTimeMs)
            }

            if (filteredBuffer.size < count) {
                filteredBuffer = FloatArray(count)
            }

            // 1. Clipping detection & sample peak calculation
            var maxAbsSample = 0.0f
            var blockClippingSamples = 0L

            for (i in 0 until count) {
                val absVal = kotlin.math.abs(rawSamples[i])
                if (absVal > maxAbsSample) {
                    maxAbsSample = absVal
                }
                if (absVal >= AudioConstants.CLIPPING_THRESHOLD_FLOAT) {
                    blockClippingSamples++
                }
            }

            if (blockClippingSamples > 0) {
                sessionClippingCount += blockClippingSamples
                clippingHoldUntilMs = currentTimeMs + 1500L
            }

            val samplePeakDbFS = if (maxAbsSample > 1e-6f) {
                20.0f * log10(maxAbsSample)
            } else {
                AudioConstants.MIN_DB
            }

            // 2. Frequency weighting
            val processedSamples: FloatArray = if (frequencyWeighting == FrequencyWeighting.A) {
                aFilter.process(rawSamples, filteredBuffer, count)
                filteredBuffer
            } else {
                rawSamples
            }

            // 3. Compute block Mean Square & RMS
            var sumSquares = 0.0
            val alphaSample = 1.0 - exp(-1.0 / (sampleRate * timeWeighting.tauSeconds))
            for (i in 0 until count) {
                val s = processedSamples[i].toDouble()
                sumSquares += s * s
                weightedMeanSquare += alphaSample * (s * s - weightedMeanSquare)
            }
            val blockMeanSquare = sumSquares / count
            val blockRms = sqrt(blockMeanSquare)

            val rmsDbFS = if (blockRms > 1e-6) {
                (20.0 * log10(blockRms)).toFloat()
            } else {
                AudioConstants.MIN_DB
            }

            // 4. Exponential time weighting (Fast / Slow)
            val dt = count.toDouble() / sampleRate
            if (weightedMeanSquare < 1e-12) {
                weightedMeanSquare = 1e-12
            }

            val weightedDbFS = (10.0 * log10(weightedMeanSquare)).toFloat()

            val currentLevel = if (isCalibrated) {
                weightedDbFS + calibrationOffsetDb
            } else {
                weightedDbFS
            }

            // 5. Update session duration and energy-equivalent level (L_eq)
            totalCapturedSamples += count
            totalActiveSeconds += dt
            accumulatedEnergySeconds += blockMeanSquare * dt

            val rawLeqDbFS = if (totalActiveSeconds > 0.0 && accumulatedEnergySeconds > 0.0) {
                (10.0 * log10(accumulatedEnergySeconds / totalActiveSeconds)).toFloat()
            } else {
                AudioConstants.MIN_DB
            }

            val leqLevel = if (isCalibrated) {
                rawLeqDbFS + calibrationOffsetDb
            } else {
                rawLeqDbFS
            }

            // 6. Update Min and Max
            if (currentLevel < minLevelDb) {
                minLevelDb = currentLevel
            }
            if (currentLevel > maxLevelDb) {
                maxLevelDb = currentLevel
            }

            val sessionDurationMs = (totalCapturedSamples * 1000L) / sampleRate
            val isClipping = currentTimeMs < clippingHoldUntilMs

            return MeterReading(
                currentLevelDb = currentLevel,
                rmsLevelDbFS = rmsDbFS,
                samplePeakDbFS = samplePeakDbFS,
                minLevelDb = if (minLevelDb == Float.MAX_VALUE) currentLevel else minLevelDb,
                maxLevelDb = if (maxLevelDb == -Float.MAX_VALUE) currentLevel else maxLevelDb,
                leqDb = leqLevel,
                isCalibrated = isCalibrated,
                calibrationOffsetDb = calibrationOffsetDb,
                timeWeighting = timeWeighting,
                frequencyWeighting = frequencyWeighting,
                unit = if (isCalibrated) MeasurementUnit.DBSPL else MeasurementUnit.DBFS,
                isCurrentlyClipping = isClipping,
                sessionClippingCount = sessionClippingCount,
                sessionDurationMs = sessionDurationMs,
                totalCapturedSamples = totalCapturedSamples,
                sampleRate = sampleRate
            )
        }
    }

    /**
     * Retrieves current reading without feeding audio.
     */
    fun currentReading(currentTimeMs: Long): MeterReading {
        synchronized(lock) {
            val weightedDbFS = (10.0 * log10(max(weightedMeanSquare, 1e-12))).toFloat()
            val currentLevel = if (isCalibrated) weightedDbFS + calibrationOffsetDb else weightedDbFS

            val rawLeqDbFS = if (totalActiveSeconds > 0.0 && accumulatedEnergySeconds > 0.0) {
                (10.0 * log10(accumulatedEnergySeconds / totalActiveSeconds)).toFloat()
            } else {
                AudioConstants.MIN_DB
            }
            val leqLevel = if (isCalibrated) rawLeqDbFS + calibrationOffsetDb else rawLeqDbFS

            val sessionDurationMs = (totalCapturedSamples * 1000L) / sampleRate

            return MeterReading(
                currentLevelDb = currentLevel,
                rmsLevelDbFS = weightedDbFS,
                samplePeakDbFS = AudioConstants.MIN_DB,
                minLevelDb = if (minLevelDb == Float.MAX_VALUE) currentLevel else minLevelDb,
                maxLevelDb = if (maxLevelDb == -Float.MAX_VALUE) currentLevel else maxLevelDb,
                leqDb = leqLevel,
                isCalibrated = isCalibrated,
                calibrationOffsetDb = calibrationOffsetDb,
                timeWeighting = timeWeighting,
                frequencyWeighting = frequencyWeighting,
                unit = if (isCalibrated) MeasurementUnit.DBSPL else MeasurementUnit.DBFS,
                isCurrentlyClipping = currentTimeMs < clippingHoldUntilMs,
                sessionClippingCount = sessionClippingCount,
                sessionDurationMs = sessionDurationMs,
                totalCapturedSamples = totalCapturedSamples,
                sampleRate = sampleRate
            )
        }
    }
}
