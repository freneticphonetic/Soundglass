package com.example.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.*
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.example.data.entity.TimeSeriesPoint
import com.example.data.preferences.SoundglassPreferences
import com.example.dsp.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class CaptureState { IDLE, RECORDING, PAUSED, ERROR }

/** Main-thread lifecycle commands; DSP and immutable snapshots share a single lock. */
class AudioCaptureManager(val context: Context, private val playbackManager: AudioPlaybackManager) : AnalyzerAudioPort {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val lifecycleMutex = Mutex()
    private val dspLock = Any()
    @Volatile private var epoch = 0L
    @Volatile private var foreground = false
    private var captureJob: Job? = null
    private var hardware: OwnedResource<AudioRecord>? = null
    private var sampleRate = 48000
    private var captureSource = MediaRecorder.AudioSource.UNPROCESSED
    private var dsp = LevelMeterDsp(sampleRate)
    private var analyzer = FftAnalyzer(4096, sampleRate)
    private var smoothing = SpectrumSmoothing.NONE
    private var profile: CalibrationProfile? = null
    @Volatile private var clientSilenced = false
    private var currentRoute: AudioDeviceInfo? = null
    private var sessionRouteKey: String? = null
    private var sessionProfile: CalibrationProfile? = null
    private var sampler: CalibrationSampler? = null
    private var samplerRouteKey: String? = null
    private var calibrationFilter: AWeightingFilter? = null
    private var sessionPeak = AudioConstants.MIN_DB
    private val points = ArrayList<TimeSeriesPoint>()
    private var pointInterval = 250L
    private var nextPoint = 0L
    var sessionDurationLimitMinutes = 30

    private val _captureState = MutableStateFlow(CaptureState.IDLE)
    override val captureState = _captureState.asStateFlow()
    private val _errorMessage = MutableStateFlow<String?>(null)
    override val errorMessage = _errorMessage.asStateFlow()
    private val _diagnostics = MutableStateFlow<AudioHardwareDiagnostics?>(null)
    val diagnostics = _diagnostics.asStateFlow()
    private val _isMicrophoneMuted = MutableStateFlow(false)
    val isMicrophoneMuted = _isMicrophoneMuted.asStateFlow()
    private val _calibrationWarning = MutableStateFlow<String?>(null)
    val calibrationWarning = _calibrationWarning.asStateFlow()
    private val _isSessionCompleted = MutableStateFlow(false)
    val isSessionCompleted = _isSessionCompleted.asStateFlow()
    private val _meterReading = MutableStateFlow(dsp.currentReading(0))
    val meterReading = _meterReading.asStateFlow()
    private var lastReading = _meterReading.value
    private val _waveform = MutableStateFlow(FloatArray(128))
    override val waveform = _waveform.asStateFlow()
    private val _fftFrame = MutableStateFlow<FftAnalysisFrame?>(null)
    override val fftFrame = _fftFrame.asStateFlow()

    fun setForeground(active: Boolean) {
        foreground = active
        if (!active) pauseCapture()
    }

    fun setTimeWeighting(value: TimeWeighting) = synchronized(dspLock) {
        if (dsp.timeWeighting != value) {
            dsp.setTimeWeighting(value)
            resetSessionLocked()
            _errorMessage.value = "Time weighting changed; a new measurement session has started."
        }
    }
    fun setFrequencyWeighting(value: FrequencyWeighting) = synchronized(dspLock) {
        if (dsp.setFrequencyWeighting(value)) {
            cancelCalibrationSampling()
            validateCalibrationLocked()
            resetSessionLocked()
            _errorMessage.value = "Frequency weighting changed; a new measurement session has started."
        }
    }
    override fun setFftSize(size: Int) = synchronized(dspLock) {
        require(size in listOf(2048, 4096, 8192))
        if (analyzer.fftSize != size) { analyzer = FftAnalyzer(size, sampleRate); _fftFrame.value = null }
    }
    override fun setSpectrumSmoothing(value: SpectrumSmoothing) = synchronized(dspLock) { smoothing = value }
    fun syncCalibrationFromPreferences(prefs: SoundglassPreferences) = setCalibrationProfile(prefs.toCalibrationProfile())
    fun setCalibrationProfile(value: CalibrationProfile?) = synchronized(dspLock) {
        if (profile != value) { profile = value; validateCalibrationLocked() }
    }

