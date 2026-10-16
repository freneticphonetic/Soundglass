package com.example.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.example.dsp.ToneGeneratorEngine
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

class AudioPlaybackManager(private val context: Context) {

    val engine = ToneGeneratorEngine(48000)

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentFrequencyHz = MutableStateFlow(440.0)
    val currentFrequencyHz: StateFlow<Double> = _currentFrequencyHz.asStateFlow()

    private val _sweepProgress = MutableStateFlow(0.0f)
    val sweepProgress: StateFlow<Float> = _sweepProgress.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var audioTrack: AudioTrack? = null
    private var playbackJob: Job? = null
    private val playbackMutex = Mutex()
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        if (focusChange == AudioManager.AUDIOFOCUS_LOSS ||
            focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
        ) {
            stopPlayback()
        }
    }

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                stopPlayback()
            }
        }
    }
    private var isReceiverRegistered = false

    fun startPlayback(scope: CoroutineScope) {
        scope.launch {
            playbackMutex.withLock {
                if (_isPlaying.value) return@withLock
                _errorMessage.value = null

                // Request audio focus
                val focusResult = audioManager?.requestAudioFocus(
                    audioFocusListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN
                )

                if (focusResult != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    _errorMessage.value = "Audio focus request denied by system."
                    return@withLock
                }

                // Register headphone disconnect listener
                try {
                    if (!isReceiverRegistered) {
                        context.registerReceiver(
                            noisyReceiver,
                            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
                        )
                        isReceiverRegistered = true
                    }
                } catch (_: Exception) {}

                val sampleRate = engine.sampleRate
                val minBufferSize = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                if (minBufferSize <= 0) {
                    _errorMessage.value = "Hardware does not support requested audio output configuration."
                    return@withLock
                }

                val bufferSize = minBufferSize.coerceAtLeast(2048) * 2

                val track = try {
                    AudioTrack.Builder()
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build()
                        )
                        .setAudioFormat(
                            AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(sampleRate)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build()
                        )
                        .setBufferSizeInBytes(bufferSize)
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .build()
                } catch (e: Exception) {
                    _errorMessage.value = "AudioTrack creation failed: ${e.localizedMessage ?: "Unknown error"}"
                    return@withLock
                }

                if (track.state != AudioTrack.STATE_INITIALIZED) {
                    track.release()
                    _errorMessage.value = "AudioTrack failed to initialize."
                    return@withLock
                }

                audioTrack = track
                engine.start()
                try {
                    track.play()
                } catch (e: Exception) {
                    track.release()
                    audioTrack = null
                    _errorMessage.value = "AudioTrack play failed: ${e.localizedMessage ?: "Device error"}"
                    return@withLock
                }

                _isPlaying.value = true

                playbackJob = scope.launch(Dispatchers.IO) {
                    val chunk = 512
                    val shortBuffer = ShortArray(chunk)

                    try {
                        while (isActive && !engine.isStopped()) {
                            val samplesRendered = engine.renderBlock(shortBuffer, chunk)
                            if (samplesRendered > 0) {
                                val written = track.write(shortBuffer, 0, samplesRendered)
                                if (written < 0) {
                                    // AudioTrack error
                                    break
                                }
                            }

                            _currentFrequencyHz.value = engine.currentFrequencyHz
                            _sweepProgress.value = engine.sweepProgress

                            if (engine.isStopped()) {
                                break
                            }
                        }
                    } finally {
                        withContext(Dispatchers.IO) {
                            cleanupTrack(track)
                        }
                    }
                }
            }
        }
    }

    private fun cleanupTrack(track: AudioTrack) {
        try {
            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                track.stop()
            }
        } catch (_: Exception) {}
        try {
            track.release()
        } catch (_: Exception) {}

        if (audioTrack === track) {
            audioTrack = null
        }
        _isPlaying.value = false

        try {
            if (isReceiverRegistered) {
                context.unregisterReceiver(noisyReceiver)
                isReceiverRegistered = false
            }
        } catch (_: Exception) {}

        audioManager?.abandonAudioFocus(audioFocusListener)
    }

    fun stopPlayback() {
        engine.stop()
    }

    /**
     * Suspends until the generator has completely ramped down, stopped, and released AudioTrack.
     * Essential for mutual exclusivity before opening microphone capture.
     */
    suspend fun stopPlaybackAndWait() {
        stopPlayback()
        playbackJob?.join()
        playbackMutex.withLock {
            audioTrack?.let { cleanupTrack(it) }
        }
    }

    fun release() {
        stopPlayback()
        playbackJob?.cancel()
        playbackJob = null
        audioTrack?.let { cleanupTrack(it) }
    }
}
