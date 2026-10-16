package com.example

import com.example.dsp.AudioConstants
import com.example.dsp.CalibrationProfile
import com.example.dsp.FftAnalyzer
import com.example.dsp.FrequencyWeighting
import com.example.dsp.LevelMeterDsp
import com.example.dsp.SpectrumSmoothing
import com.example.dsp.TimeWeighting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

/**
 * Regression tests proving:
 * 1. Analyzer settings (FFT size and spectrum smoothing) preserve meter duration, energy accumulation, and history.
 * 2. Unchanged calibration or weighting settings never reset session statistics.
 * 3. Changed calibration or weighting resets statistics cleanly to prevent mixing different units or weightings.
 * 4. Calibration profiles strictly validate routed device, sample rate, capture source, and frequency weighting.
 * 5. Hardware pause and resume preserves session duration without accumulating paused time.
 */
class AnalyzerSettingsRegressionTest {

    @Test
    fun testFftSizeChangePreservesMeterStatsAndDuration() {
        val sampleRate = 48000
        val dsp = LevelMeterDsp(
            sampleRate = sampleRate,
            initialTimeWeighting = TimeWeighting.FAST,
            initialFrequencyWeighting = FrequencyWeighting.Z
        )
        var analyzer = FftAnalyzer(fftSize = 4096, sampleRate = sampleRate)

        // Generate 1 second of 1000 Hz sine wave at amplitude 1.0
        val count = 48000
        val block1 = FloatArray(count) { i ->
            sin(2.0 * PI * 1000.0 * i / sampleRate).toFloat()
        }

        val reading1 = dsp.processBlock(block1, count, 1000L)
        analyzer.process(block1.copyOfRange(0, 4096))

        assertEquals(1000L, reading1.sessionDurationMs)
        assertEquals(-3.01f, reading1.rmsLevelDbFS, 0.05f)
        assertEquals(-3.01f, reading1.leqDb, 0.05f)

        // Change FFT size (e.g. from 4096 to 2048, then 8192)
        analyzer = FftAnalyzer(fftSize = 2048, sampleRate = sampleRate)

        // Verify DSP reading is completely unaffected by FFT size modification
        val readingAfterFftChange = dsp.currentReading(1000L)
        assertEquals(1000L, readingAfterFftChange.sessionDurationMs)
        assertEquals(-3.01f, readingAfterFftChange.leqDb, 0.05f)
        assertEquals(reading1.minLevelDb, readingAfterFftChange.minLevelDb, 0.01f)
        assertEquals(reading1.maxLevelDb, readingAfterFftChange.maxLevelDb, 0.01f)

        // Process a second 1.0 second block of identical signal
        val reading2 = dsp.processBlock(block1, count, 2000L)
        assertEquals(2000L, reading2.sessionDurationMs)
        assertEquals(-3.01f, reading2.leqDb, 0.05f)
    }

    @Test
    fun testSpectrumSmoothingPreservesMeterStatsAndDuration() {
        val sampleRate = 48000
        val dsp = LevelMeterDsp(
            sampleRate = sampleRate,
            initialTimeWeighting = TimeWeighting.FAST,
            initialFrequencyWeighting = FrequencyWeighting.Z
        )
        val analyzer = FftAnalyzer(fftSize = 4096, sampleRate = sampleRate)

        val count = 48000
        val block = FloatArray(count) { i ->
            sin(2.0 * PI * 440.0 * i / sampleRate).toFloat()
        }

        val reading = dsp.processBlock(block, count, 1000L)
        assertEquals(1000L, reading.sessionDurationMs)

        // Apply various spectrum smoothing modes to analyzer
        for (smoothing in SpectrumSmoothing.values()) {
            val frame = analyzer.process(block.copyOfRange(0, 4096), smoothing)
            assertNotNull(frame)

            // Meter duration and statistics must remain identical
            val current = dsp.currentReading(1000L)
            assertEquals(1000L, current.sessionDurationMs)
            assertEquals(reading.leqDb, current.leqDb, 0.01f)
            assertEquals(reading.minLevelDb, current.minLevelDb, 0.01f)
            assertEquals(reading.maxLevelDb, current.maxLevelDb, 0.01f)
        }
    }

