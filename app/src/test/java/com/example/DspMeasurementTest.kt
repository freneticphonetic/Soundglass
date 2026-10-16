package com.example

import com.example.dsp.AWeightingFilter
import com.example.dsp.AudioConstants
import com.example.dsp.FftAnalyzer
import com.example.dsp.FrequencyWeighting
import com.example.dsp.LevelMeterDsp
import com.example.dsp.PitchDetector
import com.example.dsp.TimeWeighting
import com.example.dsp.ToneGeneratorEngine
import com.example.dsp.WaveformType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

class DspMeasurementTest {

    @Test
    fun testRmsAndPeakFullScaleSine() {
        val sampleRate = 48000
        val dsp = LevelMeterDsp(
            sampleRate = sampleRate,
            initialTimeWeighting = TimeWeighting.FAST,
            initialFrequencyWeighting = FrequencyWeighting.Z
        )

        // Generate 1 second of 1000 Hz pure sine wave at peak amplitude 1.0
        val count = 48000
        val samples = FloatArray(count) { i ->
            sin(2.0 * PI * 1000.0 * i / sampleRate).toFloat()
        }

        val reading = dsp.processBlock(samples, count, 1000L)

        // Theoretical peak of amplitude 1.0 sine is 0.0 dBFS
        assertEquals(0.0f, reading.samplePeakDbFS, 0.01f)

        // Theoretical RMS of amplitude 1.0 sine is 1/sqrt(2) = 0.7071 -> 20*log10(0.7071) = -3.01 dBFS
        assertEquals(-3.01f, reading.rmsLevelDbFS, 0.05f)
    }

    @Test
    fun testFullScaleSquareWave() {
        val sampleRate = 48000
        val dsp = LevelMeterDsp(
            sampleRate = sampleRate,
            initialTimeWeighting = TimeWeighting.FAST,
            initialFrequencyWeighting = FrequencyWeighting.Z
        )

        // Full scale square wave alternating between +1.0 and -1.0
        val count = 4800
        val samples = FloatArray(count) { i ->
            if ((i % 100) < 50) 1.0f else -1.0f
        }

        val reading = dsp.processBlock(samples, count, 100L)

        // Square wave of +/- 1.0 has RMS = 1.0 (0.0 dBFS) and Peak = 1.0 (0.0 dBFS)
        assertEquals(0.0f, reading.samplePeakDbFS, 0.01f)
        assertEquals(0.0f, reading.rmsLevelDbFS, 0.05f)
    }

    @Test
    fun testZeroInputSilence() {
        val sampleRate = 48000
        val dsp = LevelMeterDsp(sampleRate = sampleRate)

        val silence = FloatArray(2048) { 0.0f }
        val reading = dsp.processBlock(silence, 2048, 100L)

        assertTrue(reading.samplePeakDbFS <= AudioConstants.MIN_DB)
        assertTrue(reading.rmsLevelDbFS <= -90.0f)
    }

    @Test
    fun testEnergyEquivalentLevelLeq() {
        val sampleRate = 48000
        val dsp = LevelMeterDsp(
            sampleRate = sampleRate,
            initialTimeWeighting = TimeWeighting.FAST,
            initialFrequencyWeighting = FrequencyWeighting.Z
        )

        // Segment 1: 1.0 second of full scale square wave (MeanSquare = 1.0)
        val seg1 = FloatArray(48000) { 1.0f }
        dsp.processBlock(seg1, 48000, 1000L)

        // Segment 2: 1.0 second of silence (MeanSquare = 0.0)
        val seg2 = FloatArray(48000) { 0.0f }
        val reading = dsp.processBlock(seg2, 48000, 2000L)

        // Over 2 seconds: total energy = 1.0 * 1.0s + 0.0 * 1.0s = 1.0
        // Average power = 1.0 / 2.0 = 0.5
        // Expected Leq = 10 * log10(0.5) = -3.01 dBFS
        assertEquals(-3.01f, reading.leqDb, 0.1f)
        assertEquals(2000L, reading.sessionDurationMs)
    }

    @Test
    fun testFastAndSlowTimeWeightingResponse() {
        val sampleRate = 48000

        // Test FAST weighting response to step input of amplitude 1.0 for 125 ms (tau = 125 ms)
        val dspFast = LevelMeterDsp(
            sampleRate = sampleRate,
            initialTimeWeighting = TimeWeighting.FAST,
            initialFrequencyWeighting = FrequencyWeighting.Z
        )
        val stepSamplesFast = (0.125 * sampleRate).toInt() // 6000 samples
        val stepSignalFast = FloatArray(stepSamplesFast) { 1.0f }
        val readingFast = dspFast.processBlock(stepSignalFast, stepSamplesFast, 125L)

        // At t = tau, step response should reach 1 - 1/e ≈ 0.63212
        // 10 * log10(0.63212) ≈ -1.99 dBFS
        assertEquals(-1.99f, readingFast.currentLevelDb, 0.15f)

        // Test SLOW weighting response to step input for 125 ms (tau = 1000 ms)
        val dspSlow = LevelMeterDsp(
            sampleRate = sampleRate,
            initialTimeWeighting = TimeWeighting.SLOW,
            initialFrequencyWeighting = FrequencyWeighting.Z
        )
        val readingSlow = dspSlow.processBlock(stepSignalFast, stepSamplesFast, 125L)

        // At t = 0.125 s with tau = 1.0 s, response is 1 - exp(-0.125) ≈ 0.1175
        // 10 * log10(0.1175) ≈ -9.30 dBFS
        assertEquals(-9.30f, readingSlow.currentLevelDb, 0.20f)
    }

