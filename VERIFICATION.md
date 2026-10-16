# Verification — Soundglass 1.1.0

Performed 2026-10-16 against the repaired source derived from upstream commit `5500bb8`.

## Executed checks

| Check | Result |
|---|---|
| Android/Kotlin compilation | Passed |
| Clean project rebuild and debug APK assembly | Passed |
| JVM/Robolectric unit tests | 33 passed, 0 failures, 0 errors |
| Android lint | 0 errors, 59 warnings |
| Git diff whitespace/conflict check | Passed |
| APK signature | Verified with Android apksigner (v2), debug key |
| Packaged manifest | RECORD_AUDIO and AndroidX private receiver permission; no INTERNET |
| Final UI implementation in APK | Weighting-change confirmation present in compiled DEX |
| Bundled license bytes | Match source LICENSE |

Build commands: `./gradlew clean :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`. The build environment used a full JDK 17, Gradle 9.3.1 and Android SDK 36.1/Build Tools 36.0.0. Dependencies and Robolectric's Android runtime were downloaded through the environment's network proxy; no proxy settings are included in app source or runtime.

Tests cover RMS/sample peak, equivalent energy, Fast/Slow response and block partitioning, A-weighting response, FFT peak interpolation and silence/noise rejection, synthesis amplitude, equal-frequency sweep completion, triangle wrap continuity, calibration energy/completeness/input matching, owned-resource idempotency/isolation, actual analyzer ViewModel freeze/settings wiring, Room DAO operations and CSV/JSON exports. Some original DSP-isolation tests remain; they do not prove physical microphone routing.

Lint warnings are advisory style/resource/dependency/icon recommendations, including UseKtx, unused resources, icon recommendations, newer dependency notices, redundant label and legacy API guards. No errors were suppressed to make lint pass. The added GitHub Actions workflow has not been run on GitHub in this session.

APK SHA-256: `695ba8013ab0282ca6e8eaffaf7ac1f3a89bf3b45e743bb2414dd057c6140897`

## Physical-device checks still required

1. Install on a Pixel 9 or other Android 10+ device. Grant/deny/revoke microphone access and toggle Android microphone privacy. Confirm the app reports unavailable/silenced input correctly.
2. Start, pause, resume, rapidly switch Meter/Analyzer/Generator, press Home, and rotate. Confirm the Android microphone indicator disappears on pause/foreground exit, returning remains paused, and capture/playback never overlap.
3. Connect/disconnect external input devices, including two different USB microphones. Confirm calibration warnings and coherent new sessions; do not infer physical route behavior from the unit tests.
4. Calibrate with a steady reference meter/calibrator. Check stability/clipping errors, cancel midway, then recalibrate. Compare estimated SPL under matching settings.
5. Check audible waveforms/sweeps at a suitable volume, stop behavior, audio-focus interruption and headphone removal.
6. Save/rename/delete a measurement; export CSV/JSON/PNG through the system picker and share it. Check an existing version-1 database migration on a real installed app.
7. Check long sessions, automatic duration completion, large font/TalkBack, light/dark themes and compact-screen layouts.

No connected-device, acoustic calibration or listening test was performed in this environment. The supplied APK is a debug-signed test build, not an owner-signed production release. Private signing keys are excluded from the source archive.
