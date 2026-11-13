# Mutiny Media for Android

Native Kotlin and Jetpack Compose Android app.

**Earlier build:** [APK ZIP and source ZIP](https://github.com/HornyforCheese/Cloudstream-repo/releases/tag/android-debug-2026-11-09).

The CloudStream provider runtime is pinned to v4.5.4. See the [integration, build checks, and physical-device test instructions](docs/CLOUDSTREAM_HOST_COMPATIBILITY.md).

- [Build instructions and features](README_FIRST.md)
- [Repairs and improvements](FIXES.md)
- [Validation results](REBUILD_STATUS.md)
- [Device checklist](DEVICE_CHECKLIST.md)

Use Android SDK 36 and a full JDK 17 or newer. Build with:

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
```

On Windows, use gradlew.bat. Debug APKs are for testing; configure your own signing for release distribution. Live provider and playback verification requires a physical device.
