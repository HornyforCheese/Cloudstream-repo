# CloudStream provider runtime

The Android host uses the CloudStream v4.5.4 library from upstream revision `91652da9203cd566648f1eaf27cac4e5f12c7a61`. The source and license are in [cloudstream-runtime](../cloudstream-runtime/NOTICE.md).

## Using providers

1. Open **Settings → Integrations → CloudStream**. Add and verify a repository, discover extensions, and select **Install**. A direct HTTPS `.cs3` URL also works.
2. Check the installed package's state and registered provider names. Use **Enable** for a package installed by an older Mutiny build, or **Reload** to retry failed initialization. Extensions with their own settings expose a **Settings** button.
3. Search or browse in the app, open a provider title, select an episode if available, and play an extracted source. The player handles direct HTTPS, HLS and DASH, plus supported subtitle formats, referers, headers and scoped session cookies.
4. Rediscover repository extensions to check for a higher version, then select **Update**. An active extension is replaced only after the download passes validation, and the previous package is restored if the new version fails activation. A direct URL can be installed again to refresh its package.
5. **Disable** unloads an extension and persists its disabled state. **Uninstall** removes the package and registration. If initialization is interrupted, the next launch leaves that package disabled until it is explicitly retried.

Extensions execute in the app process with the app's permissions. Package checks and HTTPS transport do not sandbox code. Install only extensions you trust. The compatibility baseline applies to provider and extractor APIs. Newer APIs, CloudStream-specific UI and action selection, plugin-supplied playback interceptors, DRM, segmented file playlists, and CloudStream torrents are outside this integration. Extension registration does not establish that a remote provider still responds or that every extracted stream plays.

## Build and compatibility checks

Use JDK 17, Android SDK Platform 36, and Build Tools 36.0.0.

```sh
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug \
  :app:assembleDebug :app:assembleDebugAndroidTest \
  :cloudstream-extension:assembleCloudStreamFixtures
./gradlew :app:dependencies --configuration debugRuntimeClasspath
curl -fL https://github.com/recloudstream/cloudstream/releases/download/v4.5.4/4.5.4.apk -o cloudstream-v4.5.4.apk
python3 tools/check_cloudstream_abi.py cloudstream-v4.5.4.apk app/build/outputs/apk/debug/app-debug.apk
```

The ABI check compares public and protected field and method descriptors, constructors, access flags, and class hierarchy against the pinned release APK. It samples the provider ABI and does not execute plugin code. The fixture compiles as a separate module with the runtime on its compile classpath only. The `.cs3` archives contain fixture DEX, a manifest, and fixture assets where applicable.

## Physical-device testing

Enable USB debugging on an Android 8.0 or later test device. Use a separate test device or profile if an existing install uses a different signing key. An installation signed with a different key will fail to update in place.

```sh
adb devices -l
adb -s DEVICE_SERIAL install -r app/build/outputs/apk/debug/app-debug.apk
adb -s DEVICE_SERIAL install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s DEVICE_SERIAL shell am instrument -w \
  -e class com.mutinymedia.android.CloudStreamRuntimeTest,com.mutinymedia.android.CloudStreamCompatibilityTest \
  com.mutinymedia.android.test/androidx.test.runner.AndroidJUnitRunner
adb -s DEVICE_SERIAL shell am instrument -w \
  -e class com.mutinymedia.android.RuntimeSmokeTest \
  com.mutinymedia.android.test/androidx.test.runner.AndroidJUnitRunner
```

The CloudStream tests cover fixture resource loading, provider and extractor registration, catalog and search, metadata, episodes, extraction, subtitles and headers, enable/disable, reload, uninstall, and rollback of partial registrations. The fixture maps to a public sample video. Its subtitle URL is synthetic and verifies metadata handoff only.

For a live regression check, try one trusted v4.5.4 extension and a title that you can legally access. Verify subtitles, player fallback, restart, and updating an active extension. Verify your own Stremio and Nuvio accounts and use your Real-Debrid account with a permitted test link. Live services and device playback require a device run. Building an instrumentation APK only verifies that the tests compile.
