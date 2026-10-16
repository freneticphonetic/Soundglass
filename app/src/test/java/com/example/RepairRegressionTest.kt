package com.example

import com.example.audio.*
import com.example.dsp.*
import com.example.ui.viewmodel.AnalyzerViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

@OptIn(ExperimentalCoroutinesApi::class)
class RepairRegressionTest {
    @Test fun oldWorkerClosesOnlyItsOwnHandleAndOnlyOnce() {
        val closed = mutableListOf<Int>()
        val old = OwnedResource(1) { closed.add(it) }
        val next = OwnedResource(2) { closed.add(it) }
        old.close(); old.close()
        assertEquals(listOf(1), closed)
        assertFalse(next.isClosed)
        next.close()
        assertEquals(listOf(1, 2), closed)
    }

    @Test fun calibrationCountsSamplesAndAveragesEnergy() {
        val sample = CalibrationSampler(48000, 3.0)
        repeat(144) { sample.add(FloatArray(1000) { 0.1f }, 1000) }
        assertEquals(1f, sample.progress, 0f)
        assertEquals(114f, sample.offset(94f), 0.001f)
        sample.add(FloatArray(1000) { 1f }, 1000) // completed sampler remains bounded
        assertEquals(144000L, sample.samples)
        assertEquals(114f, sample.offset(94f), 0.001f)
    }

    @Test fun interruptedCalibrationIsRejected() {
        val sample = CalibrationSampler(48000)
        sample.add(FloatArray(1024) { 0.1f }, 1024)
        assertThrows(IllegalStateException::class.java) { sample.offset(94f) }
    }

    @Test fun sameTypeDifferentMicIsRejected() {
        val p = CalibrationProfile(100f, 1, 94f, "USB A", 11, 48000, 9, 16, FrequencyWeighting.A, "port-a")
        assertTrue(p.validate(11, "USB A", 48000, 9, FrequencyWeighting.A, "port-a").isValid)
        assertFalse(p.validate(11, "USB B", 48000, 9, FrequencyWeighting.A, "port-b").isValid)
        assertFalse(p.validate(11, "USB A", 48000, 9, FrequencyWeighting.A, "port-b").isValid)
        assertFalse(p.validate(0, "USB A", 48000, 9, FrequencyWeighting.A, "port-a").isValid)
    }

    @Test fun timeWeightingIsIndependentOfReadBlockPartition() {
        val a = LevelMeterDsp(48000, initialFrequencyWeighting = FrequencyWeighting.Z)
        val b = LevelMeterDsp(48000, initialFrequencyWeighting = FrequencyWeighting.Z)
        val input = FloatArray(48000) { if (it < 24000) 0.8f else 0.1f }
        a.processBlock(input, input.size, 1000)
        repeat(100) { n -> b.processBlock(input.copyOfRange(n * 480, (n + 1) * 480), 480, n * 10L) }
        assertEquals(a.currentReading(1000).currentLevelDb, b.currentReading(1000).currentLevelDb, 0.0001f)
        assertEquals(a.currentReading(1000).leqDb, b.currentReading(1000).leqDb, 0.0001f)
    }

    @Test fun broadbandNoiseDoesNotBecomeAMusicalNote() {
        val rng = java.util.Random(1234)
        val frame = FftAnalyzer().process(FloatArray(4096) { (rng.nextFloat() * 2 - 1) * 0.5f })
        assertNull(frame.peakFrequencyHz)
        assertNull(frame.musicalNote)
    }

    @Test fun triangleIsContinuousAcrossPhaseWrap() {
        val e = ToneGeneratorEngine()
        val dp = 0.1
        val before = e.generateWaveformSample(WaveformType.TRIANGLE, 1.0 - 1e-8, dp)
        val after = e.generateWaveformSample(WaveformType.TRIANGLE, 1e-8, dp)
        assertEquals(before, after, 1e-5)
        assertTrue(after > -1.0) // corrected corner, not a clipped naive triangle
    }

    @Test fun equalFrequencySweepProducesToneThenStops() {
        val e = ToneGeneratorEngine(48000)
        e.waveform = WaveformType.SWEEP
        e.configureSweep(1000.0, 1000.0, 0.5)
        e.start()
        val pcm = ShortArray(256)
        var samples = 0; var nonzero = 0; var crossings = 0; var previous: Short = 0
        while (!e.isStopped() && samples < 48000) {
            val n = e.renderBlock(pcm, pcm.size)
            for (i in 0 until n) {
                if (pcm[i].toInt() != 0) nonzero++
                if (previous < 0 && pcm[i] >= 0) crossings++
                previous = pcm[i]
            }
            samples += n
        }
        assertTrue(e.isStopped())
        assertTrue(samples in 23900..24300)
        assertTrue(nonzero > 20000)
        assertTrue(crossings in 490..510)
    }

    @Test fun freezeHoldsActualViewModelStreamsAndSettingsReachAudioPort() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val audio = FakeAnalyzerPort()
            val vm = AnalyzerViewModel(audio, emptyFlow(), { })
            runCurrent()
            val first = FftAnalyzer().process(FloatArray(4096) { sin(2 * PI * 440 * it / 48000).toFloat() })
            audio.fftFrame.value = first
            audio.waveform.value = floatArrayOf(0.1f)
            runCurrent()
            vm.toggleFreeze()
            audio.fftFrame.value = first.copy(peakFrequencyHz = 880f)
            audio.waveform.value = floatArrayOf(0.9f)
            runCurrent()
            assertEquals(440f, vm.activeFftFrame.value!!.peakFrequencyHz!!, 1f)
            assertEquals(0.1f, vm.waveform.value[0], 0f)
            val history = vm.spectrogramHistory.value.size
            vm.toggleFreeze(); runCurrent()
            assertEquals(880f, vm.activeFftFrame.value!!.peakFrequencyHz!!, 0f)
            assertEquals(0.9f, vm.waveform.value[0], 0f)
            vm.setSmoothing(SpectrumSmoothing.SLOW); vm.setFftSize(2048)
            assertEquals(SpectrumSmoothing.SLOW, audio.smoothing)
            assertEquals(2048, audio.size)
            assertTrue(history > 0)
        } finally { Dispatchers.resetMain() }
    }

    private class FakeAnalyzerPort : AnalyzerAudioPort {
        override val captureState = MutableStateFlow(CaptureState.PAUSED)
        override val waveform = MutableStateFlow(FloatArray(128))
        override val fftFrame = MutableStateFlow<FftAnalysisFrame?>(null)
        override val errorMessage = MutableStateFlow<String?>(null)
        var smoothing = SpectrumSmoothing.NONE; var size = 4096
        override fun setFftSize(size: Int) { this.size = size }
        override fun setSpectrumSmoothing(value: SpectrumSmoothing) { smoothing = value }
        override fun startCapture(unusedScope: CoroutineScope) { captureState.value = CaptureState.RECORDING }
        override fun resumeCapture(unusedScope: CoroutineScope) { captureState.value = CaptureState.RECORDING }
        override fun pauseCapture() { captureState.value = CaptureState.PAUSED }
    }
}
