package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioCaptureManager
import com.example.audio.AudioDeviceHelper
import com.example.audio.AudioPlaybackManager
import com.example.audio.CaptureState
import com.example.data.entity.SavedSession
import com.example.data.export.SessionExporter
import com.example.data.preferences.PreferencesManager
import com.example.data.preferences.SoundglassPreferences
import com.example.data.repository.SessionRepository
import com.example.dsp.CalibrationProfile
import com.example.dsp.FrequencyWeighting
import com.example.dsp.MeterReading
import com.example.dsp.TimeWeighting
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
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
    val isComplete: Boolean = false,
    val error: String? = null
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
    val isSessionCompleted = captureManager.isSessionCompleted
    val calibrationWarning: StateFlow<String?> = captureManager.calibrationWarning

    val preferences: StateFlow<SoundglassPreferences?> = preferencesManager.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // Rolling buffer for live graph (last 45 seconds, ~4 points per second)
    private val _liveHistory = MutableStateFlow<List<LiveGraphPoint>>(emptyList())
    val liveHistory: StateFlow<List<LiveGraphPoint>> = _liveHistory.asStateFlow()

    // Calibration workflow state
    private val _calibrationState = MutableStateFlow(CalibrationState())
    val calibrationState: StateFlow<CalibrationState> = _calibrationState.asStateFlow()
    private var calibrationJob: Job? = null
    private var activeCalibratedProfile: CalibrationProfile? = null

    init {
        // Collect preferences and apply to capture manager safely without resetting active sessions
        viewModelScope.launch {
            preferencesManager.preferencesFlow.collect { prefs ->
                captureManager.sessionDurationLimitMinutes = prefs.sessionDurationLimitMinutes
                captureManager.setTimeWeighting(prefs.defaultTimeWeighting)
                if (captureManager.captureState.value == CaptureState.IDLE) captureManager.setFrequencyWeighting(prefs.defaultFrequencyWeighting)
                captureManager.syncCalibrationFromPreferences(prefs)
                captureManager.setFftSize(prefs.defaultFftSize)
            }
        }

        // Collect readings to populate live history graph
        viewModelScope.launch {
            var lastHistoryAddMs = 0L
            var lastDurationMs = 0L
            captureManager.meterReading.collect { reading ->
                if (reading.sessionDurationMs < lastDurationMs || reading.totalCapturedSamples == 0L) _liveHistory.value = emptyList()
                lastDurationMs = reading.sessionDurationMs
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
        captureManager.resumeCapture(viewModelScope)
    }

    fun resetMeasurement() {
        captureManager.clearSessionData()
        _liveHistory.value = emptyList()
    }

    fun setTimeWeighting(weighting: TimeWeighting) {
        captureManager.setTimeWeighting(weighting)
        viewModelScope.launch {
            preferencesManager.updateTimeWeighting(weighting)
        }
    }

    fun setFrequencyWeighting(weighting: FrequencyWeighting) {
        captureManager.setFrequencyWeighting(weighting)
        viewModelScope.launch {
            preferencesManager.updateFrequencyWeighting(weighting)
        }
    }

    /**
     * Starts calibration procedure:
     * Samples for 3.0 seconds, integrates stable readings in memory, computes offset = targetRefSpl - measured_dBFS
     */
    fun startCalibration(targetRefSpl: Float) {
        if (calibrationJob?.isActive == true) return
        _calibrationState.value = CalibrationState(isCalibrating = true, targetRefSpl = targetRefSpl)
        calibrationJob = viewModelScope.launch {
            try {
                startMeasurement()
                kotlinx.coroutines.withTimeout(5000) {
                    captureManager.captureState.first { it == CaptureState.RECORDING || it == CaptureState.ERROR }
                }
                captureManager.beginCalibrationSampling(3.0)
                kotlinx.coroutines.withTimeout(6000) {
                    while (captureManager.calibrationProgress() < 1f) {
                        check(captureState.value == CaptureState.RECORDING) { "Microphone capture interrupted." }
                        delay(50)
                        _calibrationState.value = _calibrationState.value.copy(progress = captureManager.calibrationProgress())
                    }
                }
                val profile = captureManager.finishCalibrationSampling(targetRefSpl).getOrThrow()
                activeCalibratedProfile = profile
                _calibrationState.value = CalibrationState(isComplete = true, calculatedOffset = profile.offsetDb)
            } catch (e: kotlinx.coroutines.CancellationException) {
                captureManager.cancelCalibrationSampling()
                _calibrationState.value = CalibrationState(error = if (e is kotlinx.coroutines.TimeoutCancellationException) "Calibration timed out. Check microphone access and try again." else null)
                throw e
            } catch (e: Exception) {
                captureManager.cancelCalibrationSampling()
                _calibrationState.value = CalibrationState(error = e.localizedMessage ?: "Calibration failed.")
            }
        }
    }

    fun applyCalibration() {
        val profile = activeCalibratedProfile ?: return
        viewModelScope.launch {
            captureManager.setCalibrationProfile(profile)
            preferencesManager.saveCalibration(profile)
            activeCalibratedProfile = null
            _calibrationState.value = CalibrationState()
            resetMeasurement()
        }
    }

    fun cancelCalibration() {
        calibrationJob?.cancel(); calibrationJob = null
        captureManager.cancelCalibrationSampling()
        activeCalibratedProfile = null
        _calibrationState.value = CalibrationState()
    }

    fun updateTheme(mode: String) { viewModelScope.launch { preferencesManager.updateThemeMode(mode) } }
    fun updateDurationLimit(minutes: Int) { viewModelScope.launch { preferencesManager.updateSessionLimit(minutes) } }

    fun resetCalibrationToDefaults() {
        viewModelScope.launch {
            preferencesManager.resetCalibration()
            captureManager.setCalibrationProfile(null)
            resetMeasurement()
        }
    }

    suspend fun saveSession(title: String, notes: String): Long {
        val snapshot = captureManager.sessionSnapshot()
        val reading = snapshot.reading
        require(reading.totalCapturedSamples > 0) { "Capture some audio before saving a session." }
        val points = snapshot.points
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
            timeSeriesJson = json,
            calibrationProfileJson = snapshot.calibration?.let { p -> org.json.JSONObject().apply {
                put("timestampMs", p.timestampMs); put("referenceSpl", p.referenceSpl)
                put("deviceName", p.deviceName); put("deviceType", p.deviceType); put("deviceAddress", p.deviceAddress)
                put("sampleRate", p.sampleRate); put("captureSource", p.captureSource)
                put("channelConfig", p.channelConfig); put("encoding", p.encoding)
                put("frequencyWeighting", p.frequencyWeighting.name); put("offsetDb", p.offsetDb)
            }.toString() }
        )

        return sessionRepository.saveSession(session)
    }

    override fun onCleared() {
        super.onCleared()
        cancelCalibration()
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
