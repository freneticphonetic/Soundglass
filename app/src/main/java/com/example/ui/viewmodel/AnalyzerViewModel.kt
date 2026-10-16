package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.audio.AnalyzerAudioPort
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
    private val captureManager: AnalyzerAudioPort,
    private val preferencesFlow: kotlinx.coroutines.flow.Flow<com.example.data.preferences.SoundglassPreferences>,
    private val saveFftSize: suspend (Int) -> Unit
) : ViewModel() {

    val captureState: StateFlow<CaptureState> = captureManager.captureState
    private val _waveform = MutableStateFlow(FloatArray(128))
    val waveform = _waveform.asStateFlow()
    val errorMessage = captureManager.errorMessage
    private val _displayFrame = MutableStateFlow<FftAnalysisFrame?>(null)
    val activeFftFrame = _displayFrame.asStateFlow()


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
            preferencesFlow.collect { prefs ->
                _selectedFftSize.value = prefs.defaultFftSize
            }
        }

        viewModelScope.launch {
            captureManager.waveform.collect { if (!_isFrozen.value) _waveform.value = it }
        }
        viewModelScope.launch {
            captureManager.fftFrame.collect { frame ->
                if (!_isFrozen.value && frame != null) {
                    val old = _displayFrame.value
                    if (old != null && (old.fftSize != frame.fftSize || old.sampleRate != frame.sampleRate)) _spectrogramHistory.value = emptyList()
                    _displayFrame.value = frame
                    val currentList = _spectrogramHistory.value.toMutableList()
                    // Downsample magnitude array to 128 log bins for efficient spectrogram rendering
                    val downsampled = com.example.dsp.SpectrumProjection.logBins(frame, 128)
                    currentList.add(downsampled)
                    if (currentList.size > 80) {
                        currentList.removeAt(0)
                    }
                    _spectrogramHistory.value = currentList
                }
            }
        }
    }


    fun toggleFreeze() {
        if (!_isFrozen.value) {
            _frozenFrame.value = _displayFrame.value
            _isFrozen.value = true
        } else {
            _isFrozen.value = false
            _frozenFrame.value = null
            _displayFrame.value = captureManager.fftFrame.value
            _waveform.value = captureManager.waveform.value
            _inspectedFrequencyHz.value = null
            _inspectedMagnitudeDbFS.value = null
        }
    }

    fun setFftSize(size: Int) {
        _selectedFftSize.value = size
        captureManager.setFftSize(size)
        viewModelScope.launch {
            saveFftSize(size)
        }
    }

    fun setSmoothing(smoothing: SpectrumSmoothing) {
        _selectedSmoothing.value = smoothing
        captureManager.setSpectrumSmoothing(smoothing)
    }

    fun setInspectionPoint(freqHz: Float?, magDbFS: Float?) {
        _inspectedFrequencyHz.value = freqHz
        _inspectedMagnitudeDbFS.value = magDbFS
    }

    fun ensureCaptureRunning() {
        if (captureManager.captureState.value == CaptureState.IDLE || captureManager.captureState.value == CaptureState.ERROR) {
            captureManager.startCapture(viewModelScope)
        } else if (captureManager.captureState.value == CaptureState.PAUSED) {
            captureManager.resumeCapture(viewModelScope)
        }
    }

    fun pauseCapture() { captureManager.pauseCapture() }

    class Factory(
        private val captureManager: AudioCaptureManager,
        private val playbackManager: AudioPlaybackManager,
        private val preferencesManager: PreferencesManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AnalyzerViewModel(captureManager, preferencesManager.preferencesFlow, preferencesManager::updateFftSize) as T
        }
    }
}
