package com.example.data.preferences

import android.content.Context
import android.media.AudioFormat
import android.media.MediaRecorder
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.dsp.CalibrationProfile
import com.example.dsp.FrequencyWeighting
import com.example.dsp.TimeWeighting
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "soundglass_settings")

data class SoundglassPreferences(
    val isCalibrated: Boolean,
    val calibrationOffsetDb: Float,
    val calibrationDateMs: Long,
    val calibratedDeviceName: String,
    val calibratedDeviceType: Int,
    val calibratedSampleRate: Int,
    val calibratedCaptureSource: Int,
    val calibratedChannelConfig: Int,
    val calibratedFrequencyWeighting: FrequencyWeighting,
    val calibratedDeviceAddress: String,
    val calibratedEncoding: Int,
    val referenceSpl: Float,
    val defaultFrequencyWeighting: FrequencyWeighting,
    val defaultTimeWeighting: TimeWeighting,
    val defaultFftSize: Int,
    val sessionDurationLimitMinutes: Int,
    val themeMode: String // "SYSTEM", "DARK", "LIGHT"
) {
    fun toCalibrationProfile(): CalibrationProfile? {
        if (!isCalibrated) return null
        return CalibrationProfile(
            offsetDb = calibrationOffsetDb,
            timestampMs = calibrationDateMs,
            referenceSpl = referenceSpl,
            deviceName = calibratedDeviceName,
            deviceType = calibratedDeviceType,
            sampleRate = calibratedSampleRate,
            captureSource = calibratedCaptureSource,
            channelConfig = calibratedChannelConfig,
            frequencyWeighting = calibratedFrequencyWeighting,
            deviceAddress = calibratedDeviceAddress,
            encoding = calibratedEncoding
        )
    }
}

class PreferencesManager(private val context: Context) {
    companion object {
        private val KEY_CALIBRATED_ADDRESS = stringPreferencesKey("calibrated_address")
        private val KEY_CALIBRATED_ENCODING = intPreferencesKey("calibrated_encoding")
        private val KEY_IS_CALIBRATED = booleanPreferencesKey("is_calibrated")
        private val KEY_CALIBRATION_OFFSET = floatPreferencesKey("calibration_offset_db")
        private val KEY_CALIBRATION_DATE = longPreferencesKey("calibration_date_ms")
        private val KEY_CALIBRATED_DEVICE = stringPreferencesKey("calibrated_device_name")
        private val KEY_CALIBRATED_DEVICE_TYPE = intPreferencesKey("calibrated_device_type")
        private val KEY_CALIBRATED_SAMPLE_RATE = intPreferencesKey("calibrated_sample_rate")
        private val KEY_CALIBRATED_CAPTURE_SOURCE = intPreferencesKey("calibrated_capture_source")
        private val KEY_CALIBRATED_CHANNEL_CONFIG = intPreferencesKey("calibrated_channel_config")
        private val KEY_CALIBRATED_FREQ_WEIGHTING = stringPreferencesKey("calibrated_freq_weighting")
        private val KEY_CALIBRATED_REF_SPL = floatPreferencesKey("calibrated_ref_spl")

        private val KEY_FREQ_WEIGHTING = stringPreferencesKey("default_freq_weighting")
        private val KEY_TIME_WEIGHTING = stringPreferencesKey("default_time_weighting")
        private val KEY_FFT_SIZE = intPreferencesKey("default_fft_size")
        private val KEY_SESSION_LIMIT = intPreferencesKey("session_limit_minutes")
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
    }

    val preferencesFlow: Flow<SoundglassPreferences> = context.dataStore.data.map { prefs ->
        val isCalibrated = prefs[KEY_IS_CALIBRATED] ?: false
        val offset = prefs[KEY_CALIBRATION_OFFSET] ?: 0.0f
        val date = prefs[KEY_CALIBRATION_DATE] ?: 0L
        val device = prefs[KEY_CALIBRATED_DEVICE] ?: "Default Microphone"
        val deviceType = prefs[KEY_CALIBRATED_DEVICE_TYPE] ?: 0
        val rate = prefs[KEY_CALIBRATED_SAMPLE_RATE] ?: 48000
        val captureSource = prefs[KEY_CALIBRATED_CAPTURE_SOURCE] ?: MediaRecorder.AudioSource.UNPROCESSED
        val channelConfig = prefs[KEY_CALIBRATED_CHANNEL_CONFIG] ?: AudioFormat.CHANNEL_IN_MONO
        val calFreqStr = prefs[KEY_CALIBRATED_FREQ_WEIGHTING] ?: FrequencyWeighting.A.name
        val calFreqWeighting = try {
            FrequencyWeighting.valueOf(calFreqStr)
        } catch (_: Exception) {
            FrequencyWeighting.A
        }
        val refSpl = prefs[KEY_CALIBRATED_REF_SPL] ?: 94.0f

        val freqStr = prefs[KEY_FREQ_WEIGHTING] ?: FrequencyWeighting.A.name
        val freqWeighting = try {
            FrequencyWeighting.valueOf(freqStr)
        } catch (_: Exception) {
            FrequencyWeighting.A
        }

        val timeStr = prefs[KEY_TIME_WEIGHTING] ?: TimeWeighting.FAST.name
        val timeWeighting = try {
            TimeWeighting.valueOf(timeStr)
        } catch (_: Exception) {
            TimeWeighting.FAST
        }

        val fftSize = prefs[KEY_FFT_SIZE] ?: 4096
        val limit = prefs[KEY_SESSION_LIMIT] ?: 30 // Default 30 min limit
        val theme = prefs[KEY_THEME_MODE] ?: "DARK"

        SoundglassPreferences(
            isCalibrated = isCalibrated,
            calibrationOffsetDb = offset,
            calibrationDateMs = date,
            calibratedDeviceName = device,
            calibratedDeviceType = deviceType,
            calibratedSampleRate = rate,
            calibratedCaptureSource = captureSource,
            calibratedChannelConfig = channelConfig,
            calibratedFrequencyWeighting = calFreqWeighting,
            calibratedDeviceAddress = prefs[KEY_CALIBRATED_ADDRESS] ?: "",
            calibratedEncoding = prefs[KEY_CALIBRATED_ENCODING] ?: AudioFormat.ENCODING_PCM_16BIT,
            referenceSpl = refSpl,
            defaultFrequencyWeighting = freqWeighting,
            defaultTimeWeighting = timeWeighting,
            defaultFftSize = fftSize,
            sessionDurationLimitMinutes = limit,
            themeMode = theme
        )
    }