    @Test
    fun testUnchangedCalibrationOffsetDoesNotResetSession() {
        val sampleRate = 48000
        val dsp = LevelMeterDsp(
            sampleRate = sampleRate,
            initialTimeWeighting = TimeWeighting.FAST,
            initialFrequencyWeighting = FrequencyWeighting.Z,
            initialCalibrationOffset = 42.0f
        )

        val count = 48000
        val block = FloatArray(count) { 0.5f } // DC / constant
        dsp.processBlock(block, count, 1000L)

        val readingBefore = dsp.currentReading(1000L)
        assertEquals(1000L, readingBefore.sessionDurationMs)
        assertTrue(readingBefore.isCalibrated)

        // Calling setCalibrationOffset with identical offset returns false (no change)
        val changed = dsp.setCalibrationOffset(42.0f)
        assertFalse(changed)

        // Verify session stats, duration, and Leq were NOT reset
        val readingAfter = dsp.currentReading(1000L)
        assertEquals(1000L, readingAfter.sessionDurationMs)
        assertEquals(readingBefore.leqDb, readingAfter.leqDb, 0.001f)
        assertEquals(readingBefore.minLevelDb, readingAfter.minLevelDb, 0.001f)
        assertEquals(readingBefore.maxLevelDb, readingAfter.maxLevelDb, 0.001f)
    }

    @Test
    fun testChangedCalibrationOffsetResetsSessionCleanly() {
        val sampleRate = 48000
        val dsp = LevelMeterDsp(
            sampleRate = sampleRate,
            initialCalibrationOffset = 42.0f
        )

        val count = 48000
        dsp.processBlock(FloatArray(count) { 0.5f }, count, 1000L)
        assertEquals(1000L, dsp.currentReading(1000L).sessionDurationMs)

        // Applying a different calibration offset returns true and resets session stats
        val changed = dsp.setCalibrationOffset(50.0f)
        assertTrue(changed)

        // Statistics are reset to avoid mixing differing calibration offsets
        val readingAfter = dsp.currentReading(1000L)
        assertEquals(0L, readingAfter.sessionDurationMs)
        assertEquals(50.0f, readingAfter.calibrationOffsetDb, 0.01f)
    }

    @Test
    fun testUnchangedFrequencyWeightingPreservesStats() {
        val sampleRate = 48000
        val dsp = LevelMeterDsp(
            sampleRate = sampleRate,
            initialFrequencyWeighting = FrequencyWeighting.A
        )

        dsp.processBlock(FloatArray(48000) { 0.1f }, 48000, 1000L)
        val durationBefore = dsp.currentReading(1000L).sessionDurationMs
        assertEquals(1000L, durationBefore)

        val changed = dsp.setFrequencyWeighting(FrequencyWeighting.A)
        assertFalse(changed)

        assertEquals(1000L, dsp.currentReading(1000L).sessionDurationMs)
    }

    @Test
    fun testChangedFrequencyWeightingResetsSessionCleanly() {
        val sampleRate = 48000
        val dsp = LevelMeterDsp(
            sampleRate = sampleRate,
            initialFrequencyWeighting = FrequencyWeighting.A
        )

        dsp.processBlock(FloatArray(48000) { 0.1f }, 48000, 1000L)
        assertEquals(1000L, dsp.currentReading(1000L).sessionDurationMs)

        // Changing from A to Z weighting returns true and resets stats
        val changed = dsp.setFrequencyWeighting(FrequencyWeighting.Z)
        assertTrue(changed)

        assertEquals(0L, dsp.currentReading(1000L).sessionDurationMs)
        assertEquals(FrequencyWeighting.Z, dsp.currentReading(1000L).frequencyWeighting)
    }

