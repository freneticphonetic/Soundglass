package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioCaptureManager
import com.example.audio.AudioPlaybackManager
import com.example.audio.CaptureState
import com.example.data.preferences.PreferencesManager
import com.example.dsp.FftAnalysisFrame
import com.example.dsp.SpectrumSmoothing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AnalyzerViewModel(
    private val captureManager: AudioCaptureManager,
    private val playbackManager: AudioPlaybackManager,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    val captureState: StateFlow<CaptureState> = captureManager.captureState
    val waveform: StateFlow<FloatArray> = captureManager.waveform

    private val _isFrozen = MutableStateFlow(false)
    val isFrozen: StateFlow<Boolean> = _isFrozen.asStateFlow()

    private val _selectedFftSize = MutableStateFlow(4096)
    val selectedFftSize: StateFlow<Int> = _selectedFftSize.asStateFlow()

    private val _selectedSmoothing = MutableStateFlow(SpectrumSmoothing.NONE)
    val selectedSmoothing: StateFlow<SpectrumSmoothing> = _selectedSmoothing.asStateFlow()

    // Frozen snapshot when freeze is enabled
    private val _frozenFrame = MutableStateFlow<FftAnalysisFrame?>(null)

    // Touch inspection cursor
    private val _inspectedFrequencyHz = MutableStateFlow<Float?>(null)
    val inspectedFrequencyHz: StateFlow<Float?> = _inspectedFrequencyHz.asStateFlow()

    private val _inspectedMagnitudeDbFS = MutableStateFlow<Float?>(null)
    val inspectedMagnitudeDbFS: StateFlow<Float?> = _inspectedMagnitudeDbFS.asStateFlow()

    // Spectrogram history rows (max 80 rows for scrolling waterfall)
    private val _spectrogramHistory = MutableStateFlow<List<FloatArray>>(emptyList())
    val spectrogramHistory: StateFlow<List<FloatArray>> = _spectrogramHistory.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesManager.preferencesFlow.collect { prefs ->
                _selectedFftSize.value = prefs.defaultFftSize
            }
        }

        viewModelScope.launch {
            captureManager.fftFrame.collect { frame ->
                if (!_isFrozen.value && frame != null) {
                    val currentList = _spectrogramHistory.value.toMutableList()
                    // Downsample magnitude array to 128 log bins for efficient spectrogram rendering
                    val downsampled = downsampleMagnitudes(frame.magnitudesDbFS, 128)
                    currentList.add(downsampled)
                    if (currentList.size > 80) {
                        currentList.removeAt(0)
                    }
                    _spectrogramHistory.value = currentList
                }
            }
        }
    }

    val activeFftFrame: StateFlow<FftAnalysisFrame?> = captureManager.fftFrame

    fun toggleFreeze() {
        if (!_isFrozen.value) {
            _frozenFrame.value = captureManager.fftFrame.value
            _isFrozen.value = true
        } else {
            _isFrozen.value = false
            _frozenFrame.value = null
            _inspectedFrequencyHz.value = null
            _inspectedMagnitudeDbFS.value = null
        }
    }

    fun setFftSize(size: Int) {
        _selectedFftSize.value = size
        viewModelScope.launch {
            preferencesManager.updateFftSize(size)
        }
    }

    fun setSmoothing(smoothing: SpectrumSmoothing) {
        _selectedSmoothing.value = smoothing
        captureManager.configureSettings(
            timeWeighting = captureManager.meterReading.value.timeWeighting,
            frequencyWeighting = captureManager.meterReading.value.frequencyWeighting,
            calibrationOffsetDb = if (captureManager.meterReading.value.isCalibrated) captureManager.meterReading.value.calibrationOffsetDb else null,
            fftSize = _selectedFftSize.value,
            smoothing = smoothing
        )
    }

    fun setInspectionPoint(freqHz: Float?, magDbFS: Float?) {
        _inspectedFrequencyHz.value = freqHz
        _inspectedMagnitudeDbFS.value = magDbFS
    }

    fun ensureCaptureRunning() {
        if (playbackManager.isPlaying.value) {
            playbackManager.stopPlayback()
        }
        if (captureManager.captureState.value == CaptureState.IDLE) {
            captureManager.startCapture(viewModelScope)
        } else if (captureManager.captureState.value == CaptureState.PAUSED) {
            captureManager.resumeCapture()
        }
    }

    private fun downsampleMagnitudes(mags: FloatArray, targetBins: Int): FloatArray {
        val out = FloatArray(targetBins)
        val step = mags.size.toFloat() / targetBins
        for (i in 0 until targetBins) {
            val start = (i * step).toInt()
            val end = (((i + 1) * step).toInt()).coerceAtMost(mags.size)
            var maxV = -120f
            for (j in start until end) {
                if (mags[j] > maxV) maxV = mags[j]
            }
            out[i] = maxV
        }
        return out
    }

    class Factory(
        private val captureManager: AudioCaptureManager,
        private val playbackManager: AudioPlaybackManager,
        private val preferencesManager: PreferencesManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AnalyzerViewModel(captureManager, playbackManager, preferencesManager) as T
        }
    }
}
