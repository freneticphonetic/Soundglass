package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CyanAccentDark
import com.example.ui.theme.PurplePrimaryDark

@Composable
fun DspDocsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("dsp_docs_screen")
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "DSP Reference & Formulas",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 1. Level Measurements Section
        DspDocCard(
            title = "1. SOUND LEVEL MEASUREMENT",
            formula = "RMS(dBFS) = 20 * log10( sqrt( (1/N) * sum(x_i^2) ) )\nPeak(dBFS) = 20 * log10( max(|x_i|) )\nL_eq = 10 * log10( (1/T) * sum( P_inst * dt ) )",
            description = "• Full Scale (0 dBFS) corresponds to 16-bit PCM amplitude +/- 32768.0.\n• Exponential time-weighting uses alpha = 1 - exp(-dt / tau), where tau = 125 ms for Fast and 1000 ms for Slow.\n• Equivalent continuous sound level (L_eq) integrates energy strictly in the linear pressure-squared domain, avoiding logarithmic bias."
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 2. Frequency Weighting Section
        DspDocCard(
            title = "2. A-WEIGHTING DIGITAL FILTER (IEC 61672-1)",
            formula = "H_A(s) = k_A * s^4 / [ (s+w1)^2 * (s+w2) * (s+w3) * (s+w4)^2 ]\nw1 = 2*pi*20.6, w2 = 2*pi*107.7, w3 = 2*pi*737.9, w4 = 2*pi*12194.2",
            description = "• Implemented as a cascade of 3 second-order IIR biquad sections (Direct Form II Transposed).\n• Discretized via the Bilinear Transform with frequency prewarping at 1000 Hz.\n• Normalized such that digital filter gain at 1000 Hz is exactly 1.000 (0.00 dB)."
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 3. FFT & Pitch Estimation
        DspDocCard(
            title = "3. FFT ANALYSIS & PARABOLIC PEAK INTERPOLATION",
            formula = "w[n] = 0.5 * (1 - cos(2*pi*n / (N-1)))  [Hann window]\nCoherent Gain G_c = 0.5 -> Mag = 4.0 * |X_k| / N\ndelta = 0.5 * (y_{k+1} - y_{k-1}) / (2*y_k - y_{k-1} - y_{k+1})\nf_est = (k + delta) * (f_s / N)",
            description = "• Single-tone peak amplitude is calibrated against coherent window gain.\n• Dominant frequency estimation ignores DC bins (< 20 Hz).\n• Quadratic interpolation on spectral bins provides sub-bin frequency accuracy.\n• Pitch detection converts Hz to MIDI notes and tuning deviation in cents: cents = round( (m - round(m)) * 100 )."
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 4. Tone Generator & Anti-aliasing
        DspDocCard(
            title = "4. TONE SYNTHESIS & POLYBLEP ANTI-ALIASING",
            formula = "f(t) = f0 * (f1/f0)^(t/T)  [Log sweep]\nphase(t) = 2*pi * f0*T / ln(f1/f0) * [ (f1/f0)^(t/T) - 1 ]\nPolyBLEP: v += polyBlep(p, dp) at discontinuities",
            description = "• Square and Sawtooth waveforms use Polynomial Band-Limited Step (PolyBLEP) to suppress Nyquist mirror aliasing.\n• Pink noise is synthesized via Paul Kellet's refined 3-pole 1/f filter bank.\n• 15 ms linear ramps prevent audible transient clicks on start and stop."
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 5. Calibration & Acoustic Limitations
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = CyanAccentDark)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "5. CALIBRATION & HARDWARE LIMITATIONS",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = CyanAccentDark
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "• Calibration Offset: offsetDb = targetRefSpl - measured_dBFS.\n• Calibrated level: dB SPL = dBFS + offsetDb.\n• Microphone limitations: Smartphone MEMS microphones typically exhibit a self-noise floor around 30 to 35 dBA and acoustic clipping distortion above 115 to 125 dB SPL.\n• A scalar offset calibrates broadband sensitivity at the reference frequency, but does not alter the physical transducer's frequency response curve.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun DspDocCard(title: String, formula: String, description: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = PurplePrimaryDark
            )

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF100E17),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = formula,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = CyanAccentDark,
                    modifier = Modifier.padding(10.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
