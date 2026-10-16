package com.example.dsp

import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin

/**
 * Real-time synthesis engine for band-limited periodic waveforms, filtered noise, and log sweeps.
 * Produces signed 16-bit PCM audio buffers for playback via AudioTrack.
 */
class ToneGeneratorEngine(
    val sampleRate: Int = 48000
) {
    // Current synthesis parameters
    @Volatile var waveform: WaveformType = WaveformType.SINE
    @Volatile var targetFrequencyHz: Double = 440.0
    @Volatile var currentFrequencyHz: Double = 440.0
    @Volatile var amplitudeDbFS: Double = AudioConstants.DEFAULT_GENERATOR_AMPLITUDE_DBFS.toDouble()
    @Volatile var currentLinearGain: Double = 0.1

    // Logarithmic sweep parameters
    @Volatile var sweepStartFreqHz: Double = 20.0
    @Volatile var sweepEndFreqHz: Double = 20000.0
    @Volatile var sweepDurationSeconds: Double = 5.0
    @Volatile var sweepRepeat: Boolean = false
    @Volatile var sweepProgress: Float = 0.0f
    private var sweepTimeElapsedSeconds: Double = 0.0

    private var renderedWaveform = WaveformType.SINE
    private var previousWaveform = WaveformType.SINE
    private var transitionRemaining = 0

    // Synthesis phase accumulator [0.0, 1.0)
    private var phase: Double = 0.0

    // Click-free start and stop ramps
    @Volatile var isPlaying: Boolean = false
        private set

    @Volatile private var rampState: RampState = RampState.IDLE
    private var rampMultiplier: Double = 0.0
    private val rampSamples = (0.015 * sampleRate).toInt().coerceAtLeast(64) // 15 ms ramp

    private enum class RampState {
        IDLE, RAMPING_UP, PLAYING, RAMPING_DOWN
    }

    // Pseudo-random generator for noise
    private val rng = Random()

    // Paul Kellet's refined 3-pole pink noise filter bank state
    private var b0Pink = 0.0
    private var b1Pink = 0.0
    private var b2Pink = 0.0

    fun start() {
        synchronized(this) {
            rampMultiplier = 0.0
            currentFrequencyHz = targetFrequencyHz
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
        require(freqHz.isFinite())
        targetFrequencyHz = freqHz.coerceIn(10.0, sampleRate / 2.1)
    }

    fun setAmplitudeDb(dbFS: Double) {
        require(dbFS.isFinite())
        amplitudeDbFS = dbFS.coerceIn(
            AudioConstants.MIN_GENERATOR_DBFS.toDouble(),
            AudioConstants.MAX_SAFE_GENERATOR_DBFS.toDouble()
        )
    }

    fun configureSweep(startHz: Double, endHz: Double, durationSec: Double, repeat: Boolean = false) {
        require(startHz.isFinite() && endHz.isFinite() && durationSec.isFinite())
        sweepStartFreqHz = startHz.coerceIn(10.0, sampleRate / 2.1)
        sweepEndFreqHz = endHz.coerceIn(10.0, sampleRate / 2.1)
        sweepDurationSeconds = durationSec.coerceIn(0.5, 60.0)
        sweepRepeat = repeat
    }

    /**
     * Generates a chunk of 16-bit PCM audio samples into [outputBuffer].
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
            // Slew-rate limiting for gain and frequency to avoid clicks
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
                        if (waveform == WaveformType.SWEEP) sweepProgress = 1.0f
                        for (j in i until count) {
                            outputBuffer[j] = 0
                        }
                        return count
                    }
                }
                else -> {}
            }

            val rawSample: Double
            if (isSweep) {
                rawSample = generateSweepSample(dt)
            } else {
                val phaseIncrement = currentFrequencyHz / sampleRate
                if (waveform != renderedWaveform) {
                    previousWaveform = renderedWaveform; renderedWaveform = waveform
                    transitionRemaining = rampSamples
                }
                val nextSample = generateWaveformSample(waveform, phase, phaseIncrement)
                rawSample = if (transitionRemaining > 0) {
                    val fraction = 1.0 - transitionRemaining.toDouble() / rampSamples
                    transitionRemaining--
                    generateWaveformSample(previousWaveform, phase, phaseIncrement) * (1 - fraction) + nextSample * fraction
                } else nextSample
                phase = (phase + phaseIncrement) % 1.0
            }

            val finalSample = (rawSample * currentLinearGain * rampMultiplier).coerceIn(-1.0, 1.0)
            outputBuffer[i] = (finalSample * 32767.0).toInt().toShort()
        }

        return count
    }

    /**
     * Synthesizes instantaneous sample with PolyBLEP / PolyBLAMP anti-aliasing.
     */
    fun generateWaveformSample(type: WaveformType, p: Double, dp: Double): Double {
        return when (type) {
            WaveformType.SINE -> sin(2.0 * PI * p)

            WaveformType.SQUARE -> {
                // Square wave with PolyBLEP band-limiting at both edges
                var v = if (p < 0.5) 1.0 else -1.0
                v += polyBlep(p, dp)
                v -= polyBlep((p + 0.5) % 1.0, dp)
                v.coerceIn(-1.0, 1.0)
            }

            WaveformType.SAWTOOTH -> {
                // Sawtooth wave with PolyBLEP step correction at wrap-around
                var v = 2.0 * p - 1.0
                v -= polyBlep(p, dp)
                v.coerceIn(-1.0, 1.0)
            }

            WaveformType.TRIANGLE -> {
                // Triangle with PolyBLAMP correction to reduce aliasing (Polynomial Band-Limited Ramp)
                // Naive triangle with range [-1.0, 1.0]: slope = +4 for p in [0, 0.5), -4 for p in [0.5, 1.0)
                var v = if (p < 0.5) 4.0 * p - 1.0 else 3.0 - 4.0 * p
                // Derivative has step of -8 at p = 0.5 and step of +8 at p = 0.0 / 1.0
                // Correct slope discontinuities with PolyBLAMP:
                v += 4.0 * polyBlamp(p, dp)
                v -= 4.0 * polyBlamp((p + 0.5) % 1.0, dp)
                v.coerceIn(-1.0, 1.0)
            }

            WaveformType.WHITE_NOISE -> {
                rng.nextDouble() * 2.0 - 1.0
            }

            WaveformType.PINK_NOISE -> {
                val white = rng.nextDouble() * 2.0 - 1.0
                b0Pink = 0.99765 * b0Pink + white * 0.0990460
                b1Pink = 0.96300 * b1Pink + white * 0.2965164
                b2Pink = 0.57000 * b2Pink + white * 1.0526913
                val pink = b0Pink + b1Pink + b2Pink + white * 0.1848
                (pink * 0.18).coerceIn(-1.0, 1.0)
            }

            WaveformType.SWEEP -> 0.0
        }
    }

    /**
     * PolyBLEP residual function for a unit step discontinuity at t = 0.
     */
    private fun polyBlep(t: Double, dt: Double): Double {
        if (dt <= 0.0) return 0.0
        if (t < dt) {
            val u = t / dt
            return 2.0 * u - u * u - 1.0
        } else if (t > 1.0 - dt) {
            val u = (t - 1.0) / dt
            return u * u + 2.0 * u + 1.0
        }
        return 0.0
    }

    /**
     * PolyBLAMP residual function for a unit slope discontinuity at t = 0.
     */
    private fun polyBlamp(t: Double, dt: Double): Double {
        if (dt <= 0.0) return 0.0
        if (t < dt) {
            val u = t / dt
            return dt * (1.0 - u).pow(3.0) / 3.0
        } else if (t > 1.0 - dt) {
            val u = (t - 1.0) / dt
            return dt * (1.0 + u).pow(3.0) / 3.0
        }
        return 0.0
    }

    /**
     * Computes logarithmic sine sweep sample without division by zero.
     */
    fun generateSweepSample(dt: Double): Double {
        val f0 = sweepStartFreqHz
        val f1 = sweepEndFreqHz
        val tTotal = sweepDurationSeconds

        val t = sweepTimeElapsedSeconds.coerceAtMost(tTotal)

        val ratio = f1 / f0
        currentFrequencyHz = if (abs(f1 - f0) < 0.1) f0 else f0 * ratio.pow(t / tTotal)
        val s = sin(2.0 * PI * phase)
        phase = (phase + currentFrequencyHz / sampleRate) % 1.0

        sweepTimeElapsedSeconds += dt
        sweepProgress = (sweepTimeElapsedSeconds / tTotal).toFloat().coerceIn(0.0f, 1.0f)

        if (sweepTimeElapsedSeconds >= tTotal - if (sweepRepeat) 0.0 else rampSamples.toDouble() / sampleRate) {
            if (sweepRepeat) {
                sweepTimeElapsedSeconds = 0.0
            } else {
                // Single-shot sweep completes at selected duration and initiates ramp-down
                stop()
            }
        }

        return s
    }
}
