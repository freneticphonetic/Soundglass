package com.example.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioRouting
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.example.data.entity.TimeSeriesPoint
import com.example.data.preferences.SoundglassPreferences
import com.example.dsp.AudioConstants
import com.example.dsp.CalibrationProfile
import com.example.dsp.FftAnalysisFrame
import com.example.dsp.FftAnalyzer
import com.example.dsp.FrequencyWeighting
import com.example.dsp.LevelMeterDsp
import com.example.dsp.MeterReading
import com.example.dsp.SpectrumSmoothing
import com.example.dsp.TimeWeighting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

enum class CaptureState {
    IDLE, RECORDING, PAUSED, ERROR
}

class AudioCaptureManager(
    val context: Context,
    private val playbackManager: AudioPlaybackManager
) {
    private val captureMutex = Mutex()
    private val timeSeriesLock = Any()
    private val dspLock = Any()

    private val _captureState = MutableStateFlow(CaptureState.IDLE)
    val captureState: StateFlow<CaptureState> = _captureState.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _diagnostics = MutableStateFlow<AudioHardwareDiagnostics?>(null)
    val diagnostics: StateFlow<AudioHardwareDiagnostics?> = _diagnostics.asStateFlow()

    private val _isMicrophoneMuted = MutableStateFlow(false)
    val isMicrophoneMuted: StateFlow<Boolean> = _isMicrophoneMuted.asStateFlow()

    private val _calibrationWarning = MutableStateFlow<String?>(null)
    val calibrationWarning: StateFlow<String?> = _calibrationWarning.asStateFlow()

    private val _isSessionCompleted = MutableStateFlow(false)
    val isSessionCompleted: StateFlow<Boolean> = _isSessionCompleted.asStateFlow()

    // Bounded time series with adaptive decimation
    private val activeTimeSeries = mutableListOf<TimeSeriesPoint>()
    private var decimationIntervalMs = 250L

    // Active calibration profile
    private var activeCalibrationProfile: CalibrationProfile? = null

    // DSP components
    private var sampleRate: Int = 48000
    private var selectedCaptureSource: Int = MediaRecorder.AudioSource.UNPROCESSED
    private var levelMeterDsp = LevelMeterDsp(sampleRate)
    private var fftAnalyzer = FftAnalyzer(4096, sampleRate)
    private var spectrumSmoothing = SpectrumSmoothing.NONE

    private val _meterReading = MutableStateFlow(levelMeterDsp.currentReading(System.currentTimeMillis()))
    val meterReading: StateFlow<MeterReading> = _meterReading.asStateFlow()

    private val _waveform = MutableStateFlow(FloatArray(128))
    val waveform: StateFlow<FloatArray> = _waveform.asStateFlow()

    private val _fftFrame = MutableStateFlow<FftAnalysisFrame?>(null)
    val fftFrame: StateFlow<FftAnalysisFrame?> = _fftFrame.asStateFlow()

    // Recording job & AudioRecord instance
    private var captureJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private var routingListener: AudioRouting.OnRoutingChangedListener? = null

    // Session duration limit in minutes (0 = unlimited)
    var sessionDurationLimitMinutes: Int = 30

    // Calibration sampling session state
    private var isCalibrating: Boolean = false
    private var calibrationAccumulatedEnergy: Double = 0.0
    private var calibrationTotalSamples: Long = 0L
    private val calibrationBlockPowers = mutableListOf<Double>()
    private var calibrationClippingDetected: Boolean = false

    fun setTimeWeighting(timeWeighting: TimeWeighting) {
        synchronized(dspLock) {
            levelMeterDsp.setTimeWeighting(timeWeighting)
        }
    }

    fun setFrequencyWeighting(frequencyWeighting: FrequencyWeighting) {
        synchronized(dspLock) {
            val changed = levelMeterDsp.setFrequencyWeighting(frequencyWeighting)
            if (changed) {
                // If weighting changed, validate calibration against new weighting
                validateActiveCalibration()
                // Clear time series so different frequency weightings are not mixed
                synchronized(timeSeriesLock) {
                    activeTimeSeries.clear()
                }
            }
        }
    }

    fun setFftSize(fftSize: Int) {
        synchronized(dspLock) {
            if (fftAnalyzer.fftSize != fftSize) {
                fftAnalyzer = FftAnalyzer(fftSize, sampleRate)
            }
        }
    }

    fun setSpectrumSmoothing(smoothing: SpectrumSmoothing) {
        this.spectrumSmoothing = smoothing
    }

    fun syncCalibrationFromPreferences(prefs: SoundglassPreferences) {
        val profile = prefs.toCalibrationProfile()
        setCalibrationProfile(profile)
    }

    fun setCalibrationProfile(profile: CalibrationProfile?) {
        synchronized(dspLock) {
            activeCalibrationProfile = profile
            if (profile != null) {
                validateActiveCalibration()
            } else {
                val changed = levelMeterDsp.setCalibrationOffset(null)
                _calibrationWarning.value = null
                if (changed) {
                    synchronized(timeSeriesLock) {
                        activeTimeSeries.clear()
                    }
                }
            }
        }
    }

    /**
     * Legacy / convenience settings applier that isolates changes and never
     * resets statistics unless calibration or weighting actually changed.
     */
    fun configureSettings(
        timeWeighting: TimeWeighting,
        frequencyWeighting: FrequencyWeighting,
        calibrationOffsetDb: Float?,
        fftSize: Int,
        smoothing: SpectrumSmoothing
    ) {
        synchronized(dspLock) {
            setTimeWeighting(timeWeighting)
            setFrequencyWeighting(frequencyWeighting)
            if (activeCalibrationProfile == null && calibrationOffsetDb != null) {
                val changed = levelMeterDsp.setCalibrationOffset(calibrationOffsetDb)
                if (changed) {
                    synchronized(timeSeriesLock) {
                        activeTimeSeries.clear()
                    }
                }
            }
            setFftSize(fftSize)
            setSpectrumSmoothing(smoothing)
        }
    }

    private fun validateActiveCalibration() {
        val profile = activeCalibrationProfile ?: run {
            _calibrationWarning.value = null
            val changed = levelMeterDsp.setCalibrationOffset(null)
            if (changed) {
                synchronized(timeSeriesLock) {
                    activeTimeSeries.clear()
                }
            }
            return
        }

        val currentDeviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            audioRecord?.routedDevice?.type ?: AudioDeviceHelper.getActiveInputDeviceType(context)
        } else {
            AudioDeviceHelper.getActiveInputDeviceType(context)
        }

        val currentDeviceName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            audioRecord?.routedDevice?.productName?.toString() ?: AudioDeviceHelper.getActiveInputRouteName(context)
        } else {
            AudioDeviceHelper.getActiveInputRouteName(context)
        }

        val result = profile.validate(
            currentDeviceType = currentDeviceType,
            currentDeviceName = currentDeviceName,
            currentSampleRate = sampleRate,
            currentCaptureSource = selectedCaptureSource,
            currentFreqWeighting = levelMeterDsp.frequencyWeighting
        )

        if (!result.isValid) {
            _calibrationWarning.value = result.invalidationReason
            // Fallback to uncalibrated dBFS
            val changed = levelMeterDsp.setCalibrationOffset(null)
            if (changed) {
                synchronized(timeSeriesLock) {
                    activeTimeSeries.clear()
                }
            }
        } else {
            _calibrationWarning.value = null
            val changed = levelMeterDsp.setCalibrationOffset(profile.offsetDb)
            if (changed) {
                synchronized(timeSeriesLock) {
                    activeTimeSeries.clear()
                }
            }
        }
    }

    fun getTimeSeriesSnapshot(): List<TimeSeriesPoint> {
        synchronized(timeSeriesLock) {
            return ArrayList(activeTimeSeries)
        }
    }

    fun clearSessionData() {
        synchronized(timeSeriesLock) {
            activeTimeSeries.clear()
            decimationIntervalMs = 250L
        }
        synchronized(dspLock) {
            levelMeterDsp.resetSessionStats()
            _meterReading.value = levelMeterDsp.currentReading(System.currentTimeMillis())
        }
        _isSessionCompleted.value = false
    }

    @SuppressLint("MissingPermission")
    fun startCapture(scope: CoroutineScope) {
        scope.launch {
            captureMutex.withLock {
                if (_captureState.value == CaptureState.RECORDING) return@withLock

                // Mutual exclusivity: ensure generator playback is completely stopped and released
                playbackManager.stopPlaybackAndWait()

                if (_captureState.value == CaptureState.PAUSED) {
                    resumeCaptureInternal(scope)
                    return@withLock
                }

                // New session initialization
                clearSessionData()
                synchronized(dspLock) {
                    levelMeterDsp.resetAll()
                    fftAnalyzer.reset()
                }
                _errorMessage.value = null
                _isSessionCompleted.value = false

                startAudioRecordHardware(scope)
            }
        }
    }

    fun pauseCapture() {
        // Stop and release audio hardware while strictly preserving session data
        captureJob?.cancel()
        captureJob = null
        releaseAudioRecordHardware()
        if (_captureState.value == CaptureState.RECORDING) {
            _captureState.value = CaptureState.PAUSED
        }
    }

    fun resumeCapture(scope: CoroutineScope) {
        scope.launch {
            captureMutex.withLock {
                if (_captureState.value != CaptureState.PAUSED) return@withLock
                playbackManager.stopPlaybackAndWait()
                resumeCaptureInternal(scope)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun resumeCaptureInternal(scope: CoroutineScope) {
        _errorMessage.value = null
        startAudioRecordHardware(scope)
    }

    @SuppressLint("MissingPermission")
    private fun startAudioRecordHardware(scope: CoroutineScope) {
        releaseAudioRecordHardware()

        // Check permission explicitly
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            _captureState.value = CaptureState.ERROR
            _errorMessage.value = "Microphone permission is required to capture sound."
            return
        }

        val sampleRatesToTry = listOf(48000, 44100)
        var selectedRate = 48000
        var bufferSize = -1

        for (sr in sampleRatesToTry) {
            val minBuf = AudioRecord.getMinBufferSize(
                sr,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBuf > 0) {
                selectedRate = sr
                bufferSize = minBuf * 2
                break
            }
        }

        if (bufferSize <= 0) {
            _captureState.value = CaptureState.ERROR
            _errorMessage.value = "Hardware error: No supported audio capture configuration."
            return
        }

        sampleRate = selectedRate
        val (prefSource, sourceLabel) = AudioDeviceHelper.getPreferredAudioSource(context)
        selectedCaptureSource = prefSource

        val record = try {
            AudioRecord(
                selectedCaptureSource,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
        } catch (e: Exception) {
            _captureState.value = CaptureState.ERROR
            _errorMessage.value = "AudioRecord creation failed: ${e.localizedMessage ?: "Device error"}"
            return
        }

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            _captureState.value = CaptureState.ERROR
            _errorMessage.value = "AudioRecord failed to initialize. Microphone may be in use by another app."
            return
        }

        audioRecord = record

        // Suppress acoustic effects
        val effectsResult = AudioDeviceHelper.inspectAndDisableEffects(record.audioSessionId)

        // Setup routing change listener
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val listener = AudioRouting.OnRoutingChangedListener { routing ->
                val routedDev = routing.routedDevice
                val devName = routedDev?.productName?.toString() ?: AudioDeviceHelper.getActiveInputRouteName(context)
                updateDiagnostics(sourceLabel, effectsResult, devName)
                validateActiveCalibration()
            }
            routingListener = listener
            record.addOnRoutingChangedListener(listener, Handler(Looper.getMainLooper()))
        }

        val initialDevName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            record.routedDevice?.productName?.toString() ?: AudioDeviceHelper.getActiveInputRouteName(context)
        } else {
            AudioDeviceHelper.getActiveInputRouteName(context)
        }

        updateDiagnostics(sourceLabel, effectsResult, initialDevName)
        validateActiveCalibration()

        try {
            record.startRecording()
        } catch (e: Exception) {
            releaseAudioRecordHardware()
            _captureState.value = CaptureState.ERROR
            _errorMessage.value = "Failed to start recording: ${e.localizedMessage ?: "Hardware error"}"
            return
        }

        if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            releaseAudioRecordHardware()
            _captureState.value = CaptureState.ERROR
            _errorMessage.value = "Microphone failed to start recording (hardware error)."
            return
        }

        _captureState.value = CaptureState.RECORDING

        captureJob = scope.launch(Dispatchers.IO) {
            runCaptureLoop(record)
        }
    }

    private fun updateDiagnostics(
        sourceLabel: String,
        effectsResult: Triple<Pair<Boolean, Boolean>, Pair<Boolean, Boolean>, Pair<Boolean, Boolean>>,
        deviceName: String
    ) {
        _diagnostics.value = AudioHardwareDiagnostics(
            selectedSource = sourceLabel,
            isUnprocessedSupported = AudioDeviceHelper.isUnprocessedSourceSupported(context),
            sampleRate = sampleRate,
            channelConfig = "CHANNEL_IN_MONO",
            encoding = "ENCODING_PCM_16BIT",
            activeMicrophoneName = deviceName,
            agcAvailable = effectsResult.first.first,
            agcDisabled = effectsResult.first.second,
            nsAvailable = effectsResult.second.first,
            nsDisabled = effectsResult.second.second,
            aecAvailable = effectsResult.third.first,
            aecDisabled = effectsResult.third.second
        )
    }

    fun stopCapture() {
        captureJob?.cancel()
        captureJob = null
        releaseAudioRecordHardware()
        _captureState.value = CaptureState.IDLE
    }

    private fun releaseAudioRecordHardware() {
        val rec = audioRecord ?: return
        audioRecord = null
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && routingListener != null) {
                rec.removeOnRoutingChangedListener(routingListener)
                routingListener = null
            }
        } catch (_: Exception) {}

        try {
            if (rec.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                rec.stop()
            }
        } catch (_: Exception) {}

        try {
            rec.release()
        } catch (_: Exception) {}
    }

    /**
     * Samples actual audio blocks in memory over durationSec for certified calibration.
     */
    fun beginCalibrationSampling(durationSeconds: Double = 3.0) {
        synchronized(dspLock) {
            isCalibrating = true
            calibrationAccumulatedEnergy = 0.0
            calibrationTotalSamples = 0L
            calibrationBlockPowers.clear()
            calibrationClippingDetected = false
        }
    }

    fun finishCalibrationSampling(
        referenceSpl: Float,
        deviceType: Int,
        deviceName: String
    ): Result<CalibrationProfile> {
        synchronized(dspLock) {
            isCalibrating = false
            if (calibrationClippingDetected) {
                return Result.failure(IllegalStateException("Acoustic clipping detected during calibration. Reduce sound level or reposition reference calibrator."))
            }
            if (calibrationTotalSamples < sampleRate) {
                return Result.failure(IllegalStateException("Insufficient audio samples captured for calibration."))
            }

            val meanPower = calibrationAccumulatedEnergy / calibrationTotalSamples
            if (meanPower < 1e-8) {
                return Result.failure(IllegalStateException("No test signal detected. Ensure acoustic calibrator is active and tightly seated."))
            }

            // Check stability across sampled blocks
            if (calibrationBlockPowers.size >= 5) {
                var sumPow = 0.0
                for (p in calibrationBlockPowers) sumPow += p
                val avgP = sumPow / calibrationBlockPowers.size

                var variance = 0.0
                for (p in calibrationBlockPowers) {
                    val diff = p - avgP
                    variance += diff * diff
                }
                val stdDev = sqrt(variance / calibrationBlockPowers.size)
                val relativeStdDev = stdDev / (avgP + 1e-12)

                if (relativeStdDev > 0.40) { // More than 40% fluctuation
                    return Result.failure(IllegalStateException("Calibration signal was unstable (fluctuation too high). Please maintain a steady tone."))
                }
            }

            val measuredDbfs = (10.0 * log10(meanPower)).toFloat()
            val offsetDb = referenceSpl - measuredDbfs

            val profile = CalibrationProfile(
                offsetDb = offsetDb,
                timestampMs = System.currentTimeMillis(),
                referenceSpl = referenceSpl,
                deviceName = deviceName,
                deviceType = deviceType,
                sampleRate = sampleRate,
                captureSource = selectedCaptureSource,
                channelConfig = AudioFormat.CHANNEL_IN_MONO,
                frequencyWeighting = levelMeterDsp.frequencyWeighting
            )

            setCalibrationProfile(profile)
            return Result.success(profile)
        }
    }

    private suspend fun runCaptureLoop(record: AudioRecord) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val chunkSamples = 1024
        val rawPcmBuffer = ShortArray(chunkSamples)
        val floatSamples = FloatArray(chunkSamples)

        val fftRingBufferSize = 8192
        val fftRingBuffer = FloatArray(fftRingBufferSize)
        var ringHead = 0

        var lastTimeSeriesSampleMs = 0L
        var lastFftProcessMs = 0L
        val waveformDisplay = FloatArray(128)

        // Discard initial transient buffer to prevent stale reads
        try {
            record.read(rawPcmBuffer, 0, chunkSamples)
        } catch (_: Exception) {}

        try {
            while (kotlin.coroutines.coroutineContext.isActive) {
                val readCount = try {
                    record.read(rawPcmBuffer, 0, chunkSamples)
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        _captureState.value = CaptureState.ERROR
                        _errorMessage.value = "Hardware read failed: ${e.localizedMessage ?: "Unknown"}"
                    }
                    break
                }

                if (readCount <= 0) {
                    if (readCount == AudioRecord.ERROR_INVALID_OPERATION ||
                        readCount == AudioRecord.ERROR_BAD_VALUE ||
                        readCount == AudioRecord.ERROR_DEAD_OBJECT ||
                        readCount == AudioRecord.ERROR
                    ) {
                        withContext(Dispatchers.Main) {
                            _captureState.value = CaptureState.ERROR
                            _errorMessage.value = "Microphone hardware disconnected or dead object ($readCount)."
                        }
                        break
                    }
                    continue
                }

                val now = System.currentTimeMillis()
                _isMicrophoneMuted.value = audioManager?.isMicrophoneMute ?: false

                var blockSumSq = 0.0
                var blockClipping = false

                for (i in 0 until readCount) {
                    val s = rawPcmBuffer[i] / AudioConstants.FULL_SCALE_16_BIT
                    floatSamples[i] = s
                    blockSumSq += s * s
                    if (kotlin.math.abs(s) >= AudioConstants.CLIPPING_THRESHOLD_FLOAT) {
                        blockClipping = true
                    }

                    fftRingBuffer[ringHead] = s
                    ringHead = (ringHead + 1) % fftRingBufferSize
                }

                var sessionLimitReached = false

                // In-memory calibration sampling integration
                synchronized(dspLock) {
                    if (isCalibrating) {
                        calibrationAccumulatedEnergy += blockSumSq
                        calibrationTotalSamples += readCount
                        calibrationBlockPowers.add(blockSumSq / readCount)
                        if (blockClipping) {
                            calibrationClippingDetected = true
                        }
                    }

                    // Process sound level meter DSP
                    val reading = levelMeterDsp.processBlock(floatSamples, readCount, now)
                    _meterReading.value = reading

                    // Automatic session completion when duration limit reached
                    if (sessionDurationLimitMinutes > 0) {
                        val limitMs = sessionDurationLimitMinutes * 60 * 1000L
                        if (reading.sessionDurationMs >= limitMs) {
                            _isSessionCompleted.value = true
                            sessionLimitReached = true
                        }
                    }
                }

                if (sessionLimitReached) {
                    withContext(Dispatchers.Main) {
                        pauseCapture()
                    }
                    break
                }

                // Downsample waveform for live oscilloscope display
                val step = readCount / 128
                if (step > 0) {
                    for (i in 0 until 128) {
                        waveformDisplay[i] = floatSamples[i * step]
                    }
                    _waveform.value = waveformDisplay.clone()
                }

                // Bounded time-series aggregation
                if (now - lastTimeSeriesSampleMs >= decimationIntervalMs) {
                    lastTimeSeriesSampleMs = now
                    val reading = _meterReading.value
                    synchronized(timeSeriesLock) {
                        activeTimeSeries.add(
                            TimeSeriesPoint(
                                offsetMs = reading.sessionDurationMs,
                                levelDb = reading.currentLevelDb,
                                peakDbFS = reading.samplePeakDbFS
                            )
                        )

                        // Compact if list exceeds 4000 points
                        if (activeTimeSeries.size >= 4000) {
                            compactTimeSeries()
                        }
                    }
                }

                // Real-time FFT analysis update (~22 fps)
                if (now - lastFftProcessMs >= 45L) {
                    lastFftProcessMs = now
                    val curFftSize = fftAnalyzer.fftSize
                    val linearFftInput = FloatArray(curFftSize)
                    var readIdx = (ringHead - curFftSize + fftRingBufferSize) % fftRingBufferSize
                    for (i in 0 until curFftSize) {
                        linearFftInput[i] = fftRingBuffer[readIdx]
                        readIdx = (readIdx + 1) % fftRingBufferSize
                    }

                    val frame = fftAnalyzer.process(linearFftInput, spectrumSmoothing)
                    _fftFrame.value = frame
                }
            }
        } finally {
            releaseAudioRecordHardware()
        }
    }

    /**
     * Pairwise decimation maintaining energy equivalence and whole-session history.
     */
    private fun compactTimeSeries() {
        val compacted = ArrayList<TimeSeriesPoint>(activeTimeSeries.size / 2 + 1)
        var i = 0
        while (i < activeTimeSeries.size) {
            if (i + 1 < activeTimeSeries.size) {
                val p1 = activeTimeSeries[i]
                val p2 = activeTimeSeries[i + 1]
                // Energy-average the two levels
                val lin1 = 10.0.pow(p1.levelDb.toDouble() / 10.0)
                val lin2 = 10.0.pow(p2.levelDb.toDouble() / 10.0)
                val avgLevel = (10.0 * log10((lin1 + lin2) / 2.0)).toFloat()
                val maxPeak = max(p1.peakDbFS, p2.peakDbFS)
                compacted.add(TimeSeriesPoint(p2.offsetMs, avgLevel, maxPeak))
                i += 2
            } else {
                compacted.add(activeTimeSeries[i])
                i++
            }
        }
        activeTimeSeries.clear()
        activeTimeSeries.addAll(compacted)
        decimationIntervalMs *= 2
    }
}
