package com.example.audio

import com.example.dsp.FftAnalysisFrame
import com.example.dsp.SpectrumSmoothing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/** Injectable analyzer boundary; production implementation is the real microphone engine. */
interface AnalyzerAudioPort {
    val captureState: StateFlow<CaptureState>
    val waveform: StateFlow<FloatArray>
    val fftFrame: StateFlow<FftAnalysisFrame?>
    val errorMessage: StateFlow<String?>
    fun setFftSize(size: Int)
    fun setSpectrumSmoothing(value: SpectrumSmoothing)
    fun startCapture(unusedScope: CoroutineScope)
    fun resumeCapture(unusedScope: CoroutineScope)
    fun pauseCapture()
}
