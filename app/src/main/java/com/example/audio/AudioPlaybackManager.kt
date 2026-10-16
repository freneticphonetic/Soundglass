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
import com.example.dsp.WaveformType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AudioPlaybackManager(private val context: Context) {

    val engine = ToneGeneratorEngine(48000)

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentFrequencyHz = MutableStateFlow(440.0)
    val currentFrequencyHz: StateFlow<Double> = _currentFrequencyHz.asStateFlow()

    private val _sweepProgress = MutableStateFlow(0.0f)
    val sweepProgress: StateFlow<Float> = _sweepProgress.asStateFlow()

    private var audioTrack: AudioTrack? = null
    private var playbackJob: Job? = null
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
        if (_isPlaying.value) return

        // Request audio focus
        val focusResult = audioManager?.requestAudioFocus(
            audioFocusListener,
            AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN
        )

        if (focusResult != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            return
        }

        // Register noisy receiver
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
        val bufferSize = minBufferSize.coerceAtLeast(2048) * 2

        val track = AudioTrack.Builder()
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

        audioTrack = track
        engine.start()
        track.play()
        _isPlaying.value = true

        playbackJob = scope.launch(Dispatchers.IO) {
            val chunk = 512
            val shortBuffer = ShortArray(chunk)

            while (isActive && !engine.isStopped()) {
                val samplesRendered = engine.renderBlock(shortBuffer, chunk)
                if (samplesRendered > 0) {
                    track.write(shortBuffer, 0, samplesRendered)
                }

                _currentFrequencyHz.value = engine.currentFrequencyHz
                _sweepProgress.value = engine.sweepProgress

                if (engine.isStopped()) {
                    break
                }
            }

            // Cleanup track after loop finishes
            try {
                track.stop()
            } catch (_: Exception) {}
            try {
                track.release()
            } catch (_: Exception) {}

            _isPlaying.value = false
        }
    }

    fun stopPlayback() {
        if (!_isPlaying.value) return
        engine.stop()

        // Wait brief moment for ramp-down then cancel
        try {
            if (isReceiverRegistered) {
                context.unregisterReceiver(noisyReceiver)
                isReceiverRegistered = false
            }
        } catch (_: Exception) {}

        audioManager?.abandonAudioFocus(audioFocusListener)
        _isPlaying.value = false
    }

    fun release() {
        stopPlayback()
        playbackJob?.cancel()
        playbackJob = null
        try {
            audioTrack?.release()
        } catch (_: Exception) {}
        audioTrack = null
    }
}
