package com.example.dsp

import kotlin.math.*

object SpectrumProjection {
    fun logBins(frame: FftAnalysisFrame, count: Int): FloatArray {
        val low = 20.0
        val high = minOf(20000.0, frame.sampleRate / 2.0)
        val width = frame.sampleRate.toDouble() / frame.fftSize
        return FloatArray(count) { i ->
            val a = low * (high / low).pow(i.toDouble() / count)
            val b = low * (high / low).pow((i + 1.0) / count)
            val first = ceil(a / width).toInt().coerceIn(1, frame.magnitudesDbFS.lastIndex)
            val last = floor(b / width).toInt().coerceIn(1, frame.magnitudesDbFS.lastIndex)
            if (last >= first) (first..last).maxOf { frame.magnitudesDbFS[it] }
            else frame.magnitudesDbFS[((sqrt(a * b) / width).roundToInt()).coerceIn(1, frame.magnitudesDbFS.lastIndex)]
        }
    }
}
