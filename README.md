# Mutiny Media for Android

Native Kotlin/Jetpack Compose source and a signed debug APK for testing.

**Downloads:** [APK ZIP and source ZIP](https://github.com/HornyforCheese/Cloudstream-repo/releases/tag/android-debug-2026-11-09).

The build passes, all **16 JVM tests pass**, and Android lint reports **zero errors**. The separate device-test APK also compiles. Runtime device verification remains incomplete because the software emulator's Android system failed before app installation; see [runtime status](RUNTIME_DEBUG.md).

- [Build instructions and features](README_FIRST.md)
- [Repairs and improvements](FIXES.md)
- [Validation results](REBUILD_STATUS.md)
- [Device checklist](DEVICE_CHECKLIST.md)

Use Android SDK 36 and a full JDK 17 or newer. Build with:

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
```

On Windows, use gradlew.bat. Debug APKs are for testing; configure your own signing for release distribution. Nuvio login/sync, executable CloudStream plugins, torrent/debrid playback and account synchronization remain separate integrations.
