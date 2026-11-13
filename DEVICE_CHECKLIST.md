# Device verification before release

Build checks verify compilation, static analysis and unit behavior. Perform these checks on your target phone/tablet with your own providers before release:

- Launch the app, navigate all five sections, rotate, and check status/navigation bars and keyboard behavior.
- Play the demonstration video. Check seek, pause, Fit/Fill, Close, Back, headphone disconnect, audio interruption, background/resume and Retry after network loss.
- Add a valid HTTPS or stremio:// manifest. Test an invalid URL, unavailable host, Remove and Retry saved addons. Restart and confirm persistence.
- Search a known title. Test a provider without search support, a series with seasons, episode selection, direct episode ID (tt…:season:episode) and a missing title.
- Create a collection in My Library, add a title from its detail page, restart, and confirm membership. Remove it from the collection and check its watchlist entry separately.
- Enable only a CloudStream extension with a catalog and check that Browse → Browse catalogs can load it. Exclude a Stremio-compatible addon, move another up, and verify the order survives restart.
- Select an exact resolution in Settings → Playback and check that unknown and conflicting labels are omitted. Test the dual-audio and uncensored filters on an anime title.
- Play a movie or selected episode for at least 15 seconds, close it, restart the app, and open a source for the same title. Check the resume position and verify it is cleared near the end.
- Randomise colours and font in Settings → Appearance and verify the new appearance applies immediately and persists after restart.
- Test direct MP4, HLS and DASH from your providers, including URLs requiring request headers and external website links. Actual codec/DRM compatibility depends on the source and device.
- Verify low-bandwidth settings persist; compare available track selection and confirm unknown anime language sources are excluded in Dub/Sub modes.
- Configure an OpenRouter model/key in Settings. Send a question; test no key, invalid key, insufficient credit and offline states. Clear the key and confirm it is no longer saved.
- Queue a small HTTPS file; check DownloadManager notification and file in Android/data/com.mutinymedia.android/files/Download. Files are app-specific and removed when the app is uninstalled.
- Open Search, load saved catalogs, and verify Movie, TV, Anime, genre and year filters. With an OpenRouter key, search by an actor or a style and verify results are drawn from the saved catalog candidates.

For current implementation boundaries see [Feature status](FEATURE_PARITY.md). For the signed app/test APK installation and physical CloudStream fixture commands see [CloudStream verification](docs/CLOUDSTREAM_HOST_COMPATIBILITY.md).
