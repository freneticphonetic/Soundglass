package com.example.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Digital approximation of the standard analog A-weighting response.
 * This filter and the phone are not certified sound level instruments.
 *
 * Implemented as a cascade of three 2nd-order IIR biquad sections (Direct Form II Transposed)
 * derived via the Bilinear Transform of the continuous s-domain transfer function:
 *
 *   H_A(s) = k_A * s^4 / [ (s + w1)^2 * (s + w2) * (s + w3) * (s + w4)^2 ]
 *
 * Where:
 *   f1 = 20.598997 Hz  -> w1 = 2 * PI * f1
 *   f2 = 107.65265 Hz  -> w2 = 2 * PI * f2
 *   f3 = 737.86223 Hz  -> w3 = 2 * PI * f3
 *   f4 = 12194.217 Hz  -> w4 = 2 * PI * f4
 *
 * Normalized so that |H_A(e^(j*2*PI*1000/fs))| = 1.0 (0.00 dB at 1 kHz).
 */
class AWeightingFilter(sampleRate: Int) {
    private val biquads: List<BiquadFilter>

    init {
        val fs = sampleRate.toDouble()
        val c = 2.0 * fs

        val w1 = 2.0 * PI * 20.598997
        val w2 = 2.0 * PI * 107.65265
        val w3 = 2.0 * PI * 737.86223
        val w4 = 2.0 * PI * 12194.217

        // Section 1: High-pass (w1, w1)
        val a0_1 = (c + w1) * (c + w1)
        val b0_1 = (c * c) / a0_1
        val b1_1 = (-2.0 * c * c) / a0_1
        val b2_1 = (c * c) / a0_1
        val a1_1 = (2.0 * (w1 * w1 - c * c)) / a0_1
        val a2_1 = ((c - w1) * (c - w1)) / a0_1

        // Section 2: Band-pass (w2, w3)
        val a0_2 = c * c + (w2 + w3) * c + w2 * w3
        val b0_2 = (c * c) / a0_2
        val b1_2 = (-2.0 * c * c) / a0_2
        val b2_2 = (c * c) / a0_2
        val a1_2 = (2.0 * (w2 * w3 - c * c)) / a0_2
        val a2_2 = (c * c - (w2 + w3) * c + w2 * w3) / a0_2

        // Section 3: Low-pass (w4, w4)
        val a0_3 = (c + w4) * (c + w4)
        val b0_3 = (w4 * w4) / a0_3
        val b1_3 = (2.0 * w4 * w4) / a0_3
        val b2_3 = (w4 * w4) / a0_3
        val a1_3 = (2.0 * (w4 * w4 - c * c)) / a0_3
        val a2_3 = ((c - w4) * (c - w4)) / a0_3

        // Calculate digital gain at 1000 Hz to ensure exact 0.00 dB reference
        val fRef = AudioConstants.A_WEIGHTING_REF_FREQ_HZ
        val wRef = 2.0 * PI * fRef / fs
        val g1 = evalBiquadMag(b0_1, b1_1, b2_1, a1_1, a2_1, wRef)
        val g2 = evalBiquadMag(b0_2, b1_2, b2_2, a1_2, a2_2, wRef)
        val g3 = evalBiquadMag(b0_3, b1_3, b2_3, a1_3, a2_3, wRef)
        val totalGainAt1k = g1 * g2 * g3

        // Distribute normalization factor into Section 1
        val norm = 1.0 / totalGainAt1k

        biquads = listOf(
            BiquadFilter(b0_1 * norm, b1_1 * norm, b2_1 * norm, a1_1, a2_1),
            BiquadFilter(b0_2, b1_2, b2_2, a1_2, a2_2),
            BiquadFilter(b0_3, b1_3, b2_3, a1_3, a2_3)
        )
    }

    /**
     * Filters a single input sample in place.
     */
    fun process(sample: Double): Double {
        var v = sample
        for (i in biquads.indices) {
            v = biquads[i].process(v)
        }
        return v
    }

    /**
     * Filters an array of float samples into the destination array.
     */
    fun process(input: FloatArray, output: FloatArray, count: Int = input.size) {
        for (i in 0 until count) {
            output[i] = process(input[i].toDouble()).toFloat()
        }
    }

    /**
     * Resets filter states to zero.
     */
    fun reset() {
        for (b in biquads) {
            b.reset()
        }
    }

    companion object {
        private fun evalBiquadMag(b0: Double, b1: Double, b2: Double, a1: Double, a2: Double, w: Double): Double {
            val cosW = cos(w)
            val sinW = sin(w)
            val cos2W = cos(2.0 * w)
            val sin2W = sin(2.0 * w)

            val numReal = b0 + b1 * cosW + b2 * cos2W
            val numImag = -b1 * sinW - b2 * sin2W
            val denReal = 1.0 + a1 * cosW + a2 * cos2W
            val denImag = -a1 * sinW - a2 * sin2W

            val numMag = sqrt(numReal * numReal + numImag * numImag)
            val denMag = sqrt(denReal * denReal + denImag * denImag)
            return numMag / denMag
        }
    }
}
