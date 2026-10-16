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
            Pair(MediaRecorder.AudioSource.UNPROCESSED, "UNPROCESSED (requested)")
        } else {
            // Fallback to VOICE_RECOGNITION which disables handset tuning filters on most Android devices
            Pair(MediaRecorder.AudioSource.VOICE_RECOGNITION, "VOICE_RECOGNITION (processing unknown)")
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

    class Effects : AutoCloseable {
        private val held = mutableListOf<android.media.audiofx.AudioEffect>()
        var agcAvailable = false; var agcDisabled = false
        var nsAvailable = false; var nsDisabled = false
        var aecAvailable = false; var aecDisabled = false
        fun disable(effect: android.media.audiofx.AudioEffect?): Boolean {
            if (effect == null) return false
            held.add(effect)
            return try { effect.enabled = false; !effect.enabled } catch (_: Exception) { false }
        }
        override fun close() { held.forEach { try { it.release() } catch (_: Exception) {} }; held.clear() }
    }
    fun disableEffects(sessionId: Int): Effects = Effects().apply {
        try { agcAvailable = AutomaticGainControl.isAvailable(); if (agcAvailable) agcDisabled = disable(AutomaticGainControl.create(sessionId)) } catch (_: Exception) {}
        try { nsAvailable = NoiseSuppressor.isAvailable(); if (nsAvailable) nsDisabled = disable(NoiseSuppressor.create(sessionId)) } catch (_: Exception) {}
        try { aecAvailable = AcousticEchoCanceler.isAvailable(); if (aecAvailable) aecDisabled = disable(AcousticEchoCanceler.create(sessionId)) } catch (_: Exception) {}
    }
}