    private fun validateCalibrationLocked() {
        val p = profile
        val route = currentRoute
        val validation = p?.validate(
            route?.type ?: 0, route?.productName?.toString() ?: "", sampleRate, captureSource,
            dsp.frequencyWeighting, route?.let { it.address.ifBlank { "android-device-id:${it.id}" } } ?: "", AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val offset = if (validation?.isValid == true) p?.offsetDb else null
        _calibrationWarning.value = if (p != null) validation?.invalidationReason else null
        if (dsp.setCalibrationOffset(offset)) resetSessionLocked()
        sessionProfile = if (offset != null) p else null
    }

    private fun resetSessionLocked() {
        dsp.resetAll()
        points.clear(); pointInterval = 250; nextPoint = 0
        sessionPeak = AudioConstants.MIN_DB
        _isSessionCompleted.value = false
        lastReading = dsp.currentReading(SystemClock.elapsedRealtime())
        _meterReading.value = lastReading
    }
    fun clearSessionData() = synchronized(dspLock) { resetSessionLocked() }
    fun getTimeSeriesSnapshot(): List<TimeSeriesPoint> = synchronized(dspLock) { points.toList() }
    data class SessionSnapshot(val reading: MeterReading, val points: List<TimeSeriesPoint>, val calibration: CalibrationProfile?)
    fun sessionSnapshot(): SessionSnapshot = synchronized(dspLock) {
        SessionSnapshot(lastReading.copy(samplePeakDbFS = sessionPeak), points.toList(), sessionProfile)
    }

    override fun startCapture(unusedScope: CoroutineScope) { requestStart() }
    override fun resumeCapture(unusedScope: CoroutineScope) { requestStart() }
    private fun requestStart() {
        if (!foreground || _captureState.value == CaptureState.RECORDING) return
        val request = ++epoch
        scope.launch {
            lifecycleMutex.withLock {
                stopWorkerAndWait()
                playbackManager.stopPlaybackAndWait()
                if (!foreground || request != epoch) return@withLock
                if (_captureState.value != CaptureState.PAUSED || _isSessionCompleted.value) clearSessionData()
                _errorMessage.value = null
                try { openHardware(request) }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    hardware?.close(); hardware = null
                    _captureState.value = CaptureState.ERROR
                    _errorMessage.value = "Microphone unavailable: ${e.localizedMessage ?: "audio capture failed"}"
                    cancelCalibrationSampling()
                }
            }
        }
    }
    override fun pauseCapture() {
        ++epoch
        if (_captureState.value == CaptureState.RECORDING) _captureState.value = CaptureState.PAUSED
        cancelCalibrationSampling()
        synchronized(dspLock) { _meterReading.value = lastReading }
        // Close this exact handle now to unblock a blocking read. Old finally blocks
        // close their own handle and never dereference a newer manager resource.
        hardware?.close()
        captureJob?.cancel()
    }
    suspend fun pauseCaptureAndWait() {
        pauseCapture()
        lifecycleMutex.withLock { stopWorkerAndWait() }
    }
    private suspend fun stopWorkerAndWait() {
        hardware?.close()
        captureJob?.cancelAndJoin()
        captureJob = null; hardware = null
    }
    fun stopCapture() { pauseCapture(); _captureState.value = CaptureState.IDLE }

