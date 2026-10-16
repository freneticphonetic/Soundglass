package com.example.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Build

data class AudioHardwareDiagnostics(
    val selectedSource: String,
    val isUnprocessedSupported: Boolean,
    val sampleRate: Int,
    val channelConfig: String,
    val encoding: String,
    val activeMicrophoneName: String,
    val agcAvailable: Boolean,
    val agcDisabled: Boolean,
    val nsAvailable: Boolean,
    val nsDisabled: Boolean,
    val aecAvailable: Boolean,
    val aecDisabled: Boolean
)

object AudioDeviceHelper {

    fun isUnprocessedSourceSupported(context: Context): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        val prop = audioManager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED)
        return prop?.toBoolean() ?: false
    }

    fun getPreferredAudioSource(context: Context): Pair<Int, String> {
        return if (isUnprocessedSourceSupported(context)) {
            Pair(MediaRecorder.AudioSource.UNPROCESSED, "UNPROCESSED (Raw Hardware Direct)")
        } else {
            // Fallback to VOICE_RECOGNITION which disables handset tuning filters on most Android devices
            Pair(MediaRecorder.AudioSource.VOICE_RECOGNITION, "VOICE_RECOGNITION (Fallback Unprocessed)")
        }
    }

    fun getActiveInputRouteName(context: Context): String {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return "Default Microphone"
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
        for (device in devices) {
            when (device.type) {
                AudioDeviceInfo.TYPE_BUILTIN_MIC -> return "Built-in Microphone"
                AudioDeviceInfo.TYPE_WIRED_HEADSET -> return "Wired Headset Mic"
                AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> return "USB Audio Device"
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BLE_HEADSET -> return "Bluetooth Microphone"
            }
        }
        return "Built-in Microphone"
    }

    fun getActiveInputDeviceType(context: Context): Int {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return AudioDeviceInfo.TYPE_BUILTIN_MIC
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
        for (device in devices) {
            when (device.type) {
                AudioDeviceInfo.TYPE_BUILTIN_MIC,
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_USB_DEVICE,
                AudioDeviceInfo.TYPE_USB_HEADSET,
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                AudioDeviceInfo.TYPE_BLE_HEADSET -> return device.type
            }
        }
        return AudioDeviceInfo.TYPE_BUILTIN_MIC
    }

    /**
     * Inspects acoustic effects and attempts to disable them on the given audioSessionId.
     * Returns whether each effect was available and successfully disabled.
     */
    fun inspectAndDisableEffects(audioSessionId: Int): Triple<Pair<Boolean, Boolean>, Pair<Boolean, Boolean>, Pair<Boolean, Boolean>> {
        // Automatic Gain Control (AGC)
        var agcAvailable = false
        var agcDisabled = false
        try {
            if (AutomaticGainControl.isAvailable()) {
                agcAvailable = true
                val agc = AutomaticGainControl.create(audioSessionId)
                if (agc != null) {
                    agc.enabled = false
                    agcDisabled = !agc.enabled
                    agc.release()
                }
            }
        } catch (_: Exception) {}

        // Noise Suppressor (NS)
        var nsAvailable = false
        var nsDisabled = false
        try {
            if (NoiseSuppressor.isAvailable()) {
                nsAvailable = true
                val ns = NoiseSuppressor.create(audioSessionId)
                if (ns != null) {
                    ns.enabled = false
                    nsDisabled = !ns.enabled
                    ns.release()
                }
            }
        } catch (_: Exception) {}

        // Acoustic Echo Canceler (AEC)
        var aecAvailable = false
        var aecDisabled = false
        try {
            if (AcousticEchoCanceler.isAvailable()) {
                aecAvailable = true
                val aec = AcousticEchoCanceler.create(audioSessionId)
                if (aec != null) {
                    aec.enabled = false
                    aecDisabled = !aec.enabled
                    aec.release()
                }
            }
        } catch (_: Exception) {}

        return Triple(
            Pair(agcAvailable, agcDisabled),
            Pair(nsAvailable, nsDisabled),
            Pair(aecAvailable, aecDisabled)
        )
    }
}
