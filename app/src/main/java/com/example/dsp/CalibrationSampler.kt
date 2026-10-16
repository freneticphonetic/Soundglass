package com.example.dsp

import kotlin.math.log10
import kotlin.math.sqrt

/** Bounded, sample-counted reference measurement; no retained PCM or per-block list. */
class CalibrationSampler(private val sampleRate: Int, durationSeconds: Double = 3.0) {
    private val targetSamples = (durationSeconds * sampleRate).toLong()
    var samples: Long = 0
        private set
    private var energy = 0.0
    private var blocks = 0L
    private var blockMean = 0.0
    private var blockM2 = 0.0
    val progress: Float get() = (samples.toDouble() / targetSamples).toFloat().coerceIn(0f, 1f)
    val complete: Boolean get() = samples >= targetSamples

    fun add(input: FloatArray, count: Int) {
        val n = minOf(count.toLong(), targetSamples - samples).toInt()
        if (n <= 0) return
        var sum = 0.0
        for (i in 0 until n) {
            val v = input[i].toDouble()
            sum += v * v
        }
        energy += sum
        samples += n
        val power = sum / n
        blocks++
        val delta = power - blockMean
        blockMean += delta / blocks
        blockM2 += delta * (power - blockMean)
    }

    fun offset(referenceSpl: Float): Float {
        require(referenceSpl.isFinite() && referenceSpl in 20f..140f) { "Enter a reference level between 20 and 140 dB SPL." }
        check(complete) { "Calibration interrupted before three seconds of audio were captured." }
        val mean = energy / samples
        check(mean >= 1e-8) { "Reference signal is too quiet." }
        check(blocks > 4 && sqrt(blockM2 / blocks) / blockMean <= 0.40) { "Reference signal was unstable. Keep the sound steady and try again." }
        return referenceSpl - (10.0 * log10(mean)).toFloat()
    }
}
