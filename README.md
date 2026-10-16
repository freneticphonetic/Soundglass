# Soundglass

An offline, open-source Android sound meter, spectrum analyzer, and signal generator. Original code is licensed **GPL-3.0-or-later**. Source: https://github.com/freneticphonetic/Soundglass

## Build

Requires a **full JDK 17** (including `javac`), Android SDK platform **36.1**, Build Tools **36.0.0**, and an SDK path in `ANDROID_HOME` or untracked `local.properties`.

```sh
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest :app:lintDebug
```

The pinned Gradle wrapper is **9.3.1**, compatible with Android Gradle Plugin **9.1.1**. Dependencies are pinned in `gradle/libs.versions.toml`. Android 10/API 29 or later is required. The debug APK is `app/build/outputs/apk/debug/app-debug.apk`. Android's ordinary debug signing key is generated on a fresh checkout; an AI Studio-provided `debug.keystore` takes precedence when present.

### Release signing

Keep keys outside this repository. Create a private key with the default build alias:

```sh
keytool -genkeypair -keystore /private/path/soundglass.jks -alias upload -keyalg RSA -keysize 3072 -validity 10000
```

Supply `KEYSTORE_PATH`, `STORE_PASSWORD`, and `KEY_PASSWORD` through your private build environment. `KEY_ALIAS` optionally overrides `upload`. Then run `./gradlew :app:assembleRelease`. Release builds are non-debuggable. Do not commit private keystores or passwords. The supplied debug APK is for installation/testing, not a production-signed release.

## What it does

- Meter: PCM RMS and sample peak, A/Z frequency weighting, per-sample Fast/Slow response, captured-sample duration, energy-equivalent average, clipping indicator, rolling graph, pause/resume/reset, and local saves.
- Analyzer: actual FFT with Hann window/coherent-gain normalization, interpolated dominant peak and note estimate, log spectrum, log-frequency waterfall, waveform, freeze/resume, and inspection cursor. Start capture explicitly; the screen does not open the mic automatically.
- Generator: sine, PolyBLEP square/saw, PolyBLAMP triangle, white/pink noise, and a single logarithmic sweep. Frequency and gain changes are smoothed; ordinary stop ramps down. Lifecycle/audio-route interruptions release output immediately.
- History: local Room database, rename/notes/delete, CSV with comment-prefixed metadata, JSON schema 2, PNG charts, and system-picker export/share. Version-1 sessions migrate without deletion.
- Settings: light/dark/device theme and session duration limit. Completing a session releases the mic and leaves the session available to save.

## Privacy

No Internet permission, ads, analytics, accounts, cloud API, or raw-audio file recording. PCM is processed in memory. Numeric summaries are persisted when you save; exports go only to the document location or share target you choose. Android backup and transfer exclusions are declared. Source and bundled licenses are accessible in the app.

Leaving the foreground stops capture and playback. Returning leaves measurement paused until you deliberately resume. Capture/playback transitions wait for the old worker to release its own hardware. Old cleanup cannot release a replacement handle.

## Calibration and sessions

Before calibration, the meter reads dBFS. A compatible saved calibration profile enables **estimated SPL**. Calibration averages three seconds of captured signal energy under the selected frequency weighting, rejects clipping/weak/unstable signals, and is bounded in memory. Cancellation and input changes discard pending calibration.

Profiles include route name/type/address (or Android device ID when no address is reported), capture source, sample rate, channel configuration, encoding, frequency weighting, date, reference level, and offset. Unknown/incompatible routes use dBFS with a warning. Device IDs can change after reconnection or reboot; recalibration may be needed. A scalar offset does not correct microphone frequency response. This app and its A-weighting approximation are not certified sound-level instruments.

Save before changing A/Z or Fast/Slow: those changes explicitly begin a new measurement session. A route or calibration change also starts a fresh session, avoiding mixed units/configurations. FFT size/smoothing changes leave the meter session intact. Saved sessions preserve original units and calibration metadata.

The live graph retains 45 seconds. Saved history is bounded by adaptive representative-point decimation, retaining early/late coverage. It is a chart, not a lossless recording of every transient. Whole-session equivalent energy uses every processed sample independently of chart decimation.

## Validation

See `VERIFICATION.md` for actual executed checks and remaining device checks. Test source alone is not evidence that a test passed. The repository includes deterministic DSP, calibration, resource-ownership and analyzer-ViewModel regression tests, plus Robolectric persistence/export tests.

## Source layout

- `audio`: capture/playback lifecycle, owned hardware handles, route diagnostics and retained effect controls.
- `dsp`: Android-independent level/FFT/synthesis/calibration algorithms.
- `data`: Room sessions/migration, DataStore settings and exports.
- `ui`: Compose screens and ViewModels.

See `DSP_DOCUMENTATION.md`, `DEPENDENCIES.md`, `THIRD_PARTY_NOTICES.txt`, and `LICENSE`.
