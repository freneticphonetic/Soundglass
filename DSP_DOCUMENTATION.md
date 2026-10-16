# Soundglass DSP Documentation

Soundglass is an open-source native Android acoustic instrumentation application built for precision sound level metering, real-time frequency analysis, calibrated acoustic measurement, and anti-aliased audio synthesis.

This document details the mathematical formulations, signal processing pipeline, digital filter designs, and physical measurement limits implemented in the application.

---

## 1. Sound Level Metering & Weighting

### 1.1 PCM Representation & Dynamic Range
Audio input is captured as 16-bit signed pulse-code modulation (PCM) via `AudioRecord`, normalized in memory to double/float precision values in the range $x \in [-1.0, +1.0]$:
$$x_i = \frac{\text{raw}_{16}}{32768.0}$$

The digital full scale reference magnitude corresponds to peak value $1.0$ ($32768$ in 16-bit signed integer).
- Full scale reference: $0\text{ dBFS}$
- Decibel floor clamp: $-120\text{ dBFS}$

### 1.2 RMS Level (dBFS)
For a block of $N$ samples:
$$\text{RMS} = \sqrt{\frac{1}{N}\sum_{i=0}^{N-1} x_i^2}$$
$$L_{\text{RMS}}(\text{dBFS}) = 20 \log_{10}\left(\max(\text{RMS}, 10^{-6})\right)$$
For a full-scale pure sine wave of peak amplitude $1.0$, the theoretical RMS is $1/\sqrt{2} \approx 0.7071$, yielding $-3.01\text{ dBFS}$.

### 1.3 Sample Peak (dBFS)
$$\text{Peak} = \max_{0 \le i < N} |x_i|$$
$$L_{\text{Peak}}(\text{dBFS}) = 20 \log_{10}\left(\max(\text{Peak}, 10^{-6})\right)$$
Explicitly distinguished from RMS or time-weighted sound levels.

### 1.4 Exponential Time Weighting (IEC 61672-1)
Sound level meters apply exponential time weighting to the instantaneous mean-square power $P = x_i^2$. For block processing with block duration $\Delta t = N / f_s$:
$$\alpha = 1 - e^{-\Delta t / \tau}$$
$$P_{\text{weighted}}[k] = (1 - \alpha) P_{\text{weighted}}[k-1] + \alpha \cdot \left(\frac{1}{N}\sum_{i=0}^{N-1} x_i^2\right)$$
$$L_{\tau}(\text{dBFS}) = 10 \log_{10}(P_{\text{weighted}}[k])$$

Standard time constants:
- **Fast Weighting ($F$):** $\tau = 125\text{ ms} = 0.125\text{ s}$
- **Slow Weighting ($S$):** $\tau = 1000\text{ ms} = 1.000\text{ s}$

### 1.5 Energy-Equivalent Continuous Sound Level ($L_{\text{eq}}$)
$L_{\text{eq}}$ represents the steady level having the same acoustic energy as the fluctuating signal over total active duration $T$:
$$L_{\text{eq}} = 10 \log_{10}\left( \frac{1}{T} \int_0^T \frac{p^2(t)}{p_0^2} dt \right) = 10 \log_{10}\left( \frac{\sum (P_{\text{meanSquare}} \cdot \Delta t)}{\sum \Delta t} \right)$$
$L_{\text{eq}}$ is integrated strictly in the linear energy domain ($\text{sample}^2$) without logarithmic distortion.

### 1.6 Minimum ($L_{\min}$) and Maximum ($L_{\max}$)
$L_{\min}$ and $L_{\max}$ track the minimum and maximum values of the selected time-weighted level ($L_F$ or $L_S$, with current frequency weighting $A$ or $Z$) recorded during the session.

---

## 2. Frequency Weighting Filters

### 2.1 A-Weighting (IEC 61672-1:2013)
The continuous analog A-weighting transfer function is:
$$H_A(s) = \frac{k_A \cdot s^4}{(s + \omega_1)^2 (s + \omega_2) (s + \omega_3) (s + \omega_4)^2}$$
where corner frequencies are:
- $f_1 = 20.598997\text{ Hz} \implies \omega_1 = 2\pi f_1$
- $f_2 = 107.65265\text{ Hz} \implies \omega_2 = 2\pi f_2$
- $f_3 = 737.86223\text{ Hz} \implies \omega_3 = 2\pi f_3$
- $f_4 = 12194.217\text{ Hz} \implies \omega_4 = 2\pi f_4$

Discretization uses the Bilinear Transform $s = 2f_s \frac{1 - z^{-1}}{1 + z^{-1}}$ prewarped at $1000\text{ Hz}$. It is factored into three Direct Form II Transposed biquad sections:
1. High-pass section: $(\omega_1, \omega_1)$
2. Band-pass section: $(\omega_2, \omega_3)$
3. Low-pass section: $(\omega_4, \omega_4)$

