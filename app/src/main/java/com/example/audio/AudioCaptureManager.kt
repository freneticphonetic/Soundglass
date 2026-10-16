package com.example.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import com.example.data.entity.TimeSeriesPoint
import com.example.dsp.AudioConstants
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
import kotlin.math.abs

enum class CaptureState {
    IDLE, RECORDING, PAUSED, ERROR
}

class AudioCaptureManager(private val context: Context) {

    private val _captureState = MutableStateFlow(CaptureState.IDLE)
    val captureState: StateFlow<CaptureState> = _captureState.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _diagnostics = MutableStateFlow<AudioHardwareDiagnostics?>(null)
    val diagnostics: StateFlow<AudioHardwareDiagnostics?> = _diagnostics.asStateFlow()

    // Captured time series for active session
    private val timeSeriesLock = Any()
    private val activeTimeSeries = mutableListOf<TimeSeriesPoint>()

    // DSP components
    private var sampleRate: Int = 48000
    private var levelMeterDsp = LevelMeterDsp(sampleRate)
    private var fftAnalyzer = FftAnalyzer(4096, sampleRate)
    private var spectrumSmoothing = SpectrumSmoothing.NONE

    private val _meterReading = MutableStateFlow(levelMeterDsp.currentReading(System.currentTimeMillis()))
    val meterReading: StateFlow<MeterReading> = _meterReading.asStateFlow()

    private val _waveform = MutableStateFlow(FloatArray(128))
    val waveform: StateFlow<FloatArray> = _waveform.asStateFlow()

    private val _fftFrame = MutableStateFlow<FftAnalysisFrame?>(null)
    val fftFrame: StateFlow<FftAnalysisFrame?> = _fftFrame.asStateFlow()

    private val _isMicrophoneMuted = MutableStateFlow(false)
    val isMicrophoneMuted: StateFlow<Boolean> = _isMicrophoneMuted.asStateFlow()

    // Recording job & AudioRecord instance
    private var captureJob: Job? = null
    private var audioRecord: AudioRecord? = null

    // Session duration limit in minutes (0 = unlimited)
    var sessionDurationLimitMinutes: Int = 30

    fun configureSettings(
        timeWeighting: TimeWeighting,
        frequencyWeighting: FrequencyWeighting,
        calibrationOffsetDb: Float?,
        fftSize: Int,
        smoothing: SpectrumSmoothing
    ) {
        levelMeterDsp.setTimeWeighting(timeWeighting)
        levelMeterDsp.setFrequencyWeighting(frequencyWeighting)
        levelMeterDsp.setCalibrationOffset(calibrationOffsetDb)
        this.spectrumSmoothing = smoothing

        if (fftAnalyzer.fftSize != fftSize) {
            fftAnalyzer = FftAnalyzer(fftSize, sampleRate)
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
        }
        levelMeterDsp.resetSessionStats()
        _meterReading.value = levelMeterDsp.currentReading(System.currentTimeMillis())
    }

    @SuppressLint("MissingPermission")
    fun startCapture(scope: CoroutineScope) {
        if (_captureState.value == CaptureState.RECORDING) return

        if (_captureState.value == CaptureState.PAUSED) {
            _captureState.value = CaptureState.RECORDING
            return
        }

        clearSessionData()
        levelMeterDsp.resetAll()
        fftAnalyzer.reset()
        _errorMessage.value = null

        // Negotiate sample rate: try 48000 then 44100
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
            _errorMessage.value = "Hardware error: No supported audio capture configuration found."
            return
        }

        sampleRate = selectedRate
        levelMeterDsp = LevelMeterDsp(
            sampleRate = sampleRate,
            initialTimeWeighting = levelMeterDsp.timeWeighting,
            initialFrequencyWeighting = levelMeterDsp.frequencyWeighting,
            initialCalibrationOffset = if (levelMeterDsp.isCalibrated) levelMeterDsp.calibrationOffsetDb else null
        )
        fftAnalyzer = FftAnalyzer(fftAnalyzer.fftSize, sampleRate)

        val (audioSource, sourceLabel) = AudioDeviceHelper.getPreferredAudioSource(context)

