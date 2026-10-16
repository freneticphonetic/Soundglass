package com.example.dsp

import kotlin.math.ln

/**
 * Standard DSP constants, tolerances, and calibration limits for Soundglass.
 */
object AudioConstants {
    /** Signed 16-bit PCM full scale magnitude (32768.0f). */
    const val FULL_SCALE_16_BIT = 32768.0f

    /** Threshold to consider a 16-bit PCM sample clipped (-32768 to 32767). */
    const val CLIPPING_THRESHOLD_INT16 = 32760
    const val CLIPPING_THRESHOLD_FLOAT = 0.9997f

    /** Minimum decibel floor to avoid log10(0) singularities. */
    const val MIN_DB = -120.0f

    /** Reference frequency for A-weighting normalization (0.00 dB). */
    const val A_WEIGHTING_REF_FREQ_HZ = 1000.0

    /** IEC 61672-1 Fast time-weighting time constant (tau = 125 ms). */
    const val TAU_FAST_SECONDS = 0.125

    /** IEC 61672-1 Slow time-weighting time constant (tau = 1000 ms). */
    const val TAU_SLOW_SECONDS = 1.000

    /** Default calibration reference level (standard acoustic calibrator 94.0 dB SPL). */
    const val DEFAULT_CALIBRATION_REF_SPL = 94.0f

    /** Supported FFT window sizes (must be powers of 2). */
    val SUPPORTED_FFT_SIZES = listOf(2048, 4096, 8192)

    /** Safe default tone generator digital amplitude (-20 dBFS = 0.1 amplitude). */
    const val DEFAULT_GENERATOR_AMPLITUDE_DBFS = -20.0f
    const val MAX_SAFE_GENERATOR_DBFS = 0.0f
    const val MIN_GENERATOR_DBFS = -60.0f
}

/**
 * Frequency weighting options specified by IEC 61672-1.
 */
enum class FrequencyWeighting(val label: String, val shortName: String, val description: String) {
    A("A-Weighting", "A", "Emulates human ear response across 20 Hz - 20 kHz (IEC 61672-1 Class 2)"),
    Z("Z-Weighting", "Z", "Zero/Flat frequency weighting (unfiltered PCM across capture bandwidth)")
}

/**
 * Exponential time-weighting options specified by IEC 61672-1.
 */
enum class TimeWeighting(val label: String, val shortName: String, val tauSeconds: Double) {
    FAST("Fast (125 ms)", "F", AudioConstants.TAU_FAST_SECONDS),
    SLOW("Slow (1.0 s)", "S", AudioConstants.TAU_SLOW_SECONDS)
}

/**
 * Measurement units displayed on UI and exported to data files.
 */
enum class MeasurementUnit(val symbol: String, val description: String) {
    DBFS("dBFS", "Decibels Relative to Full Scale (Digital PCM)"),
    DBSPL("dB SPL", "Estimated Sound Pressure Level (Calibrated)")
}

/**
 * Continuous waveforms and noise types synthesized by the tone generator.
 */
enum class WaveformType(val displayName: String) {
    SINE("Sine"),
    TRIANGLE("Triangle (Band-limited)"),
    SQUARE("Square (Anti-aliased)"),
    SAWTOOTH("Sawtooth (Anti-aliased)"),
    WHITE_NOISE("White Noise"),
    PINK_NOISE("Pink Noise (1/f)"),
    SWEEP("Log Sine Sweep")
}

/**
 * FFT smoothing filters.
 */
enum class SpectrumSmoothing(val displayName: String) {
    NONE("None"),
    FAST("Fast Decay (0.6)"),
    SLOW("Slow Decay (0.9)"),
    PEAK_HOLD("Peak Hold")
}
