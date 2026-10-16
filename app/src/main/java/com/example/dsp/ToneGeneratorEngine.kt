package com.example.dsp

import java.util.Random
import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin

/**
 * Real-time synthesis engine for waveforms and noise.
 * Produces 16-bit signed PCM audio buffers for playback via AudioTrack.
 */
class ToneGeneratorEngine(
    val sampleRate: Int = 48000
) {
    // Current parameters
    @Volatile var waveform: WaveformType = WaveformType.SINE
    @Volatile var targetFrequencyHz: Double = 440.0
    @Volatile var currentFrequencyHz: Double = 440.0
    @Volatile var amplitudeDbFS: Double = AudioConstants.DEFAULT_GENERATOR_AMPLITUDE_DBFS.toDouble()
    @Volatile var currentLinearGain: Double = 0.1 // -20 dBFS

    // Logarithmic sweep parameters
    @Volatile var sweepStartFreqHz: Double = 20.0
    @Volatile var sweepEndFreqHz: Double = 20000.0
    @Volatile var sweepDurationSeconds: Double = 5.0
    @Volatile var sweepProgress: Float = 0.0f
    private var sweepTimeElapsedSeconds: Double = 0.0

    // Synthesis phase accumulator [0.0, 1.0)
    private var phase: Double = 0.0

    // State for click-free start and stop ramps
    private var isPlaying: Boolean = false
    private var rampState: RampState = RampState.IDLE
    private var rampMultiplier: Double = 0.0
    private val rampSamples = (0.015 * sampleRate).toInt().coerceAtLeast(64) // 15 ms ramp

    private enum class RampState {
        IDLE, RAMPING_UP, PLAYING, RAMPING_DOWN
    }

    // Pseudo-random generator for white and pink noise
    private val rng = Random()

    // Paul Kellet's refined 3-pole pink noise filter bank state
    private var b0Pink = 0.0
    private var b1Pink = 0.0
    private var b2Pink = 0.0

    fun start() {
        synchronized(this) {
            isPlaying = true
            rampState = RampState.RAMPING_UP
            sweepTimeElapsedSeconds = 0.0
            sweepProgress = 0.0f
        }
    }

    fun stop() {
        synchronized(this) {
            if (isPlaying && rampState != RampState.RAMPING_DOWN) {
                rampState = RampState.RAMPING_DOWN
            }
        }
    }

    fun isStopped(): Boolean = rampState == RampState.IDLE

    fun setFrequency(freqHz: Double) {
        targetFrequencyHz = freqHz.coerceIn(10.0, sampleRate / 2.1)
    }

    fun setAmplitudeDb(dbFS: Double) {
        amplitudeDbFS = dbFS.coerceIn(
            AudioConstants.MIN_GENERATOR_DBFS.toDouble(),
            AudioConstants.MAX_SAFE_GENERATOR_DBFS.toDouble()
        )
    }

    fun configureSweep(startHz: Double, endHz: Double, durationSec: Double) {
        sweepStartFreqHz = startHz.coerceIn(10.0, sampleRate / 2.1)
        sweepEndFreqHz = endHz.coerceIn(10.0, sampleRate / 2.1)
        sweepDurationSeconds = durationSec.coerceIn(0.5, 60.0)
    }

    /**
     * Generates a chunk of 16-bit PCM audio samples into [outputBuffer].
     *
     * @return Number of valid 16-bit samples rendered, or 0 if stopped.
     */
    fun renderBlock(outputBuffer: ShortArray, count: Int): Int {
        if (rampState == RampState.IDLE) {
            return 0
        }

        val targetLinearGain = 10.0.pow(amplitudeDbFS / 20.0)
        val rampStep = 1.0 / rampSamples

        val isSweep = (waveform == WaveformType.SWEEP)
        val dt = 1.0 / sampleRate

        for (i in 0 until count) {
            // Slew-rate limiting for gain and frequency to prevent glitches
            currentLinearGain += (targetLinearGain - currentLinearGain) * 0.005
            if (!isSweep) {
                currentFrequencyHz += (targetFrequencyHz - currentFrequencyHz) * 0.01
            }

            // Handle start and stop ramps
            when (rampState) {
                RampState.RAMPING_UP -> {
                    rampMultiplier += rampStep
                    if (rampMultiplier >= 1.0) {
                        rampMultiplier = 1.0
                        rampState = RampState.PLAYING
                    }
                }
                RampState.RAMPING_DOWN -> {
                    rampMultiplier -= rampStep
                    if (rampMultiplier <= 0.0) {
                        rampMultiplier = 0.0
                        rampState = RampState.IDLE
                        isPlaying = false
                        // Zero remaining buffer and return
                        for (j in i until count) {
                            outputBuffer[j] = 0
                        }
                        return count
                    }
                }
                else -> {}
            }

            // Synthesize raw sample in range [-1.0, 1.0]
            val rawSample: Double
            if (isSweep) {
                rawSample = generateSweepSample(dt)
            } else {
                val phaseIncrement = currentFrequencyHz / sampleRate
                rawSample = generateWaveformSample(waveform, phase, phaseIncrement)
                phase = (phase + phaseIncrement) % 1.0
            }

            // Scale with smoothed gain, ramp multiplier, and convert to 16-bit signed PCM
            val finalSample = (rawSample * currentLinearGain * rampMultiplier).coerceIn(-1.0, 1.0)
            outputBuffer[i] = (finalSample * 32767.0).toInt().toShort()
        }

        return count
    }

    /**
     * Synthesizes instantaneous sample of periodic or noise waveform.
     */
    private fun generateWaveformSample(type: WaveformType, p: Double, dp: Double): Double {
        return when (type) {
            WaveformType.SINE -> sin(2.0 * PI * p)

            WaveformType.SQUARE -> {
                // Base square wave + PolyBLEP anti-aliasing
                var v = if (p < 0.5) 1.0 else -1.0
                v += polyBlep(p, dp)
                v -= polyBlep((p + 0.5) % 1.0, dp)
                v
            }

            WaveformType.SAWTOOTH -> {
                // Base naive saw: 2 * p - 1 + PolyBLEP anti-aliasing
                var v = 2.0 * p - 1.0
                v -= polyBlep(p, dp)
                v
            }

            WaveformType.TRIANGLE -> {
                // Integrated band-limited square wave for clean triangle:
                // Symmetrical ramp: 4 * |p - 0.5| - 1.0
                4.0 * kotlin.math.abs(p - 0.5) - 1.0
            }

            WaveformType.WHITE_NOISE -> {
                // Uniform pseudo-random in [-1.0, 1.0]
                rng.nextDouble() * 2.0 - 1.0
            }

            WaveformType.PINK_NOISE -> {
                // Paul Kellet's refined 3-pole 1/f noise filter
                val white = rng.nextDouble() * 2.0 - 1.0
                b0Pink = 0.99765 * b0Pink + white * 0.0990460
                b1Pink = 0.96300 * b1Pink + white * 0.2965164
                b2Pink = 0.57000 * b2Pink + white * 1.0526913
                val pink = b0Pink + b1Pink + b2Pink + white * 0.1848
                (pink * 0.18).coerceIn(-1.0, 1.0)
            }

            WaveformType.SWEEP -> 0.0 // Handled separately
        }
    }

    /**
     * PolyBLEP (Polynomial Band-Limited Step) algorithm to eliminate aliasing at waveform edges.
     */
    private fun polyBlep(t: Double, dt: Double): Double {
        if (dt <= 0.0) return 0.0
        var phase = t
        if (phase < dt) {
            phase /= dt
            return phase + phase - phase * phase - 1.0
        } else if (phase > 1.0 - dt) {
            phase = (phase - 1.0) / dt
            return phase * phase + phase + phase + 1.0
        }
        return 0.0
    }

    /**
     * Computes continuous logarithmic sine sweep sample.
     * f(t) = f0 * (f1/f0)^(t/T)
     * phase(t) = 2*PI * f0 * T / ln(f1/f0) * [ (f1/f0)^(t/T) - 1 ]
     */
    private fun generateSweepSample(dt: Double): Double {
        val f0 = sweepStartFreqHz
        val f1 = sweepEndFreqHz
        val tTotal = sweepDurationSeconds

        val t = sweepTimeElapsedSeconds
        val ratio = f1 / f0
        val lnRatio = ln(ratio)

        // Instantaneous frequency for display
        val currentF = f0 * ratio.pow(t / tTotal)
        currentFrequencyHz = currentF

        // Integrated continuous phase
        val instantaneousPhase = (2.0 * PI * f0 * tTotal / lnRatio) * (ratio.pow(t / tTotal) - 1.0)
        val s = sin(instantaneousPhase)

        sweepTimeElapsedSeconds += dt
        sweepProgress = (sweepTimeElapsedSeconds / tTotal).toFloat().coerceIn(0.0f, 1.0f)

        if (sweepTimeElapsedSeconds >= tTotal) {
            // Loop sweep or stop
            sweepTimeElapsedSeconds = 0.0
        }

        return s
    }
}