        try {
            val record = AudioRecord(
                audioSource,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                _captureState.value = CaptureState.ERROR
                _errorMessage.value = "AudioRecord initialization failed. Microphone may be in use."
                return
            }

            audioRecord = record

            // Inspect and suppress hardware DSP effects
            val effectsResult = AudioDeviceHelper.inspectAndDisableEffects(record.audioSessionId)

            _diagnostics.value = AudioHardwareDiagnostics(
                selectedSource = sourceLabel,
                isUnprocessedSupported = AudioDeviceHelper.isUnprocessedSourceSupported(context),
                sampleRate = sampleRate,
                channelConfig = "CHANNEL_IN_MONO",
                encoding = "ENCODING_PCM_16BIT",
                activeMicrophoneName = AudioDeviceHelper.getActiveInputRouteName(context),
                agcAvailable = effectsResult.first.first,
                agcDisabled = effectsResult.first.second,
                nsAvailable = effectsResult.second.first,
                nsDisabled = effectsResult.second.second,
                aecAvailable = effectsResult.third.first,
                aecDisabled = effectsResult.third.second
            )

            record.startRecording()
            _captureState.value = CaptureState.RECORDING

            // Launch capture loop in IO thread
            captureJob = scope.launch(Dispatchers.IO) {
                runCaptureLoop(record)
            }

        } catch (e: Exception) {
            _captureState.value = CaptureState.ERROR
            _errorMessage.value = "Failed to start microphone: ${e.localizedMessage ?: "Unknown error"}"
            stopCapture()
        }
    }

    fun pauseCapture() {
        if (_captureState.value == CaptureState.RECORDING) {
            _captureState.value = CaptureState.PAUSED
        }
    }

    fun resumeCapture() {
        if (_captureState.value == CaptureState.PAUSED) {
            _captureState.value = CaptureState.RECORDING
        }
    }

    fun stopCapture() {
        _captureState.value = CaptureState.IDLE
        captureJob?.cancel()
        captureJob = null

        try {
            audioRecord?.stop()
        } catch (_: Exception) {}
        try {
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null
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

        while (kotlin.coroutines.coroutineContext.isActive) {
            if (_captureState.value == CaptureState.PAUSED) {
                Thread.sleep(50)
                continue
            }
            if (_captureState.value != CaptureState.RECORDING) {
                break
            }

            // Check microphone mute toggle
            _isMicrophoneMuted.value = audioManager?.isMicrophoneMute ?: false

            val readCount = record.read(rawPcmBuffer, 0, chunkSamples)
            if (readCount <= 0) {
                if (readCount == AudioRecord.ERROR_INVALID_OPERATION || readCount == AudioRecord.ERROR_BAD_VALUE) {
                    _captureState.value = CaptureState.ERROR
                    _errorMessage.value = "Microphone capture interrupted."
                    break
                }
                continue
            }

            val now = System.currentTimeMillis()

            // Convert to normalized float [-1.0, 1.0]
            for (i in 0 until readCount) {
                val s = rawPcmBuffer[i] / AudioConstants.FULL_SCALE_16_BIT
                floatSamples[i] = s

                // Add to FFT ring buffer
                fftRingBuffer[ringHead] = s
                ringHead = (ringHead + 1) % fftRingBufferSize
            }

            // Update level meter DSP
            val reading = levelMeterDsp.processBlock(floatSamples, readCount, now)
            _meterReading.value = reading

            // Downsample waveform for live display (128 points)
            val step = readCount / 128
            if (step > 0) {
                for (i in 0 until 128) {
                    waveformDisplay[i] = floatSamples[i * step]
                }
                _waveform.value = waveformDisplay.clone()
            }

            // Decimate and save time-series for recording history (~2-4 points per sec)
            if (now - lastTimeSeriesSampleMs >= 300L) {
                lastTimeSeriesSampleMs = now
                synchronized(timeSeriesLock) {
                    if (activeTimeSeries.size < 10000) {
                        activeTimeSeries.add(
                            TimeSeriesPoint(
                                offsetMs = reading.sessionDurationMs,
                                levelDb = reading.currentLevelDb,
                                peakDbFS = reading.samplePeakDbFS
                            )
                        )
                    }
                }
            }

            // Check session duration limit
            if (sessionDurationLimitMinutes > 0) {
                val limitMs = sessionDurationLimitMinutes * 60 * 1000L
                if (reading.sessionDurationMs >= limitMs) {
                    pauseCapture()
                }
            }

            // Update FFT analysis periodically (approx 20-25 fps -> every ~40-50ms)
            if (now - lastFftProcessMs >= 45L) {
                lastFftProcessMs = now
                val curFftSize = fftAnalyzer.fftSize
                val linearFftInput = FloatArray(curFftSize)
                // Extract contiguous window from ring buffer
                var readIdx = (ringHead - curFftSize + fftRingBufferSize) % fftRingBufferSize
                for (i in 0 until curFftSize) {
                    linearFftInput[i] = fftRingBuffer[readIdx]
                    readIdx = (readIdx + 1) % fftRingBufferSize
                }

                val frame = fftAnalyzer.process(linearFftInput, spectrumSmoothing)
                _fftFrame.value = frame
            }
        }
    }
}
