# Soundglass

**Soundglass** is a native Android acoustic measurement and audio analysis application. It provides real-time sound level metering (dBA/dBZ, Fast/Slow, Leq), FFT spectral analysis, a scrolling waterfall spectrogram, a band-limited tone and noise generator, and local measurement recording with CSV/JSON/PNG exports.

Licensed under the **GNU General Public License v3.0 or later (GPL-3.0-or-later)**.

---

## 1. Privacy & Offline Architecture

Soundglass operates **entirely offline** with zero remote dependencies:
- **No `android.permission.INTERNET`:** Soundglass does not declare or request network access in its manifest. Network transmission is physically prevented at the Android OS kernel level.
- **Zero Raw Audio Storage:** Raw PCM microphone samples are processed in volatile memory only and discarded immediately. Only aggregated numerical summaries and decimated time-series levels are saved when the user explicitly saves a measurement.
- **No Analytics, Tracking, or Proprietary Services:** Completely free of telemetry SDKs, Google Analytics, Firebase, crash trackers, or cloud storage.
- **Backup Disabled:** Cloud auto-backup and device-to-device transfers are disabled in both `backup_rules.xml` (API < 31) and `data_extraction_rules.xml` (API >= 31).
- **Scoped Storage & SAF:** File exports use the Android Storage Access Framework (`CreateDocument`) document picker. No broad storage permissions are requested.
- **Lifecycle Cleanliness:** Microphone capture and tone generation automatically halt and release audio hardware when the app leaves the foreground.

---

## 2. Core Capabilities

### Sound Level Meter
- Real PCM calculations: RMS level in dBFS and Sample Peak in dBFS.
- Exponential time weighting: **Fast** ($\tau = 125\text{ ms}$) and **Slow** ($\tau = 1000\text{ ms}$) per IEC 61672-1.
- Frequency weighting: **A-Weighting** (3-biquad IIR cascade, calibrated $0.00\text{ dB}$ at $1\text{ kHz}$) and **Z-Weighting** (flat).
- Real-time statistics: Current level, Session Minimum ($L_{\min}$), Session Maximum ($L_{\max}$), and Energy-Equivalent Average Level ($L_{\text{eq}}$).
- Rolling live level history graph with guideline indicators.
- Hardware clipping detection with persistent session counters and visual alert banner.
- Calibration workflow: reference meter input, 3-second stability sampling, persistent offset, device profile tracking, and reset.

### Frequency Analyzer
- Live oscilloscope waveform view.
- Real-time FFT spectrum with logarithmic frequency axis ($20\text{ Hz} - 20\text{ kHz}$) and labeled dBFS units.
- Scrolling waterfall spectrogram with readable multi-stop heatmap legend.
- Dominant frequency estimation using quadratic parabolic interpolation on spectral peaks.
- Automatic musical note and cents deviation readout (e.g. `A4 +1 ct`).
- Selectable FFT sizes: **2048**, **4096**, and **8192** samples with Hann windowing and coherent gain normalization.
- Spectrum smoothing: None, Fast decay ($0.6$), Slow decay ($0.2$), and Peak Hold.
- Freeze/resume and interactive touch cursor to inspect exact frequency and amplitude.

### Tone & Noise Generator
- Waveforms: **Sine**, **Triangle** (band-limited), **Square** (anti-aliased PolyBLEP), **Sawtooth** (anti-aliased PolyBLEP), **White Noise**, **Pink Noise** (Paul Kellet 1/f filter bank).
- Logarithmic sine sweep: Start Hz, End Hz, Duration ($0.5\text{s} - 60\text{s}$), with live progress bar and instantaneous frequency readout.
- Logarithmic frequency slider ($20\text{ Hz} - 20\text{ kHz}$) and direct numeric entry.
- Digital output amplitude control ($-60\text{ dBFS}$ to $0\text{ dBFS}$) with high-volume warning.
- Click-free $15\text{ ms}$ linear ramps on start, stop, and parameter transitions.
- Audio focus handling and automatic stop on headphone disconnect (`ACTION_AUDIO_BECOMING_NOISY`).
- Mutual exclusivity: Starting generator automatically pauses microphone capture.

