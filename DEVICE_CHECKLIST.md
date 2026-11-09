# Device verification before release

Build checks verify compilation, static analysis and unit behavior. Perform these checks on your target phone/tablet with your own providers before release:

- Launch the app, navigate all five sections, rotate, and check status/navigation bars and keyboard behavior.
- Play the demonstration video. Check seek, pause, Fit/Fill, Close, Back, headphone disconnect, audio interruption, background/resume and Retry after network loss.
- Add a valid HTTPS or stremio:// manifest. Test an invalid URL, unavailable host, Remove and Retry saved addons. Restart and confirm persistence.
- Search a known title. Test a provider without search support, a series with seasons, episode selection, direct episode ID (tt…:season:episode) and a missing title.
- Save/remove a title, restart and reopen Library → Find streams. Confirm Browse shows the selected title's sources.
- Test direct MP4, HLS and DASH from your providers, including URLs requiring request headers and external website links. Actual codec/DRM compatibility depends on the source and device.
- Verify low-bandwidth settings persist; compare available track selection and confirm unknown anime language sources are excluded in Dub/Sub modes.
- Configure an OpenRouter model/key in Settings. Send a question; test no key, invalid key, insufficient credit and offline states. Clear the key and confirm it is no longer saved.
- Queue a small HTTPS file; check DownloadManager notification and file in Android/data/com.mutinymedia.android/files/Download. Files are app-specific and removed when the app is uninstalled.
- Refresh and launch an installed Android game. Only packages Android labels as games are listed.

Nuvio login/sync, Stremio account sync, executable CloudStream plugins, torrent/debrid engines, Cast, PiP, background media services and DRM/offline subscription playback are not implemented.
