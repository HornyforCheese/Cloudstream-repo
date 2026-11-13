# Batch 5: CloudStream provider runtime

Mutiny now includes upstream CloudStream v4.5.4 library source and Android Plugin
API. See `cloudstream-runtime/UPSTREAM.md` and its GPL-3.0 license for provenance,
source adaptations and the version boundary. Current upstream master was audited;
this runtime does not claim compatibility with APIs added after the pinned release.

## Execution and navigation

Settings → Integrations → CloudStream supports repositories, direct HTTPS `.cs3`
URLs, discovery, installation, updates, removal, initialization diagnostics,
provider enable/disable, homepage browsing and search. Search results also appear
alongside Stremio results in the main search. Title navigation keeps provider URLs
and episode data separate from Stremio IDs. Provider metadata, movie data and series
or anime episode lists feed the existing detail screen and stream selection flow.

Extraction invokes real MainAPI.loadLinks and the upstream extractor registry.
HTTPS direct video, HLS and DASH streams use Media3. MIME hints, referers, headers,
provider interceptors, session cookies and external subtitle configurations reach
the player. Existing bandwidth ranking, anime filtering and playback fallback still
operate on StreamOption. Real-Debrid output uses its own playback transport.

Initialization runs Android Plugin.load(Context) or portable BasePlugin.load(). A
plugin is initialized only after provider/extractor registration succeeds. Loading
and unloading are serialized against the process-global upstream registries. A
failure removes that package's registrations; uninstall/reload removes providers,
extractors, actions, mappings and cached playback clients. Provider switches persist
in separate preferences. Process-owned plugins survive ViewModel recreation.

Interrupted initialization is recorded before executing downloaded code. On a later
start, that package waits for explicit Reload instead of automatically repeating the
interrupted initialization. Downloads validate before replacing the current file;
replacement is atomic at the filesystem rename boundary. Android 14+ dynamic-code
files are read-only before class loading. Package IDs are validated SHA-256 strings.

## Security boundary

Extensions execute in the application process with its Android permissions. This
is **not a security sandbox**. Install only code you trust. Exception/linkage
handling cannot contain native crashes, VM failures, malicious code, background
threads, or non-cooperative blocking code. Coroutine timeouts are cooperative.
Plugins can deliberately bypass host clients or access app data. Strong isolation
would require a separate UID/process design and an IPC provider protocol.

Downloads use HTTPS, bounded redirects, connection/read timeouts and a 30 MiB cap.
ZIP entry counts, expanded sizes, duplicate entries, manifest/class names and DEX
headers are checked before Android's bytecode verifier and linker run. These checks
are structural, not a malware scanner or proof of binary compatibility. Host network
requests retain normal TLS verification; WebView TLS errors cancel. Cleartext is
not enabled. Host-provided logs omit request URLs, credentials and exception bodies;
a plugin can still perform its own logging. Cookies use domain/path/expiry matching.

## Reproducible verification

With JDK 17, Android SDK 36 and build tools 36.0.0:

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
python3 scripts/check-cloudstream-abi.py --baseline cloudstream-runtime/abi-v4.5.4.json --candidate app/build/outputs/apk/debug/app-debug.apk
```

The ABI baseline is extracted from the official v4.5.4 release APK, whose SHA-256
is embedded in the JSON. The checker compares exact public/protected method and
field descriptors, constructors and inheritance for the selected host interfaces,
provider response models, extractors and action API. Post-compilation `$r8$` optimizer
helpers are excluded; Kotlin default bridges and constructors remain checked. It is a bounded ABI check,
not proof that every symbol used by every extension exists. To reproduce the
reference, download the official release and use `--reference <apk>
--write-baseline <json>` with the same script.

`cloudstream-fixture` compiles a separate extension against the host API with
compileOnly dependencies; D8 packages its classes and manifest into a real `.cs3`.
The fixture classes are not linked into the app. Its movie and series metadata are
deterministic fixture data, and its extracted MP4 points to Google's public Big
Buck Bunny sample (Blender Foundation, CC BY 3.0). No third-party streaming service
is contacted by the provider fixture. It exercises the actual upstream extractor
registry, not a replacement extractor host.

On a connected physical device, run (no emulator required):

```sh
./gradlew :app:connectedDebugAndroidTest
```

CloudStreamRuntimeTest covers separate-DEX loading, Android and portable lifecycles,
provider/extractor registration, homepage, movie search/details, seasons/episodes,
stream extraction, header/referer propagation, ViewModel playback handoff,
provider enable/disable, unload/reload, partial-registration rollback and coexistence.
CloudStreamCompatibilityTest covers missing and malformed packages. Package unit
tests cover missing bytecode, invalid headers, invalid class names and structural
validation. Existing account, Stremio, network, catalog and anime filter tests remain.

Device execution was explicitly deferred to the user. Compiling an instrumentation
APK does not count as passing those runtime tests or verifying live playback.

## Device acceptance checklist

1. Install the debug APK, open CloudStream settings, add a trusted repository or
   direct `.cs3` URL, and verify initialized provider/extractor counts.
2. Install the provided legal fixture from its raw HTTPS URL; search `bunny`, open
   its metadata and play the public sample. Search `series` and verify season 2,
   episode 3 loads and extracts the same public sample.
3. Browse the fixture homepage. Disable its provider, search again, enable it,
   restart the app and confirm the switch and registration state recover correctly.
4. Install a second trusted extension and verify independent search and removal.
   Update/reinstall, reload and uninstall each; verify removed providers disappear.
5. Test real providers separately: movie and series load, subtitle selection,
   cookies/referers, HLS/DASH and fallback after a deliberately failed source.
6. Recheck Stremio/Nuvio account restore/import, saved settings/watchlist, title
   navigation, anime filters and your own Real-Debrid account. No credentials for
   live account regression testing were supplied in this session.

## Remaining boundaries

No live extension/service compatibility is certified without device testing. APIs
added after v4.5.4 and other CloudStream app-only screens/resources/helpers can fail
linkage with an actionable version diagnostic. Registered video actions are retained
and cleaned up but Mutiny does not offer an action picker. DRM, torrent/magnet and
multi-segment playlist extraction are not mapped to this player adapter. Unsupported
link types are filtered rather than presented as directly playable HTTPS sources.
Extension-created jobs/threads must cooperate with beforeUnload; class loaders can
only become collectible when no plugin references remain. Search/catalog provider
failures are isolated and reported, but cooperative timeout cannot stop arbitrary
extension code. Batch 5 cannot be declared device-verified complete until the above
runtime and live-regression acceptance checks pass.
