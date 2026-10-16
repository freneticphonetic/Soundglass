package com.example.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.log2
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Frequency analysis result frame.
 */
data class FftAnalysisFrame(
    val frequencies: FloatArray,    // Center frequencies for each bin (Hz)
    val magnitudesDbFS: FloatArray, // Normalized magnitude in dBFS
    val peakFrequencyHz: Float?,    // Dominant peak frequency in Hz, or null if no stable tone
    val peakMagnitudeDbFS: Float?,  // Dominant peak magnitude in dBFS
    val musicalNote: String?,       // Musical note name (e.g., "A4"), or null
    val centsOffset: Int?,          // Tuning offset in cents (-50 to +50), or null
    val sampleRate: Int,
    val fftSize: Int
)

/**
 * Musical pitch utilities converting frequency (Hz) to scientific pitch notation and cents.
 */
object PitchDetector {
    private val NOTE_NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    fun frequencyToPitch(frequencyHz: Float): Pair<String, Int>? {
        if (frequencyHz < 20.0f || frequencyHz > 20000.0f) return null

        val midiFloat = 69.0 + 12.0 * log2(frequencyHz.toDouble() / 440.0)
        val midiRound = midiFloat.roundToInt()
        val cents = ((midiFloat - midiRound) * 100.0).roundToInt()

        val noteIndex = (midiRound % 12 + 12) % 12
        val octave = (midiRound / 12) - 1
        val noteName = "${NOTE_NAMES[noteIndex]}$octave"

        return Pair(noteName, cents)
    }
}

/**
 * High-performance Radix-2 Cooley-Tukey FFT implementation in pure Kotlin.
 * Windowed with Hann window and calibrated for coherent gain.
 */
