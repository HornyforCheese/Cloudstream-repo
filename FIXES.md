# Repairs and improvements

## Build and packaging

- Included the official Gradle 9.3.1 wrapper JAR and launchers; verified the JAR against the official checksum. Standard launchers honor JAVA_HOME and JVM options.
- Pinned the distribution checksum and added checksum verification to the Windows bootstrap helper.
- Replaced unavailable SDK 37 with SDK 36 and selected a compatible Compose BOM. Kept target SDK 36 and minimum SDK 26.
- CI now uses the wrapper and runs unit tests, Android lint and the debug APK build. Local SDK paths, build caches and signing keys are excluded from the source archive.

## Provider browsing

- Accept HTTPS and stremio:// manifest links; normalize trailing manifest slashes and reject malformed hosts, credentials, query strings and fragments.
- Validate manifest structure rather than accepting arbitrary JSON as an addon.
- Preserve movie/series/anime identities and deduplicate by type plus ID. Anime catalog types are no longer silently changed to series.
- Skip catalogs requiring unsupported extras and encode search terms/path components correctly.
- Added provider metadata episode lookup with season selection and provider-supplied episode IDs, plus direct episode-ID lookup.
- Preserve stream request headers, filenames and external website links. HTTPS source validation prevents malformed URLs from reaching the player.
- Clear previous sources before each lookup. Library → Find streams now opens Browse instead of returning to Home.
- Added saving the current lookup to the local watchlist, anime watchlist persistence, retrying saved addons and progress feedback.
- Limit parallel provider work, preserve coroutine cancellation, report total/partial provider failures and serialize UI operations so loading state cannot be reset by a different operation.

## Playback and networking

- Replaced a demonstration-video URL returning HTTP 403 with an official ExoPlayer sample verified to serve MP4 data.
- Feed provider request headers into the Media3 HTTP data source.
- Added lifecycle pause/resume, position retention across player recreation, audio focus and headphone-disconnect handling.
- Added playback retry, system-bar padding and removed an unnecessary constraints container.
- Added bounded HTTPS redirects and proper host validation. Block HTTPS-to-HTTP downgrade redirects.
- Enforce actual byte limits while reading provider and OpenRouter responses; the previous OpenRouter implementation read the entire response before truncating it.
- Prevent credentials from following automatic redirects on OpenRouter requests.

## Other behavior

- Keep main navigation, library state, browse input and settings category across recreation where applicable.
- Add keyboard insets to AI chat; validate key presence before appending a question and bound retained chat history.
- Validate nonblank AI model IDs before persisting.
- Query games off the main thread and recognize older game flags as well as the modern category.
- Guard external activity launches and sanitize special download filenames.
- Preserve all original branding PNG bytes; move the ship bitmap to drawable-nodpi for density-independent sizing.
- Remove the redundant orientation declaration and incorrect round-icon declaration. Add explicit cloud-backup/device-transfer exclusions so encrypted preferences cannot migrate without their Keystore key.

## Verification scope

See REBUILD_STATUS.md and VALIDATION.json for actual results. Unit tests cover filtering/ranking, URL validation/normalization, response byte limits and stream-protocol fixture behavior. Public Cinemeta manifest/search/episode metadata and sample MP4 endpoint checks were performed from the build environment. These endpoint checks do not establish end-to-end Android playback or arbitrary addon compatibility.

See DEVICE_CHECKLIST.md for playback, lifecycle, game launching, download and credential-dependent checks that require your device. No Nuvio login, executable CloudStream runtime, torrent/debrid engine or account-sync implementation is claimed.

## Follow-up APK debugging

- Corrected the Media3 data-source factory wrapper while retaining provider HTTP headers.
- Added five instrumentation smoke tests and an isolated game fixture. These compile but have not passed device execution in this environment; see RUNTIME_DEBUG.md.
