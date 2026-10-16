# Soundglass DSP and measurement conventions

## Input and level units

Input is mono signed 16-bit PCM, normalized by 32768. The engine negotiates 48 kHz then 44.1 kHz and tries the reported unprocessed source before recognition/microphone fallbacks. A requested unprocessed source is reported as a request, not proof of the full hardware response. AGC, noise suppression and echo cancellation are disabled where their controls permit it, with effect handles retained until capture closes. Diagnostics distinguish availability from successful disablement.

Block RMS is `20 log10(sqrt(sum(x*x)/N))`. Sample peak is `20 log10(max(abs(x)))`; it is not a reconstructed true peak. Under A weighting, the block RMS is calculated after the weighting filter. Sample peak/clipping are detected on raw input. The finite display floor is -120 dBFS. A full-scale sine has approximately -3.0103 dBFS RMS and 0 dBFS sample peak.

## Time weighting and equivalent energy

For every frequency-weighted sample, exponential mean-square power updates as `P += alpha*(x*x-P)`, with `alpha = 1-exp(-1/(sampleRate*tau))`. Fast tau is 0.125 seconds; Slow tau is 1 second. This response is independent of AudioRecord block partitioning. Display updates are throttled separately. Session minimum/maximum refer to time-weighted values at processed block endpoints; they are not an acoustic peak detector.

Equivalent level accumulates linear squared signal energy and divides by the total active sample count before converting to dB. Paused time contributes no samples. Duration derives from the same sample count. Chart decimation does not affect whole-session energy. Saved sample peak is the maximum raw sample peak over the session.

## A weighting

Three biquads, using transposed direct-form II, approximate the conventional analog A-weighting transfer function via bilinear transformation. Analog pole frequencies are 20.598997, 107.65265, 737.86223 and 12194.217 Hz, with the first/last repeated. The numerator has four zeros at DC. Coefficients are recalculated for the capture sample rate and normalized at digital 1 kHz.

This is a digital approximation, not a claim of IEC instrument certification. At 48 kHz, calculated responses include approximately -19.15 dB at 100 Hz, 0 dB at 1 kHz, +0.93 dB at 4 kHz and -1.69 dB at 8 kHz. High-frequency bilinear warping and the microphone's actual response remain relevant. Z weighting bypasses the filter.

## Calibration

Capture exactly three seconds of valid audio samples. Accumulate signal energy in the active frequency weighting, then compute `offset = referenceSPL - 10log10(meanPower)`. Check raw clipping separately. Bounded online block-power variance rejects unstable reference signals; near-silence and incomplete/interrupted capture fail. No raw audio or per-block unbounded calibration list is retained.

Profiles must match the actual Android routed device fingerprint, sample rate, source, channel/encoding and A/Z setting. Unknown routes invalidate calibration. Save metadata with each session so exports never inherit later settings. A single offset calibrates sensitivity at the reference conditions, not frequency response. The SPL result is an estimate.

## FFT and visualization

Sizes: 2048, 4096, 8192. Use a symmetric Hann window; normalize one-sided amplitudes by its actual coherent gain `sum(window)/N`. DC is not doubled. dBFS spectrum values refer to peak sinusoidal amplitude, unlike meter RMS. Frequencies derive from actual sample rate. A ring buffer supplies windows only after enough samples exist; scheduling targets about 22 analyses/sec, with overlap dependent on window size. Acquisition does not depend on UI rendering.

Dominant frequency is a spectral peak, not a polyphonic fundamental estimator. Apply a minimum level and local-prominence check, then interpolate neighboring log magnitudes. Musical notes label that peak. Silence/insufficiently prominent signals produce no stable-tone reading. Results on complex/noisy signals remain limited by the peak method.

Waterfall columns use logarithmic bands between 20 Hz and 20 kHz, max pooling available FFT bins; low bands narrower than FFT resolution share the nearest bin. The spectrum and waterfall use distinct resolutions. Freeze holds displayed spectrum, waveform, note readout, waterfall, and inspection values while acquisition can continue.

## Synthesis

Phase-accumulated sine; square/saw with polynomial BLEP discontinuity corrections; triangle with symmetric polynomial BLAMP slope corrections; white noise and Paul Kellet's three-pole pink-noise approximation. These polynomial corrections reduce aliasing; they do not create an ideal brick-wall band limit. Gain/frequency changes are smoothed, periodic-waveform changes crossfade, and ordinary start/stop use about 15 ms ramps. Foreground exit/focus loss/headphone removal demand immediate output release instead of draining queued sound.

Log sweeps use `f(t)=f0*(f1/f0)^(t/T)` with numerically accumulated continuous phase. Equal endpoints produce a constant tone. The default sweep is single-shot; the final ramp fits within its duration. Digital output dBFS controls amplitude, not the acoustic SPL delivered by a particular speaker/headphone.

## Persistence and exports

Room schema 2 adds a nullable calibration-profile JSON field with a non-destructive migration from schema 1. Export schema 2 includes the original profile when present. Timestamp ISO strings use UTC. Numeric time-series JSON retains Float precision; CSV rounds display values and carries comment-prefixed metadata. Chart history is representative-point decimation, not equivalent-energy aggregation. PNG and saved summaries use session units and settings.
