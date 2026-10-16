package com.example.dsp

/**
 * Result of validating an existing calibration profile against the active audio hardware route and DSP configuration.
 */
data class CalibrationValidationResult(
    val isValid: Boolean,
    val invalidationReason: String? = null
)

/**
 * Immutable calibration profile tied strictly to a specific audio hardware configuration.
 *
 * A scalar sensitivity offset only represents broadband sensitivity calibration at the reference frequency
 * (typically 1 kHz) under that specific input device, route, sample rate, and weighting configuration.
 * It does not alter or correct the microphone's physical frequency response curve.
 */
data class CalibrationProfile(
    val offsetDb: Float,
    val timestampMs: Long,
    val referenceSpl: Float,
    val deviceName: String,
    val deviceType: Int,
    val sampleRate: Int,
    val captureSource: Int,
    val channelConfig: Int,
    val frequencyWeighting: FrequencyWeighting,
    val deviceAddress: String = "",
    val encoding: Int = 2
) {
    /**
     * Verifies that the active audio input configuration still satisfies the calibration parameters.
     */
    fun validate(
        currentDeviceType: Int,
        currentDeviceName: String,
        currentSampleRate: Int,
        currentCaptureSource: Int,
        currentFreqWeighting: FrequencyWeighting,
        currentDeviceAddress: String = "",
        currentChannelConfig: Int = channelConfig,
        currentEncoding: Int = encoding
    ): CalibrationValidationResult {
        if (currentDeviceType == 0 || deviceType == 0 || currentDeviceType != deviceType || currentDeviceName != deviceName || currentDeviceAddress != deviceAddress) {
            return CalibrationValidationResult(
                isValid = false,
                invalidationReason = "Audio input changed from '$deviceName' to '$currentDeviceName'."
            )
        }
        if (currentChannelConfig != channelConfig || currentEncoding != encoding) {
            return CalibrationValidationResult(false, "Microphone channel or encoding configuration changed.")
        }
        if (currentSampleRate != sampleRate) {
            return CalibrationValidationResult(
                isValid = false,
                invalidationReason = "Capture sample rate changed from $sampleRate Hz to $currentSampleRate Hz."
            )
        }
        if (currentCaptureSource != captureSource) {
            return CalibrationValidationResult(
                isValid = false,
                invalidationReason = "Microphone capture source changed."
            )
        }
        if (currentFreqWeighting != frequencyWeighting) {
            return CalibrationValidationResult(
                isValid = false,
                invalidationReason = "Weighting changed from ${frequencyWeighting.shortName}-Weighting to ${currentFreqWeighting.shortName}-Weighting."
            )
        }
        return CalibrationValidationResult(isValid = true)
    }
}