    @Test
    fun testAWeightingFrequencyResponseIec61672() {
        val sampleRate = 48000
        val filter = AWeightingFilter(sampleRate)

        // Verification frequencies and published IEC 61672-1 Class 2 tolerances
        val testCases = listOf(
            Triple(1000.0, 0.0, 0.05),    // 1 kHz reference (must be 0.00 dB)
            Triple(100.0, -19.1, 0.50),   // 100 Hz (-19.1 dB)
            Triple(4000.0, 1.0, 0.50),    // 4 kHz (+1.0 dB)
            Triple(8000.0, -1.1, 0.60)    // 8 kHz (-1.1 dB)
        )

        for ((freqHz, expectedGainDb, toleranceDb) in testCases) {
            filter.reset()
            val numCycles = 100
            val numSamples = (sampleRate * (numCycles / freqHz)).toInt().coerceAtLeast(4096)
            val inSamples = FloatArray(numSamples) { i ->
                sin(2.0 * PI * freqHz * i / sampleRate).toFloat()
            }
            val outSamples = FloatArray(numSamples)
            filter.process(inSamples, outSamples, numSamples)

            // Calculate steady-state RMS (last 50% of samples to avoid transient startup)
            val steadyStart = numSamples / 2
            var inSumSquares = 0.0
            var outSumSquares = 0.0
            for (i in steadyStart until numSamples) {
                inSumSquares += inSamples[i] * inSamples[i]
                outSumSquares += outSamples[i] * outSamples[i]
            }
            val inRms = sqrt(inSumSquares / (numSamples - steadyStart))
            val outRms = sqrt(outSumSquares / (numSamples - steadyStart))
            val measuredGainDb = 20.0 * log10(outRms / inRms)

            assertEquals(
                "A-weighting gain mismatch at $freqHz Hz",
                expectedGainDb,
                measuredGainDb,
                toleranceDb
            )
        }
    }

    @Test
    fun testFftPeakFrequencyDetectionAndInterpolation() {
        val sampleRate = 48000
        val fftSize = 4096
        val analyzer = FftAnalyzer(fftSize = fftSize, sampleRate = sampleRate)

        // 440 Hz test tone (A4)
        val targetFreq = 440.0
        val samples = FloatArray(fftSize) { i ->
            sin(2.0 * PI * targetFreq * i / sampleRate).toFloat()
        }

        val frame = analyzer.process(samples)
        assertNotNull(frame.peakFrequencyHz)
        assertEquals(440.0f, frame.peakFrequencyHz!!, 0.5f)
        assertEquals("A4", frame.musicalNote)
        assertTrue(abs(frame.centsOffset ?: 100) <= 2)

        // Off-bin tone: 1005.3 Hz (bin width is 48000 / 4096 ≈ 11.72 Hz, so 1005.3 is between bins 85 and 86)
        val offBinFreq = 1005.3
        val offBinSamples = FloatArray(fftSize) { i ->
            sin(2.0 * PI * offBinFreq * i / sampleRate).toFloat()
        }
        val offBinFrame = analyzer.process(offBinSamples)
        assertNotNull(offBinFrame.peakFrequencyHz)
        assertEquals(1005.3f, offBinFrame.peakFrequencyHz!!, 1.0f)
    }

    @Test
    fun testFftSilenceSuppressesToneDetection() {
        val analyzer = FftAnalyzer(4096, 48000)
        val silence = FloatArray(4096) { 0.0f }
        val frame = analyzer.process(silence)
        assertNull(frame.peakFrequencyHz)
        assertNull(frame.musicalNote)
    }

    @Test
    fun testPitchDetectorConversion() {
        val a4 = PitchDetector.frequencyToPitch(440.0f)
        assertNotNull(a4)
        assertEquals("A4", a4!!.first)
        assertEquals(0, a4.second)

        val c4 = PitchDetector.frequencyToPitch(261.63f)
        assertNotNull(c4)
        assertEquals("C4", c4!!.first)
    }

    @Test
    fun testToneGeneratorSynthesisBounds() {
        val engine = ToneGeneratorEngine(48000)
        engine.setFrequency(1000.0)
        engine.setAmplitudeDb(-6.0)
        engine.start()

        val buf = ShortArray(1024)

        for (wf in listOf(
            WaveformType.SINE,
            WaveformType.TRIANGLE,
            WaveformType.SQUARE,
            WaveformType.SAWTOOTH,
            WaveformType.WHITE_NOISE,
            WaveformType.PINK_NOISE
        )) {
            engine.waveform = wf
            val rendered = engine.renderBlock(buf, 1024)
            assertEquals(1024, rendered)

            for (s in buf) {
                // A -6 dBFS peak setting must remain within its expected amplitude.
                assertTrue(kotlin.math.abs(s.toInt()) <= 16424)
            }
        }

        // Test sweep
        engine.waveform = WaveformType.SWEEP
        engine.configureSweep(50.0, 15000.0, 2.0)
        engine.renderBlock(buf, 1024)
        assertTrue(engine.currentFrequencyHz >= 50.0)
        assertTrue(engine.sweepProgress in 0.0f..1.0f)

        engine.stop()
    }
}
