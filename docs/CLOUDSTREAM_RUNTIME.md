# Batch 5: CloudStream provider runtime

Mutiny embeds CloudStream v4.5.4 source, pinned to upstream commit
91652da9203cd566648f1eaf27cac4e5f12c7a61. See
`cloudstream-runtime/NOTICE.md`, its GPL-3.0 license, and
`docs/CLOUDSTREAM_HOST_COMPATIBILITY.md` for architecture and reproduction details.
Current upstream master was audited; APIs added after this baseline are not claimed.

## Integrated behavior

Settings → Integrations → CloudStream supports repository discovery, direct HTTPS
`.cs3` installation, update/reinstall, removal, initialization diagnostics,
enable/disable, reload, settings callbacks, browsing and search. Real MainAPI
providers return title metadata and movie/series/anime episode data through a typed
adapter. Stream and subtitle callbacks feed Mutiny's Media3 player with format
hints, headers, referers and CloudStream networking/session handling. Existing Home
shelves, Stremio/Nuvio clients, account persistence, source filters, ranking,
Real-Debrid integration and playback fallback are retained.

The host executes Android Plugin.load(Context) or portable BasePlugin.load(),
registers actual MainAPI/extractor instances, and cleans up registrations on failure,
disable, removal and reload. Updates validate staged downloads and restore the
previous package if replacement initialization fails. Interrupted initialization
is recorded and requires explicit recovery rather than an automatic crash loop.
Resource-bearing archives use Android resource loaders. Code is read-only before
Android dynamic loading. The final integration preserves the newer branch commits
for CloudStream, playback/source filtering and Home catalog shelves.

## Verification

Use JDK 17, Android SDK 36 and build tools 36.0.0:

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest :cloudstream-fixture:packageFixture
python3 scripts/check-cloudstream-abi.py --baseline cloudstream-runtime/abi-v4.5.4.json --candidate app/build/outputs/apk/debug/app-debug.apk
```

The stored ABI baseline is extracted from the official v4.5.4 APK; its SHA-256 is
embedded in the JSON. The check compares public/protected descriptors, constructors
and inheritance for 40 selected host, response, extractor and action classes.
Implementation-only lambda/state-machine classes and post-compilation `$r8$` helpers
are excluded; Kotlin default bridges remain checked.
A second checker, `tools/check_cloudstream_abi.py`, also checks access flags and a
broader 90-class provider/network API sample against the official APK. These are bounded
checks, not proof that every extension is compatible.

Instrumentation includes separately compiled/D8-packaged provider and failure
fixtures in `test-fixtures/cloudstream-extension`. It exercises resource loading,
provider/extractor registration, catalogs, search, metadata, episodes, extraction,
subtitles/headers, enable/disable, reload, uninstall and rollback. The supplied
subtitle URL is synthetic: it verifies metadata handoff, not subtitle downloading.

The additional `cloudstream-fixture` module produces a downloadable legal `.cs3`
with deterministic movie/series metadata and a real extractor registration. It
extracts Google's public Big Buck Bunny MP4 (Blender Foundation, CC BY 3.0). Search
`bunny` for a movie or `series` for season 2, episode 3. It does not certify external
streaming services. `downloads/CLOUDSTREAM_TESTING.md` provides its raw HTTPS URL.

No emulator or device execution is represented as passed. The user explicitly
requested that device testing be left to them. Run on a connected physical device:

```sh
./gradlew :app:connectedDebugAndroidTest
```

See `verification/cloudstream/REPORT.md` for final build evidence and counts.

## Device acceptance

1. Install the debug APK and the legal fixture through CloudStream settings.
2. Browse/search, open movie details and episodes, extract streams and play the
   public sample. Test provider disable/enable, reload, removal and restart.
3. Install another trusted extension and test coexistence and updates; verify
   removing one extension leaves the other available.
4. With a legally accessible real provider, test headers/cookies, subtitles,
   direct HTTPS, HLS, DASH and fallback after a failed source.
5. Recheck your Stremio/Nuvio account restore/import, saved settings/watchlist,
   Home/title navigation, anime/source filters and Real-Debrid with your own account.
   No credentials were supplied for live account regression testing.

## Security and limitations

In-process extension execution is **not a security sandbox**. Plugins have the app's
permissions and can access its data, create threads, log credentials themselves,
or bypass host clients. Exception handling cannot contain native/VM crashes or
malicious code. Coroutine timeouts require cooperative code; beforeUnload cannot
forcibly stop extension-created threads. Only install extensions you trust.

Packages have bounded HTTPS downloads and structural manifest/ZIP/DEX validation.
These checks are not a malware scanner or a proof of ABI compatibility. Host
networking retains TLS validation and cleartext restrictions; WebView TLS failures
cancel. Library debug output of request URLs, response bodies and encryption
material is removed, and API errors log exception types instead of message bodies.

Newer API symbols and CloudStream app-specific screens/helpers may remain
incompatible. Registered video actions have no Mutiny action picker. DRM,
torrent/magnet and multi-segment playlist extraction are not mapped by the provider
adapter. Physical-device playback and live extension/account regressions remain
unverified. Batch 5 is not device-verified complete until those checks pass.