    @SuppressLint("MissingPermission")
    private fun openHardware(request: Long) {
        check(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            "Microphone permission is required."
        }
        val (preferred, preferredLabel) = AudioDeviceHelper.getPreferredAudioSource(context)
        var rec: AudioRecord? = null
        var sourceLabel = preferredLabel
        loop@ for (rate in listOf(48000, 44100)) {
            val bytes = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            if (bytes <= 0) continue
            for (source in listOf(preferred, MediaRecorder.AudioSource.VOICE_RECOGNITION, MediaRecorder.AudioSource.MIC).distinct()) {
                val candidate = try { AudioRecord(source, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bytes * 2) }
                    catch (_: IllegalArgumentException) { continue }
                if (candidate.state != AudioRecord.STATE_INITIALIZED) { candidate.release(); continue }
                rec = candidate
                captureSource = source
                sourceLabel = if (source == preferred) preferredLabel else "${if (source == MediaRecorder.AudioSource.MIC) "MIC" else "VOICE_RECOGNITION"} (processed fallback)"
                synchronized(dspLock) {
                    if (sampleRate != rate) {
                        sampleRate = rate
                        dsp = LevelMeterDsp(rate, dsp.timeWeighting, dsp.frequencyWeighting)
                        analyzer = FftAnalyzer(analyzer.fftSize, rate)
                        resetSessionLocked()
                    }
                }
                break@loop
            }
        }
        val record = checkNotNull(rec) { "No supported microphone configuration." }
        val effects = AudioDeviceHelper.disableEffects(record.audioSessionId)
        var listener: AudioRouting.OnRoutingChangedListener? = null
        val recordingCallback = object : AudioManager.AudioRecordingCallback() {
            override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>) {
                if (hardware?.value === record) {
                    clientSilenced = record.activeRecordingConfiguration?.isClientSilenced == true
                    _isMicrophoneMuted.value = clientSilenced || (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager).isMicrophoneMute
                }
            }
        }
        val owned = OwnedResource(record) { r ->
            try { r.unregisterAudioRecordingCallback(recordingCallback) } catch (_: Exception) { }
            try { listener?.let { r.removeOnRoutingChangedListener(it) } } catch (_: Exception) { }
            try { r.stop() } catch (_: Exception) { }
            effects.close()
            r.release()
        }
        hardware = owned
        clientSilenced = false
        record.registerAudioRecordingCallback(ContextCompat.getMainExecutor(context), recordingCallback)
        fun refreshRoute() {
            if (owned.isClosed || hardware !== owned) return
            val route = record.routedDevice
            synchronized(dspLock) {
                val key = route?.let { "${it.type}:${it.productName}:${it.address}:${it.id}" }
                if (sessionRouteKey != null && key != sessionRouteKey) {
                    cancelCalibrationSampling(); resetSessionLocked()
                    _errorMessage.value = "Audio input changed; a new measurement session has started."
                }
                sessionRouteKey = key
                currentRoute = route
                validateCalibrationLocked()
                _diagnostics.value = AudioHardwareDiagnostics(
                    sourceLabel, AudioDeviceHelper.isUnprocessedSourceSupported(context), record.sampleRate,
                    "Mono", "PCM 16-bit", route?.productName?.toString() ?: "Route not yet reported",
                    effects.agcAvailable, effects.agcDisabled, effects.nsAvailable, effects.nsDisabled,
                    effects.aecAvailable, effects.aecDisabled
                )
            }
        }
        listener = AudioRouting.OnRoutingChangedListener { refreshRoute() }
        record.addOnRoutingChangedListener(listener!!, Handler(Looper.getMainLooper()))
        record.startRecording()
        check(record.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "Microphone did not start." }
        refreshRoute()
        _captureState.value = CaptureState.RECORDING
        captureJob = scope.launch(Dispatchers.IO) {
            try { readLoop(owned, request) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (request == epoch && !owned.isClosed) {
                    _captureState.value = CaptureState.ERROR
                    _errorMessage.value = "Microphone interrupted: ${e.localizedMessage ?: "capture error"}"
                    cancelCalibrationSampling()
                }
            } finally { owned.close() }
        }
    }

    private suspend fun readLoop(owned: OwnedResource<AudioRecord>, request: Long) {
        val pcm = ShortArray(1024)
        val floats = FloatArray(1024)
        val filtered = FloatArray(1024)
        val ring = FloatArray(8192)
        var head = 0; var collected = 0
        var lastUi = 0L; var lastFft = 0L
        var fftInput = FloatArray(analyzer.fftSize)
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        while (currentCoroutineContext().isActive && request == epoch && !owned.isClosed) {
            val n = owned.value.read(pcm, 0, pcm.size)
            if (owned.isClosed || request != epoch) break
            check(n >= 0) { "AudioRecord read error $n" }
            if (n == 0) { delay(5); continue }
            for (i in 0 until n) {
                val f = pcm[i] / 32768f
                floats[i] = f; ring[head] = f; head = (head + 1) % ring.size
            }
            collected += n
            val now = SystemClock.elapsedRealtime()
            var completed = false
            synchronized(dspLock) {
                sampler?.let { sample ->
                    if (dsp.frequencyWeighting == FrequencyWeighting.A) {
                        calibrationFilter!!.process(floats, filtered, n); sample.add(filtered, n)
                    } else sample.add(floats, n)
                    // Raw samples determine clipping, irrespective of weighting.
                    if ((0 until n).any { kotlin.math.abs(floats[it]) >= AudioConstants.CLIPPING_THRESHOLD_FLOAT }) calibrationClipped = true
                }
                val reading = dsp.processBlock(floats, n, now)
                lastReading = reading
                sessionPeak = maxOf(sessionPeak, reading.samplePeakDbFS)
                if (reading.sessionDurationMs >= nextPoint) {
                    points.add(TimeSeriesPoint(reading.sessionDurationMs, reading.currentLevelDb, reading.samplePeakDbFS))
                    if (points.size >= 4000) compactPoints()
                    nextPoint = reading.sessionDurationMs + pointInterval
                }
                if (now - lastUi >= 50) {
                    lastUi = now; _meterReading.value = reading
                    _waveform.value = FloatArray(128) { floats[(it * n / 128).coerceAtMost(n - 1)] }
                    _isMicrophoneMuted.value = clientSilenced || audioManager.isMicrophoneMute
                }
                if (collected >= analyzer.fftSize && now - lastFft >= 45) {
                    lastFft = now
                    if (fftInput.size != analyzer.fftSize) fftInput = FloatArray(analyzer.fftSize)
                    for (i in fftInput.indices) fftInput[i] = ring[(head - fftInput.size + ring.size + i) % ring.size]
                    _fftFrame.value = analyzer.process(fftInput, smoothing)
                }
                if (sessionDurationLimitMinutes > 0 && reading.sessionDurationMs >= sessionDurationLimitMinutes * 60000L) {
                    completed = true; _meterReading.value = reading; _isSessionCompleted.value = true
                }
            }
            if (completed) {
                withContext(Dispatchers.Main) { pauseCapture(); _isSessionCompleted.value = true }
                break
            }
        }
    }
    // Chart decimation only: retain both endpoints and every other representative point.
    // Whole-session Leq is calculated separately from every captured sample.
    private fun compactPoints() {
        val keep = points.filterIndexed { index, _ -> index == 0 || index % 2 == 1 }
        points.clear(); points.addAll(keep); pointInterval *= 2
    }

    private var calibrationClipped = false
    fun beginCalibrationSampling(durationSeconds: Double = 3.0) = synchronized(dspLock) {
        check(_captureState.value == CaptureState.RECORDING) { "Start microphone capture before calibrating." }
        check(currentRoute != null) { "Wait for Android to report the microphone route." }
        sampler = CalibrationSampler(sampleRate, durationSeconds)
        samplerRouteKey = sessionRouteKey
        calibrationFilter = AWeightingFilter(sampleRate)
        calibrationClipped = false
    }
    fun calibrationProgress(): Float = synchronized(dspLock) { sampler?.progress ?: 0f }
    fun cancelCalibrationSampling() = synchronized(dspLock) {
        sampler = null; calibrationFilter = null; samplerRouteKey = null; calibrationClipped = false
    }
    fun finishCalibrationSampling(referenceSpl: Float): Result<CalibrationProfile> = synchronized(dspLock) {
        runCatching {
            val sample = checkNotNull(sampler) { "Calibration was cancelled or the input changed." }
            check(_captureState.value == CaptureState.RECORDING && samplerRouteKey == sessionRouteKey) { "Audio input changed during calibration." }
            check(!calibrationClipped) { "Input clipped during calibration." }
            val route = checkNotNull(currentRoute) { "Microphone route unavailable." }
            CalibrationProfile(sample.offset(referenceSpl), System.currentTimeMillis(), referenceSpl,
                route.productName.toString(), route.type, sampleRate, captureSource,
                AudioFormat.CHANNEL_IN_MONO, dsp.frequencyWeighting, route.address.ifBlank { "android-device-id:${route.id}" }, AudioFormat.ENCODING_PCM_16BIT)
        }.also { cancelCalibrationSampling() }
    }
}
