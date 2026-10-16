package com.example.audio

import android.content.*
import android.media.*
import androidx.core.content.ContextCompat
import com.example.dsp.ToneGeneratorEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AudioPlaybackManager(private val context: Context) {
    val engine = ToneGeneratorEngine(48000)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutex = Mutex()
    private var epoch = 0L
    private var foreground = false
    private var job: Job? = null
    private var hardware: OwnedResource<AudioTrack>? = null
    var beforePlayback: suspend () -> Unit = {}
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()
    private val _currentFrequencyHz = MutableStateFlow(440.0)
    val currentFrequencyHz = _currentFrequencyHz.asStateFlow()
    private val _sweepProgress = MutableStateFlow(0f)
    val sweepProgress = _sweepProgress.asStateFlow()
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()
    private var focus: AudioFocusRequest? = null
    private val listener = AudioManager.OnAudioFocusChangeListener {
        if (it != AudioManager.AUDIOFOCUS_GAIN) stopImmediately()
    }
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) stopImmediately()
        }
    }
    private var receiverRegistered = false

    fun setForeground(active: Boolean) { foreground = active; if (!active) stopImmediately() }
    fun startPlayback(unusedScope: CoroutineScope) {
        if (!foreground || _isPlaying.value) return
        val request = ++epoch
        scope.launch {
            beforePlayback()
            mutex.withLock {
                stopWorkerAndWait()
                if (!foreground || request != epoch) return@withLock
                _errorMessage.value = null
                try {
                    val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
                    val f = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(attributes).setOnAudioFocusChangeListener(listener).build()
                    check(audioManager.requestAudioFocus(f) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { "Audio focus was denied." }
                    focus = f
                    ContextCompat.registerReceiver(context, noisyReceiver,
                        IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), ContextCompat.RECEIVER_NOT_EXPORTED)
                    receiverRegistered = true
                    val min = AudioTrack.getMinBufferSize(engine.sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
                    check(min > 0) { "Audio output configuration unsupported." }
                    val track = AudioTrack.Builder().setAudioAttributes(attributes)
                        .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(engine.sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                        .setBufferSizeInBytes(maxOf(min, 2048) * 2).setTransferMode(AudioTrack.MODE_STREAM).build()
                    val owned = OwnedResource(track) {
                        try { it.pause(); it.flush(); it.stop() } catch (_: Exception) { }
                        it.release()
                    }
                    hardware = owned
                    check(track.state == AudioTrack.STATE_INITIALIZED) { "Audio output could not initialize." }
                    engine.start(); track.play(); _isPlaying.value = true
                    job = scope.launch(Dispatchers.IO) {
                        try {
                            val buffer = ShortArray(512)
                            while (isActive && request == epoch && !owned.isClosed && !engine.isStopped()) {
                                val count = engine.renderBlock(buffer, buffer.size)
                                var offset = 0
                                while (offset < count && isActive && !owned.isClosed) {
                                    val n = track.write(buffer, offset, count - offset)
                                    check(n >= 0) { "Audio output write failed ($n)." }
                                    if (n == 0) delay(2) else offset += n
                                }
                                _currentFrequencyHz.value = engine.currentFrequencyHz
                                _sweepProgress.value = engine.sweepProgress
                            }
                        } catch (e: CancellationException) { throw e }
                        catch (e: Exception) {
                            if (!owned.isClosed) _errorMessage.value = e.localizedMessage ?: "Audio output interrupted."
                        } finally {
                            owned.close()
                            withContext(NonCancellable + Dispatchers.Main.immediate) {
                                if (hardware === owned) { hardware = null; _isPlaying.value = false; releaseFocus() }
                            }
                        }
                    }
                } catch (e: Exception) {
                    hardware?.close(); hardware = null; _isPlaying.value = false; releaseFocus()
                    _errorMessage.value = e.localizedMessage ?: "Unable to start audio output."
                }
            }
        }
    }
    private fun releaseFocus() {
        if (receiverRegistered) {
            try { context.unregisterReceiver(noisyReceiver) } catch (_: IllegalArgumentException) { }
            receiverRegistered = false
        }
        focus?.let { audioManager.abandonAudioFocusRequest(it) }; focus = null
    }
    fun stopPlayback() { engine.stop() }
    // Lifecycle and route interruption demand immediate hardware release, not queued output.
    fun stopImmediately() {
        ++epoch; engine.stop(); hardware?.close(); job?.cancel()
        _isPlaying.value = false; releaseFocus()
    }
    private suspend fun stopWorkerAndWait() {
        engine.stop()
        val previous = job
        withTimeoutOrNull(200) { previous?.join() }
        hardware?.close(); previous?.cancelAndJoin()
        job = null; hardware = null; _isPlaying.value = false; releaseFocus()
    }
    suspend fun stopPlaybackAndWait() {
        ++epoch
        mutex.withLock { stopWorkerAndWait() }
    }
    fun release() = stopImmediately()
}