Digital normalization guarantees that $|H_A(e^{j 2\pi 1000 / f_s})| \equiv 1.000$ ($0.00\text{ dB}$ at $1\text{ kHz}$).

### 2.2 Z-Weighting
Zero frequency weighting (flat unweighted response across the full Nyquist capture bandwidth $[0, f_s / 2]$).

---

## 3. Calibration Mathematics & Physical Limitations

### 3.1 Offset Computation
Before calibration, Soundglass displays uncalibrated readings in native **dBFS**.
When the user connects a reference acoustic calibrator (e.g., standard $94.0\text{ dB SPL}$ at $1\text{ kHz}$) or compares against a certified reference sound level meter, the calibration routine integrates steady input over a 3-second window:
$$\text{offsetDb} = L_{\text{reference}} - L_{\text{measured\_dBFS}}$$
$$\text{Estimated dB SPL} = \text{dBFS} + \text{offsetDb}$$

### 3.2 Physical Microphone Limits
1. **MEMS Transducer Self-Noise:** Smartphone microphones generally have an equivalent self-noise floor of $30 - 35\text{ dBA}$. Readings below this floor represent electronic noise rather than acoustic silence.
2. **Acoustic Overload Point (AOP):** Smartphone microphone diaphragms saturate and clip between $115\text{ dB SPL}$ and $125\text{ dB SPL}$. When clipping occurs, Soundglass triggers visual warnings.
3. **Frequency Response:** A scalar sensitivity offset calibrates broadband sensitivity at the reference frequency ($1\text{ kHz}$), but does not alter physical diaphragm resonance or phone chassis acoustic diffraction.

---

## 4. Frequency Analysis & FFT

### 4.1 Windowing & Coherent Gain
A Radix-2 Cooley-Tukey FFT is computed over $N \in \{2048, 4096, 8192\}$ samples using a Hann window:
$$w[n] = 0.5 \left(1 - \cos\left(\frac{2\pi n}{N - 1}\right)\right)$$
Coherent gain for single-tone amplitude:
$$G_c = \frac{1}{N}\sum_{n=0}^{N-1} w[n] = 0.5$$
Normalized amplitude spectrum:
$$|X_k| = \frac{2 \cdot |Y_k|}{N \cdot G_c} = \frac{4 \cdot |Y_k|}{N}$$
$$\text{Magnitude}(\text{dBFS}) = 20 \log_{10}(|X_k|)$$

### 4.2 Dominant Frequency & Parabolic Interpolation
DC bins ($< 20\text{ Hz}$) are rejected. If the dominant peak exceeds $-70\text{ dBFS}$ and stands $\ge 5\text{ dB}$ above the local floor, parabolic interpolation is applied to the three bins centered at peak $k$:
$$\delta = \frac{0.5 (y_{k+1} - y_{k-1})}{2 y_k - y_{k-1} - y_{k+1}}$$
$$f_{\text{interpolated}} = (k + \delta) \frac{f_s}{N}$$

---

## 5. Tone & Noise Generation

### 5.1 PolyBLEP Anti-Aliasing
Discontinuities in square and sawtooth waves create Nyquist-folding aliasing. Soundglass uses Polynomial Band-Limited Step (PolyBLEP):
$$\text{polyBlep}(t, dt) = \begin{cases}
\frac{2t}{dt} - \frac{t^2}{dt^2} - 1.0 & 0 \le t < dt \\
\frac{(t - 1)^2}{dt^2} + \frac{2(t - 1)}{dt} + 1.0 & 1 - dt < t \le 1 \\
0.0 & \text{otherwise}
\end{cases}$$

### 5.2 Pink Noise (1/f)
Synthesized via Paul Kellet's refined 3-pole filter driven by white noise:
$$b_0 = 0.99765 b_0 + 0.0990460 \cdot w$$
$$b_1 = 0.96300 b_1 + 0.2965164 \cdot w$$
$$b_2 = 0.57000 b_2 + 1.0526913 \cdot w$$
$$\text{pink} = b_0 + b_1 + b_2 + 0.1848 \cdot w$$

### 5.3 Continuous Logarithmic Sweep
$$f(t) = f_0 \left(\frac{f_1}{f_0}\right)^{t / T}$$
$$\phi(t) = 2\pi \frac{f_0 T}{\ln(f_1 / f_0)} \left( \left(\frac{f_1}{f_0}\right)^{t / T} - 1 \right)$$
$$s(t) = \sin(\phi(t))$$
All waveform transitions and stop events apply 15 ms linear ramps to prevent acoustic transients.
