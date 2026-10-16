package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioCaptureManager
import com.example.audio.AudioPlaybackManager
import com.example.audio.CaptureState
import com.example.data.entity.SavedSession
import com.example.data.export.SessionExporter
import com.example.data.preferences.PreferencesManager
import com.example.data.preferences.SoundglassPreferences
import com.example.data.repository.SessionRepository
import com.example.dsp.FrequencyWeighting
import com.example.dsp.MeterReading
import com.example.dsp.TimeWeighting
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LiveGraphPoint(
    val timestampMs: Long,
    val levelDb: Float,
    val peakDbFS: Float
)

data class CalibrationState(
    val isCalibrating: Boolean = false,
    val progress: Float = 0.0f,
    val targetRefSpl: Float = 94.0f,
    val accumulatedDbfsSum: Double = 0.0,
    val sampleCount: Int = 0,
    val calculatedOffset: Float? = null,
    val isComplete: Boolean = false
)

class MeterViewModel(
    private val captureManager: AudioCaptureManager,
    private val playbackManager: AudioPlaybackManager,
    private val sessionRepository: SessionRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    val captureState: StateFlow<CaptureState> = captureManager.captureState
    val meterReading: StateFlow<MeterReading> = captureManager.meterReading
    val errorMessage: StateFlow<String?> = captureManager.errorMessage
    val isMicrophoneMuted: StateFlow<Boolean> = captureManager.isMicrophoneMuted
    val diagnostics = captureManager.diagnostics

    val preferences: StateFlow<SoundglassPreferences?> = preferencesManager.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // Rolling buffer for live graph (last 45 seconds, ~4 points per second)
    private val _liveHistory = MutableStateFlow<List<LiveGraphPoint>>(emptyList())
    val liveHistory: StateFlow<List<LiveGraphPoint>> = _liveHistory.asStateFlow()

    // Calibration workflow state
    private val _calibrationState = MutableStateFlow(CalibrationState())
    val calibrationState: StateFlow<CalibrationState> = _calibrationState.asStateFlow()
    private var calibrationJob: Job? = null

    init {
        // Collect preferences and apply to capture manager
        viewModelScope.launch {
            preferencesManager.preferencesFlow.collect { prefs ->
                captureManager.sessionDurationLimitMinutes = prefs.sessionDurationLimitMinutes
                captureManager.configureSettings(
                    timeWeighting = prefs.defaultTimeWeighting,
                    frequencyWeighting = prefs.defaultFrequencyWeighting,
                    calibrationOffsetDb = if (prefs.isCalibrated) prefs.calibrationOffsetDb else null,
                    fftSize = prefs.defaultFftSize,
                    smoothing = com.example.dsp.SpectrumSmoothing.NONE
                )
            }
        }

        // Collect readings to populate live history graph
        viewModelScope.launch {
            var lastHistoryAddMs = 0L
            captureManager.meterReading.collect { reading ->
                val now = System.currentTimeMillis()
                if (captureManager.captureState.value == CaptureState.RECORDING && now - lastHistoryAddMs >= 250L) {
                    lastHistoryAddMs = now
                    val currentList = _liveHistory.value.toMutableList()
                    currentList.add(
                        LiveGraphPoint(
                            timestampMs = now,
                            levelDb = reading.currentLevelDb,
                            peakDbFS = reading.samplePeakDbFS
                        )
                    )
                    // Keep at most 180 points (~45 seconds)
                    if (currentList.size > 180) {
                        currentList.removeAt(0)
                    }
                    _liveHistory.value = currentList
                }
            }
        }
    }

    fun startMeasurement() {
        // Enforce mutual exclusivity with generator
        if (playbackManager.isPlaying.value) {
            playbackManager.stopPlayback()
        }
        captureManager.startCapture(viewModelScope)
    }

    fun pauseMeasurement() {
        captureManager.pauseCapture()
    }

    fun resumeMeasurement() {
        // Enforce mutual exclusivity
        if (playbackManager.isPlaying.value) {
            playbackManager.stopPlayback()
        }
        captureManager.resumeCapture()
    }

    fun resetMeasurement() {
        captureManager.clearSessionData()
        _liveHistory.value = emptyList()
    }

    fun setTimeWeighting(weighting: TimeWeighting) {
        viewModelScope.launch {
            preferencesManager.updateTimeWeighting(weighting)
        }
    }

    fun setFrequencyWeighting(weighting: FrequencyWeighting) {
        viewModelScope.launch {
            preferencesManager.updateFrequencyWeighting(weighting)
        }
    }

    /**
     * Starts calibration procedure:
     * Samples for 3.0 seconds, integrates stable dBFS readings, computes offset = targetRefSpl - measured_dBFS
     */
    fun startCalibration(targetRefSpl: Float) {
        if (calibrationJob?.isActive == true) return
        startMeasurement()

        _calibrationState.value = CalibrationState(
            isCalibrating = true,
            progress = 0.0f,
            targetRefSpl = targetRefSpl
        )

        calibrationJob = viewModelScope.launch {
            val totalSteps = 30 // 3 seconds, 100ms steps
            var sum = 0.0
            var count = 0

            for (step in 1..totalSteps) {
                delay(100)
                val currentDbfs = meterReading.value.rmsLevelDbFS
                if (currentDbfs > -100f) {
                    sum += currentDbfs
                    count++
                }
                _calibrationState.value = _calibrationState.value.copy(
                    progress = step.toFloat() / totalSteps,
                    accumulatedDbfsSum = sum,
                    sampleCount = count
                )
            }

            if (count > 0) {
                val avgDbfs = (sum / count).toFloat()
                val offset = targetRefSpl - avgDbfs
                _calibrationState.value = _calibrationState.value.copy(
                    isCalibrating = false,
                    isComplete = true,
                    calculatedOffset = offset
                )
            } else {
                _calibrationState.value = CalibrationState() // Reset if silence
            }
        }
    }

    fun applyCalibration() {
        val offset = _calibrationState.value.calculatedOffset ?: return
        viewModelScope.launch {
            val device = diagnostics.value?.activeMicrophoneName ?: "Default Microphone"
            val sampleRate = diagnostics.value?.sampleRate ?: 48000
            preferencesManager.saveCalibration(offset, device, sampleRate)
            _calibrationState.value = CalibrationState()
            resetMeasurement()
        }
    }

    fun cancelCalibration() {
        calibrationJob?.cancel()
        calibrationJob = null
        _calibrationState.value = CalibrationState()
    }

    fun resetCalibrationToDefaults() {
        viewModelScope.launch {
            preferencesManager.resetCalibration()
            resetMeasurement()
        }
    }

    suspend fun saveSession(title: String, notes: String): Long {
        val reading = meterReading.value
        val points = captureManager.getTimeSeriesSnapshot()
        val json = SessionExporter.pointsToJson(points)

        val diag = diagnostics.value
        val session = SavedSession(
            title = if (title.isNotBlank()) title.trim() else "Sound Measurement",
            notes = notes.trim(),
            timestampMillis = System.currentTimeMillis(),
            durationMillis = reading.sessionDurationMs,
            sampleRate = reading.sampleRate,
            captureSource = diag?.selectedSource ?: "AudioRecord Direct",
            inputRoute = diag?.activeMicrophoneName ?: "Microphone",
            timeWeighting = reading.timeWeighting.name,
            frequencyWeighting = reading.frequencyWeighting.name,
            isCalibrated = reading.isCalibrated,
            calibrationOffsetDb = reading.calibrationOffsetDb,
            minLevelDb = reading.minLevelDb,
            maxLevelDb = reading.maxLevelDb,
            leqDb = reading.leqDb,
            peakDbFS = reading.samplePeakDbFS,
            clippingOccurred = reading.sessionClippingCount > 0,
            unit = reading.unit.symbol,
            timeSeriesJson = json
        )

        return sessionRepository.saveSession(session)
    }

    override fun onCleared() {
        super.onCleared()
        captureManager.stopCapture()
    }

    class Factory(
        private val captureManager: AudioCaptureManager,
        private val playbackManager: AudioPlaybackManager,
        private val sessionRepository: SessionRepository,
        private val preferencesManager: PreferencesManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MeterViewModel(captureManager, playbackManager, sessionRepository, preferencesManager) as T
        }
    }
}