    suspend fun saveCalibration(
        offsetDb: Float,
        deviceName: String,
        sampleRate: Int,
        deviceType: Int = 0,
        captureSource: Int = MediaRecorder.AudioSource.UNPROCESSED,
        channelConfig: Int = AudioFormat.CHANNEL_IN_MONO,
        frequencyWeighting: FrequencyWeighting = FrequencyWeighting.A,
        referenceSpl: Float = 94.0f,
        deviceAddress: String = "",
        encoding: Int = AudioFormat.ENCODING_PCM_16BIT
    ) {
        context.dataStore.edit { prefs ->
            prefs[KEY_CALIBRATED_ADDRESS] = deviceAddress
            prefs[KEY_CALIBRATED_ENCODING] = encoding
            prefs[KEY_IS_CALIBRATED] = true
            prefs[KEY_CALIBRATION_OFFSET] = offsetDb
            prefs[KEY_CALIBRATION_DATE] = System.currentTimeMillis()
            prefs[KEY_CALIBRATED_DEVICE] = deviceName
            prefs[KEY_CALIBRATED_DEVICE_TYPE] = deviceType
            prefs[KEY_CALIBRATED_SAMPLE_RATE] = sampleRate
            prefs[KEY_CALIBRATED_CAPTURE_SOURCE] = captureSource
            prefs[KEY_CALIBRATED_CHANNEL_CONFIG] = channelConfig
            prefs[KEY_CALIBRATED_FREQ_WEIGHTING] = frequencyWeighting.name
            prefs[KEY_CALIBRATED_REF_SPL] = referenceSpl
        }
    }

    suspend fun saveCalibration(profile: CalibrationProfile) {
        saveCalibration(
            offsetDb = profile.offsetDb,
            deviceName = profile.deviceName,
            sampleRate = profile.sampleRate,
            deviceType = profile.deviceType,
            captureSource = profile.captureSource,
            channelConfig = profile.channelConfig,
            frequencyWeighting = profile.frequencyWeighting,
            referenceSpl = profile.referenceSpl,
            deviceAddress = profile.deviceAddress,
            encoding = profile.encoding
        )
    }

    suspend fun resetCalibration() {
        context.dataStore.edit { prefs ->
            prefs[KEY_IS_CALIBRATED] = false
            prefs[KEY_CALIBRATION_OFFSET] = 0.0f
            prefs[KEY_CALIBRATION_DATE] = 0L
            prefs[KEY_CALIBRATED_DEVICE] = ""
            prefs[KEY_CALIBRATED_DEVICE_TYPE] = 0
            prefs[KEY_CALIBRATED_SAMPLE_RATE] = 0
            prefs[KEY_CALIBRATED_CAPTURE_SOURCE] = 0
            prefs[KEY_CALIBRATED_CHANNEL_CONFIG] = 0
            prefs[KEY_CALIBRATED_FREQ_WEIGHTING] = ""
            prefs[KEY_CALIBRATED_REF_SPL] = 94.0f
        }
    }

    suspend fun updateFrequencyWeighting(weighting: FrequencyWeighting) {
        context.dataStore.edit { prefs ->
            prefs[KEY_FREQ_WEIGHTING] = weighting.name
        }
    }

    suspend fun updateTimeWeighting(weighting: TimeWeighting) {
        context.dataStore.edit { prefs ->
            prefs[KEY_TIME_WEIGHTING] = weighting.name
        }
    }

    suspend fun updateFftSize(size: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_FFT_SIZE] = size
        }
    }

    suspend fun updateSessionLimit(minutes: Int) {
        context.dataStore.edit { prefs ->
            require(minutes in listOf(0, 5, 15, 30, 60))
            prefs[KEY_SESSION_LIMIT] = minutes
        }
    }

    suspend fun updateThemeMode(mode: String) {
        context.dataStore.edit { prefs ->
            require(mode in listOf("SYSTEM", "DARK", "LIGHT"))
            prefs[KEY_THEME_MODE] = mode
        }
    }
}
