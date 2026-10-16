package com.example.dsp

/**
 * Direct Form II Transposed second-order IIR (biquad) section.
 * Provides numerical stability and low quantization noise for floating-point audio.
 *
 * Transfer function:
 * H(z) = (b0 + b1*z^-1 + b2*z^-2) / (1 + a1*z^-1 + a2*z^-2)
 */
class BiquadFilter(
    val b0: Double,
    val b1: Double,
    val b2: Double,
    val a1: Double,
    val a2: Double
) {
    private var s1: Double = 0.0
    private var s2: Double = 0.0

    /**
     * Processes a single input sample through this biquad filter.
     */
    fun process(x: Double): Double {
        val y = b0 * x + s1
        s1 = b1 * x - a1 * y + s2
        s2 = b2 * x - a2 * y
        return y
    }

    /**
     * Resets filter delay line states to zero.
     */
    fun reset() {
        s1 = 0.0
        s2 = 0.0
    }
}