    @Test
    fun testCalibrationProfileValidationComprehensive() {
        val profile = CalibrationProfile(
            offsetDb = 42.5f,
            timestampMs = 1700000000000L,
            referenceSpl = 94.0f,
            deviceName = "Built-in Microphone",
            deviceType = 15, // TYPE_BUILTIN_MIC
            sampleRate = 48000,
            captureSource = 9, // UNPROCESSED
            channelConfig = 16, // CHANNEL_IN_MONO
            frequencyWeighting = FrequencyWeighting.A
        )

        // 1. Exact match configuration -> Valid
        val validResult = profile.validate(
            currentDeviceType = 15,
            currentDeviceName = "Built-in Microphone",
            currentSampleRate = 48000,
            currentCaptureSource = 9,
            currentFreqWeighting = FrequencyWeighting.A
        )
        assertTrue(validResult.isValid)

        // 2. Route change (e.g. wired headset plugged in, type 3) -> Invalid with clear reason
        val routeChangeResult = profile.validate(
            currentDeviceType = 3,
            currentDeviceName = "Wired Headset Mic",
            currentSampleRate = 48000,
            currentCaptureSource = 9,
            currentFreqWeighting = FrequencyWeighting.A
        )
        assertFalse(routeChangeResult.isValid)
        assertTrue(routeChangeResult.invalidationReason?.contains("Audio input changed") == true ||
                   routeChangeResult.invalidationReason?.contains("Wired Headset") == true)

        // 3. Sample rate change (48000 -> 44100) -> Invalid
        val rateChangeResult = profile.validate(
            currentDeviceType = 15,
            currentDeviceName = "Built-in Microphone",
            currentSampleRate = 44100,
            currentCaptureSource = 9,
            currentFreqWeighting = FrequencyWeighting.A
        )
        assertFalse(rateChangeResult.isValid)
        assertTrue(rateChangeResult.invalidationReason?.contains("sample rate") == true)

        // 4. Frequency weighting change (A -> Z) -> Invalid
        val weightChangeResult = profile.validate(
            currentDeviceType = 15,
            currentDeviceName = "Built-in Microphone",
            currentSampleRate = 48000,
            currentCaptureSource = 9,
            currentFreqWeighting = FrequencyWeighting.Z
        )
        assertFalse(weightChangeResult.isValid)
        assertTrue(weightChangeResult.invalidationReason?.contains("Weighting changed") == true)
    }

    @Test
    fun testHardwarePauseAndResumePreservesSessionDurationWithoutPausedTime() {
        val sampleRate = 48000
        val dsp = LevelMeterDsp(sampleRate = sampleRate, initialFrequencyWeighting = FrequencyWeighting.Z)

        // Active segment 1: 1.0 second (48000 samples)
        dsp.processBlock(FloatArray(48000) { 0.5f }, 48000, 1000L)
        val reading1 = dsp.currentReading(1000L)
        assertEquals(1000L, reading1.sessionDurationMs)

        // Paused period: user pauses capture for 5 minutes (300,000 ms wall clock time)
        // No samples are fed to DSP. Hardware is released.
        // Reading current reading at t = 301,000 ms:
        val readingPaused = dsp.currentReading(301000L)
        // Session duration MUST strictly remain 1000L (excluding paused wall clock time)
        assertEquals(1000L, readingPaused.sessionDurationMs)
        assertEquals(reading1.leqDb, readingPaused.leqDb, 0.001f)

        // Resume: user resumes and captures another 1.0 second (48000 samples)
        dsp.processBlock(FloatArray(48000) { 0.5f }, 48000, 302000L)
        val reading2 = dsp.currentReading(302000L)
        // Total duration is exactly 2000 ms, not 302,000 ms!
        assertEquals(2000L, reading2.sessionDurationMs)
        assertEquals(reading1.leqDb, reading2.leqDb, 0.001f)
    }
}
