# Android feature status

| Area | Implemented in Android | Remaining work |
|---|---|---|
| Branding and layout | Mutiny Media app name and ship artwork, responsive phone and tablet navigation, pirate welcome panel | Verify focus and launcher behavior on Android TV and handhelds |
| Stremio | HTTPS manifests, catalogs, metadata, episodes, streams, account import, persistent addon priority and exclusion | Verify live accounts and provider combinations on a device |
| Nuvio | Server discovery, sign-in, profile selection and import of compatible addons and saved titles | Native Nuvio extension execution |
| CloudStream | v4.5.4 provider ABI, extension loading, provider catalogs, search, episodes and stream extraction | Validate trusted third-party extensions on a physical device |
| Search | Combined provider results and deduplication by exact type and ID, plus OpenRouter search over saved catalogs | Cross-provider matching for different title IDs |
| Stream selection | Exact source resolution, bandwidth and codec preferences, automatic HTTPS fallback | Resolution and bitrate guarantees require accurate source metadata and suitable provider tracks |
| Anime | English dub, sub-labelled sources, dual audio and uncensored/uncut filters | Independent verification of provider labels |
| Playback | Media3, external subtitle tracks, bitrate readout, local per-title/episode resume, source fallback | CPU/GPU upscaling, Cast, PiP and background playback |
| Real-Debrid | HTTPS hoster links and supported torrent hashes resolved through the user's account | Direct P2P playback and live account verification |
| Library | Local watchlist and named collections | Account sync for local edits and collection export |
| Appearance | Live colour presets, system/serif/monospace fonts and randomisation | Full desktop Theme Studio parity with custom layouts and theme import/export |
| AI | OpenRouter API key entry and key link, free text model picker and recommendations | Verify a live request with the user's account |
| Downloads | Direct HTTPS files through Android DownloadManager | Torrent downloads and game streaming |
| Additional integrations | Supported settings are available from their working screens | Watch parties, tracking services, TMDB/MDBList/OMDb settings, Live TV and Discord |

See [device verification](DEVICE_CHECKLIST.md) and [the CloudStream runtime notes](docs/CLOUDSTREAM_HOST_COMPATIBILITY.md).
