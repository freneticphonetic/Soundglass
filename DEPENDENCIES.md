# Dependency inventory

All runtime dependencies are open source. Versions are pinned in `gradle/libs.versions.toml`.

| Component | Purpose | License |
|---|---|---|
| Kotlin standard library | Language runtime | Apache-2.0 |
| kotlinx.coroutines | Structured asynchronous processing and flows | Apache-2.0 |
| AndroidX Core, Activity, Lifecycle | Platform integration | Apache-2.0 |
| Jetpack Compose UI, Material 3, material icons | UI and icons | Apache-2.0 |
| Navigation Compose | Screen navigation | Apache-2.0 |
| Room, AndroidX SQLite | Local numeric-session storage | Apache-2.0 |
| DataStore Preferences | Local settings/calibration | Apache-2.0 |

No Firebase, Moshi, HTTP client, analytics, maps, advertising, Google sign-in or secrets plugin is used. The Android framework supplies `org.json` and audio APIs. Gradle/AGP, Kotlin compiler, KSP, AndroidX Test, JUnit and Robolectric are build/test tools, not app services. Noise synthesis includes Paul Kellet's public-domain three-pole approximation.

To inspect the resolved transitive runtime graph:

```sh
./gradlew :app:dependencies --configuration debugRuntimeClasspath
./gradlew :app:processDebugMainManifest
```

Inspect the merged manifest under `app/build/intermediates/merged_manifests/debug/` and the packaged manifest with `apkanalyzer manifest print app/build/outputs/apk/debug/app-debug.apk`. The app manifest requests microphone access only. Third-party notices and Apache-2.0 terms are bundled in `app/src/main/assets/THIRD_PARTY_NOTICES.txt`; original code's GPL terms are in `LICENSE`. Version numbers listed only in the catalog do not imply a component is packaged: the resolved runtime graph is authoritative.
