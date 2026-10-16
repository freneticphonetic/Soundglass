package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioCaptureManager
import com.example.audio.AudioPlaybackManager
import com.example.dsp.WaveformType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.log10
import kotlin.math.pow

class GeneratorViewModel(
    private val playbackManager: AudioPlaybackManager,
    private val captureManager: AudioCaptureManager
) : ViewModel() {

    val errorMessage = playbackManager.errorMessage
    val isPlaying: StateFlow<Boolean> = playbackManager.isPlaying
    val currentSweepFrequencyHz: StateFlow<Double> = playbackManager.currentFrequencyHz
    val sweepProgress: StateFlow<Float> = playbackManager.sweepProgress

    private val _selectedWaveform = MutableStateFlow(WaveformType.SINE)
    val selectedWaveform: StateFlow<WaveformType> = _selectedWaveform.asStateFlow()

    private val _frequencyHz = MutableStateFlow(440.0)
    val frequencyHz: StateFlow<Double> = _frequencyHz.asStateFlow()

    private val _amplitudeDbFS = MutableStateFlow(-20.0f)
    val amplitudeDbFS: StateFlow<Float> = _amplitudeDbFS.asStateFlow()

    // Sweep settings
    private val _sweepStartHz = MutableStateFlow(20.0)
    val sweepStartHz: StateFlow<Double> = _sweepStartHz.asStateFlow()

    private val _sweepEndHz = MutableStateFlow(20000.0)
    val sweepEndHz: StateFlow<Double> = _sweepEndHz.asStateFlow()

    private val _sweepDurationSec = MutableStateFlow(5.0)
    val sweepDurationSec: StateFlow<Double> = _sweepDurationSec.asStateFlow()

    fun setWaveform(waveform: WaveformType) {
        _selectedWaveform.value = waveform
        playbackManager.engine.waveform = waveform
    }

    fun setFrequency(freq: Double) {
        val clamped = freq.coerceIn(10.0, 20000.0)
        _frequencyHz.value = clamped
        playbackManager.engine.setFrequency(clamped)
    }

    fun setAmplitudeDbFS(db: Float) {
        val clamped = db.coerceIn(-60.0f, 0.0f)
        _amplitudeDbFS.value = clamped
        playbackManager.engine.setAmplitudeDb(clamped.toDouble())
    }

    fun setSweepParameters(startHz: Double, endHz: Double, durationSec: Double) {
        _sweepStartHz.value = startHz.coerceIn(10.0, 20000.0)
        _sweepEndHz.value = endHz.coerceIn(10.0, 20000.0)
        _sweepDurationSec.value = durationSec.coerceIn(0.5, 60.0)
        playbackManager.engine.configureSweep(
            _sweepStartHz.value,
            _sweepEndHz.value,
            _sweepDurationSec.value
        )
    }

    fun togglePlayback() {
        if (playbackManager.isPlaying.value) {
            playbackManager.stopPlayback()
        } else {
            // Enforce mutual exclusivity: pause microphone capture when starting tone generator
            captureManager.pauseCapture()

            playbackManager.engine.waveform = _selectedWaveform.value
            playbackManager.engine.setFrequency(_frequencyHz.value)
            playbackManager.engine.setAmplitudeDb(_amplitudeDbFS.value.toDouble())
            if (_selectedWaveform.value == WaveformType.SWEEP) {
                playbackManager.engine.configureSweep(
                    _sweepStartHz.value,
                    _sweepEndHz.value,
                    _sweepDurationSec.value
                )
            }
            playbackManager.startPlayback(viewModelScope)
        }
    }

    fun stopPlayback() {
        playbackManager.stopPlayback()
    }

    override fun onCleared() {
        super.onCleared()
        playbackManager.stopPlayback()
    }

    class Factory(
        private val playbackManager: AudioPlaybackManager,
        private val captureManager: AudioCaptureManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return GeneratorViewModel(playbackManager, captureManager) as T
        }
    }
}