class FftAnalyzer(
    val fftSize: Int = 4096,
    val sampleRate: Int = 48000
) {
    init {
        require(fftSize > 0 && (fftSize and (fftSize - 1)) == 0) {
            "FFT size must be a power of 2, received $fftSize"
        }
    }

    private val numBins = fftSize / 2
    val binWidthHz = sampleRate.toFloat() / fftSize

    // Precomputed center frequencies
    val binFrequencies = FloatArray(numBins) { i -> i * binWidthHz }

    // Precomputed Hann window: w[n] = 0.5 * (1 - cos(2*pi*n / (N-1)))
    private val hannWindow = FloatArray(fftSize) { n ->
        (0.5 * (1.0 - cos(2.0 * PI * n / (fftSize - 1)))).toFloat()
    }

    // Coherent window gain for single-tone amplitude calibration
    // G = (1/N) * sum(w[n]) = 0.5 for Hann window
    private val coherentGain = 0.5f
    // Scale factor to normalize one-sided FFT amplitude to peak sine full scale:
    // A_k = (2 * |X_k|) / (N * G) = (4 * |X_k|) / N
    private val normalizationScale = 4.0f / fftSize

    // Working buffers to avoid allocations in critical audio path
    private val real = FloatArray(fftSize)
    private val imag = FloatArray(fftSize)
    private val smoothedMagnitudes = FloatArray(numBins) { AudioConstants.MIN_DB }
    private val peakHoldMagnitudes = FloatArray(numBins) { AudioConstants.MIN_DB }

    // Bit-reversal table
    private val bitRev = IntArray(fftSize) { i ->
        var rev = 0
        var temp = i
        var bits = 0
        var n = fftSize
        while (n > 1) {
            bits++
            n = n shr 1
        }
        for (b in 0 until bits) {
            rev = (rev shl 1) or (temp and 1)
            temp = temp shr 1
        }
        rev
    }

    // Precomputed twiddle factors
    private val cosTwiddle = FloatArray(fftSize / 2)
    private val sinTwiddle = FloatArray(fftSize / 2)

    init {
        for (i in 0 until fftSize / 2) {
            val angle = -2.0 * PI * i / fftSize
            cosTwiddle[i] = cos(angle).toFloat()
            sinTwiddle[i] = sin(angle).toFloat()
        }
    }

    /**
     * Executes FFT on the input PCM buffer and returns a complete analysis frame.
     *
     * @param inputSamples Audio samples, at least fftSize length
     * @param smoothing Smoothing mode to apply across frames
     */
    fun process(
        inputSamples: FloatArray,
        smoothing: SpectrumSmoothing = SpectrumSmoothing.NONE
    ): FftAnalysisFrame {
        val n = fftSize
        // 1. Apply Hann window and bit-reversal reordering
        for (i in 0 until n) {
            val target = bitRev[i]
            val sample = if (i < inputSamples.size) inputSamples[i] else 0.0f
            real[target] = sample * hannWindow[i]
            imag[target] = 0.0f
        }

        // 2. In-place Cooley-Tukey Radix-2 FFT
        var len = 2
        while (len <= n) {
            val halfLen = len / 2
            val step = n / len
            var i = 0
            while (i < n) {
                var k = 0
                for (j in 0 until halfLen) {
                    val twiddleIndex = j * step
                    val c = cosTwiddle[twiddleIndex]
                    val s = sinTwiddle[twiddleIndex]

                    val uReal = real[i + j]
                    val uImag = imag[i + j]

                    val vReal = real[i + j + halfLen] * c - imag[i + j + halfLen] * s
                    val vImag = real[i + j + halfLen] * s + imag[i + j + halfLen] * c

                    real[i + j] = uReal + vReal
                    imag[i + j] = uImag + vImag

                    real[i + j + halfLen] = uReal - vReal
                    imag[i + j + halfLen] = uImag - vImag
                }
                i += len
            }
            len = len shl 1
        }

        // 3. Compute normalized one-sided magnitude in dBFS
        val rawMagnitudesDbFS = FloatArray(numBins)
        val minBinIndex = (20.0f / binWidthHz).toInt().coerceAtLeast(1) // Ignore DC (< 20 Hz)
        var maxMag = -1e9f
        var peakBin = -1

        for (k in 0 until numBins) {
            val r = real[k]
            val im = imag[k]
            val mag = sqrt(r * r + im * im) * normalizationScale
            val db = if (mag > 1e-6f) {
                (20.0f * log10(mag)).coerceIn(AudioConstants.MIN_DB, 6.0f)
            } else {
                AudioConstants.MIN_DB
            }
            rawMagnitudesDbFS[k] = db

            if (k >= minBinIndex && db > maxMag) {
                maxMag = db
                peakBin = k
            }
        }

        // 4. Apply selected spectrum smoothing
        val outputMagnitudes = FloatArray(numBins)
        when (smoothing) {
            SpectrumSmoothing.NONE -> {
                System.arraycopy(rawMagnitudesDbFS, 0, outputMagnitudes, 0, numBins)
                System.arraycopy(rawMagnitudesDbFS, 0, smoothedMagnitudes, 0, numBins)
                System.arraycopy(rawMagnitudesDbFS, 0, peakHoldMagnitudes, 0, numBins)
            }
            SpectrumSmoothing.FAST -> {
                val alpha = 0.6f
                for (k in 0 until numBins) {
                    smoothedMagnitudes[k] = alpha * rawMagnitudesDbFS[k] + (1.0f - alpha) * smoothedMagnitudes[k]
                    outputMagnitudes[k] = smoothedMagnitudes[k]
                }
            }
            SpectrumSmoothing.SLOW -> {
                val alpha = 0.2f
                for (k in 0 until numBins) {
                    smoothedMagnitudes[k] = alpha * rawMagnitudesDbFS[k] + (1.0f - alpha) * smoothedMagnitudes[k]
                    outputMagnitudes[k] = smoothedMagnitudes[k]
                }
            }
            SpectrumSmoothing.PEAK_HOLD -> {
                val decay = 0.05f // Slow decay of peak hold in dB per frame
                for (k in 0 until numBins) {
                    val raw = rawMagnitudesDbFS[k]
                    if (raw > peakHoldMagnitudes[k]) {
                        peakHoldMagnitudes[k] = raw
                    } else {
                        peakHoldMagnitudes[k] = (peakHoldMagnitudes[k] - decay).coerceAtLeast(AudioConstants.MIN_DB)
                    }
                    outputMagnitudes[k] = peakHoldMagnitudes[k]
                }
            }
        }

        // 5. Tonality check & Parabolic Peak Interpolation
        var peakFreqHz: Float? = null
        var peakDbFS: Float? = null
        var note: String? = null
        var cents: Int? = null

        // Require minimum peak level (-70 dBFS) and prominence over local floor (> 5 dB)
        if (peakBin in (minBinIndex + 1) until (numBins - 1) && maxMag > -70.0f) {
            val yPrev = rawMagnitudesDbFS[peakBin - 1]
            val yCurr = rawMagnitudesDbFS[peakBin]
            val yNext = rawMagnitudesDbFS[peakBin + 1]

            // Calculate surrounding local noise floor average (5 bins to left and right)
            var floorSum = 0.0f
            var floorCount = 0
            for (offset in -6..6) {
                if (kotlin.math.abs(offset) > 2) {
                    val b = peakBin + offset
                    if (b in 0 until numBins) {
                        floorSum += rawMagnitudesDbFS[b]
                        floorCount++
                    }
                }
            }
            val localFloor = if (floorCount > 0) floorSum / floorCount else -90.0f

            // Prominence check: Peak must stand out above local spectral floor
            if (yCurr - localFloor >= 5.0f && (2.0f * yCurr - yPrev - yNext) > 1e-4f) {
                // Parabolic interpolation on decibel values:
                // delta = 0.5 * (yNext - yPrev) / (2 * yCurr - yPrev - yNext)
                val denom = 2.0f * yCurr - yPrev - yNext
                val delta = (0.5f * (yNext - yPrev)) / denom
                val interpolatedBin = peakBin + delta.coerceIn(-0.5f, 0.5f)

                val estimatedFreq = interpolatedBin * binWidthHz
                val estimatedMag = yCurr - 0.25f * (yPrev - yNext) * delta

                peakFreqHz = estimatedFreq
                peakDbFS = estimatedMag

                val pitchInfo = PitchDetector.frequencyToPitch(estimatedFreq)
                if (pitchInfo != null) {
                    note = pitchInfo.first
                    cents = pitchInfo.second
                }
            }
        }

        return FftAnalysisFrame(
            frequencies = binFrequencies,
            magnitudesDbFS = outputMagnitudes,
            peakFrequencyHz = peakFreqHz,
            peakMagnitudeDbFS = peakDbFS,
            musicalNote = note,
            centsOffset = cents,
            sampleRate = sampleRate,
            fftSize = fftSize
        )
    }

    /**
     * Resets smoothing and peak hold buffers.
     */
    fun reset() {
        for (i in 0 until numBins) {
            smoothedMagnitudes[i] = AudioConstants.MIN_DB
            peakHoldMagnitudes[i] = AudioConstants.MIN_DB
        }
    }
}