### Saved Measurements & Local Exports
- Local storage using Room database (`SavedSession` entity).
- Search and filter by session title and notes.
- Complete metric summaries with explicit calibration state and unit labels.
- Export options:
  - **CSV:** RFC 4180 compliant with metadata header and time-series columns.
  - **JSON:** Structured JSON with `schemaVersion: 1` and complete metrics.
  - **PNG Chart:** High-resolution ($1280 \times 800$) chart image with labeled axes, grid, guidelines, and summary box.
  - Direct sharing via `FileProvider`.

---

## 3. Build & Compilation Instructions

### Requirements
- JDK 17 or JDK 21
- Android SDK with Build Tools 36.0.0 and compileSdk 36
- Gradle 8.13+

### Build Commands
```bash
# Run unit and DSP tests
gradle :app:testDebugUnitTest

# Assemble Debug APK
gradle :app:assembleDebug

# Assemble Release APK (unsigned or upload-key signed)
gradle :app:assembleRelease
```

Debug APK location: `app/build/outputs/apk/debug/app-debug.apk`

---

## 4. Release Signing Instructions

To build a release APK signed with your private keystore without checking secrets into version control:

1. Generate your production keystore:
   ```bash
   keytool -genkey -v -keystore my-release-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias soundglass-release
   ```
2. Set the environment variables on your build machine:
   ```bash
   export KEYSTORE_PATH="/path/to/my-release-key.jks"
   export STORE_PASSWORD="your-store-password"
   export KEY_PASSWORD="your-key-password"
   ```
3. Run the release build:
   ```bash
   gradle :app:assembleRelease
   ```
4. Output APK location: `app/build/outputs/apk/release/app-release.apk`

---

## 5. Verification & Acceptance Checklist

### Automated Test Suite (`gradle :app:testDebugUnitTest`)
- [x] **RMS & Peak Accuracy:** Verified on full-scale sine waves (RMS $-3.01\text{ dBFS}$, Peak $0.00\text{ dBFS}$).
- [x] **Square Wave Energy:** Verified full-scale square wave ($0.00\text{ dBFS}$ RMS).
- [x] **Zero-Input Silence:** Verified silence evaluates below $-90\text{ dBFS}$.
- [x] **Energy-Equivalent Leq:** Verified piecewise integration over step power signals.
- [x] **Fast & Slow Exponential Step Response:** Verified at $t = 125\text{ ms}$ (Fast reaches $-1.99\text{ dBFS}$, Slow reaches $-9.30\text{ dBFS}$).
- [x] **A-Weighting IEC 61672-1 Frequency Response:** Verified at $100\text{ Hz}$ ($-19.1\text{ dB}$), $1000\text{ Hz}$ ($0.00\text{ dB}$ ref), $4000\text{ Hz}$ ($+1.0\text{ dB}$), and $8000\text{ Hz}$ ($-1.1\text{ dB}$).
- [x] **FFT Interpolation & Pitch Detection:** Verified on $440.0\text{ Hz}$ (A4) and off-bin $1005.3\text{ Hz}$ tones.
- [x] **Silence Handling:** Verified FFT tone detection suppression on noise/silence.
- [x] **Tone Synthesis Bounds:** Verified all 7 waveforms stay within signed 16-bit limits without wrapping.
- [x] **CSV/JSON Serialization:** Verified round-trip formatting and schema versioning.
- [x] **Room Database Operations:** Verified insert, flow observation, update, and delete.

### Physical-Device Verification Checklist (To Be Performed on Device)
1. **Microphone Capture:** Launch app, tap "Start Measurement", accept permission dialog. Confirm live dB readout responds to speech and ambient noise.
2. **Unprocessed Source:** Open Audio Diagnostics dialog. Confirm capture source indicates `UNPROCESSED` or clean fallback, with AGC and NS disabled.
3. **Reference Meter Calibration:** Apply 94.0 dB 1 kHz acoustic calibrator to microphone. Execute calibration workflow. Verify reading locks to 94.0 dB SPL.
4. **Tone Generator Output:** Switch to Generator tab. Play 440 Hz Sine wave. Confirm sound plays clearly without clicks on start or stop.
5. **Headphone Removal:** Disconnect headphones while generator is playing. Confirm playback halts immediately via `ACTION_AUDIO_BECOMING_NOISY`.
6. **Mutual Exclusivity:** Confirm playing generator pauses sound level meter, and starting meter stops generator.
7. **Foreground Exit:** Press Home button. Confirm microphone icon in Android status bar disappears immediately.
8. **Document Export:** Save a 30-second measurement. In History detail, export CSV, JSON, and PNG. Open exported files in system viewer and verify integrity.
