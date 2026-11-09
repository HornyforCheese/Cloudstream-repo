# Validation results — 2026-11-09

The source builds a signed debug APK and a separate instrumentation APK. **Device runtime verification is incomplete.**

- Build: **PASS**, JDK 21, Gradle 9.3.1, AGP 9.1.1, Android SDK 36.
- JVM unit tests: **16 passed**, zero failures/errors.
- Android lint: **0 errors, 12 advisories**; see verification/lint-results-debug.html.
- Instrumentation: **five tests compiled; zero device tests passed/executed**. The emulator's Android system failed before app installation. See RUNTIME_DEBUG.md and verification/runtime/.
- Package: com.mutinymedia.android; min SDK 26; target SDK 36. APK v2 signature verified.
- Public endpoint checks: Cinemeta manifest/search/episode metadata and the replacement demonstration MP4 endpoint succeeded from the host. These do not establish Android playback.

The latest player correction wraps the provider-aware HTTP source in Media3's DefaultDataSource.Factory. See FIXES.md for all changes, README_FIRST.md for builds and DEVICE_CHECKLIST.md for tests requiring your device/providers.

Hardware decoding, real provider playback, OpenRouter credentials/billing, completed downloads and Android lifecycle behavior have not been verified end to end. Nuvio login/sync, executable CloudStream plugins, torrent/debrid engines and account synchronization remain unimplemented integrations. Configure your own release signing before distributing release APKs.
