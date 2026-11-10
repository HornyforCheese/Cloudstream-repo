# Mutiny Media for Android

Native Kotlin/Jetpack Compose app with the original ship branding. Open this directory in Android Studio. See REBUILD_STATUS.md for the validation results and DEVICE_CHECKLIST.md for checks that require your phone and providers.

## Build

Install Android Studio supporting Android Gradle Plugin 9.1.1, JDK 17 or newer (a full JDK, not only a JRE), Android SDK Platform 36 and Build Tools 36.0.0. The Gradle 9.3.1 wrapper JAR is included and the distribution checksum is pinned. The Unix launcher honors JAVA_HOME.

Windows:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Linux/macOS:

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Android Studio configures your local SDK path. For a command-line build, set ANDROID_HOME or create local.properties containing sdk.dir pointing at your SDK. The debug APK is app/build/outputs/apk/debug/app-debug.apk. Debug APKs are for testing; configure your own release signing before distributing a release APK. No keys or signing credentials are included in the source archive.

GitHub Actions runs the same unit tests, lint and debug APK build, and uploads the APK. Internet access to official Gradle, Google Maven and Maven Central repositories is required on the first build.

## Features

- HTTPS Stremio manifests, catalogs/search, series metadata and episode selection, direct episode-ID lookup, stream results and external provider links. Add HTTPS or stremio:// manifest links in Settings → Integrations. Retry saved addons after a network failure. Catalogs needing unsupported required extras are skipped.
- Direct HTTPS MP4/HLS/DASH playback through Media3 with provider request headers, Fit/Fill, error display and retry, audio focus, headphone-disconnect handling, background pause and foreground resume. A public Big Buck Bunny sample is available from Home. DRM, codecs and provider restrictions can still prevent a particular source from playing.
- Low-bandwidth preferences: 200–1,500 kbps video, 240/360/480p, 32/48/64 kbps audio, codec-based source ranking and adaptive track selection. These select available encodings; they do not transcode or guarantee a bandwidth cap.
- Metadata-based anime Dub/Sub filtering. Unknown labels are excluded in strict modes. Dual-audio releases may qualify for both modes.
- Local persistent watchlist: save catalog titles or the current lookup, reopen from Library, remove entries. No account synchronization.
- OpenRouter recommendations with a configurable model and an API key encrypted in Android Keystore. Configure your key in Settings. Requests need network access, model availability and any required account credit.
- Search has Movie, TV and Anime filters, provider-supplied genres, and a release-year range. Ask AI beside the field can match free-form requests against loaded titles from saved catalogs and the watchlist using OpenRouter. It submits up to 200 eligible candidate titles per request and can only return entries from that set.
- Direct HTTPS downloads through Android DownloadManager, saved in app-specific external Downloads. Android handles notifications and network retries. These files are removed when uninstalling the app.
- CloudStream repository metadata verification/persistence. This is repository discovery, without executable plugins.

## Integration limits

Nuvio authentication/sync, Stremio account sync, executable CloudStream .cs3 plugins, torrent/debrid engines, Cast, PiP, background media service, certified TV support and DRM/offline subscription playback require separate integrations. This revision does not claim Windows feature parity. Real-provider playback, OpenRouter and DownloadManager still need device verification with your setup.

## Validation and assets

The protocol tests use deterministic JSON fixtures for manifest validation, encoded search paths, series episode ordering, provider request headers and unsupported streams. Additional tests cover URL handling, byte limits and stream filters/ranking.

Original reference images are in reference/. Secrets are never bundled; application backup is disabled and cleartext traffic is disabled. Review REBUILD_STATUS.md and DEVICE_CHECKLIST.md before release.

## Device smoke suite

See RUNTIME_DEBUG.md for the compiled instrumentation suite and commands to run it on a working Android device/emulator. Device execution is not verified here.
