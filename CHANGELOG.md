# 1.1.0 — repair pass

- Corrected Gradle/AGP compatibility, supplied distribution checksum, removed unused network/service template dependencies, and restored portable debug signing. Added a CI verification workflow.
- Reworked microphone/playback lifecycle around individually owned hardware handles, serialized transitions, cancellation and foreground gating. Pause releases hardware; returning requires deliberate resume. Capture and generator cannot overlap during transitions. Android input-silencing callbacks are surfaced.
- Replaced unbounded/uncancellable calibration collection with bounded online energy/stability sampling. Validate the actual routed input fingerprint and configuration, surface failures, and preserve the complete calibration profile in saved sessions.
- Prevented FFT size/smoothing from resetting meter statistics. Weighting changes require confirmation before resetting a populated session. Route/unit changes start a coherent new session.
- Fixed analyzer freeze across spectrum, waveform and waterfall; added explicit analyzer permission/start/pause controls and logarithmic waterfall mapping. Improved weak/noisy-tone rejection and exact Hann normalization.
- Corrected triangle corner anti-aliasing, equal-frequency sweeps, single-shot sweep completion, output error handling and partial writes. Added waveform crossfades and immediate interruption cleanup.
- Added theme and duration settings, completion state, session-wide peak capture, adaptive chart retention, atomic session snapshots, UTC export timestamps and original-calibration metadata. Room version 1 sessions migrate to version 2 without deletion.
- Bundled authentic GPL text, dependency notices and in-app source/license access. Replaced unsupported verification/standards claims with implementation-specific documentation.

See VERIFICATION.md for executed tests and remaining device checks.
