# Feature parity tracker

| Windows Mutiny Media goal | Android project status | Scope / next step |
|---|---|---|
| Pirate ship logo in every branded surface | **Implemented in Android entry point** | Audit every future surface and adaptive icon masks |
| Startup shows Mutiny Media | **Implemented** | Android app label and native header; add animated splash later |
| Phone/tablet landscape responsive UI | **Implemented starter** | Needs device tests |
| Native Android TV / DPAD | **Partial** | Layout uses rail; focus testing and launcher support pending |
| Stremio addon support | **Partially functional** | Manifest/catalog/stream HTTPS, metadata episodes, request headers and external links; account login pending |
| Nuvio | **Not implemented** | Requires authenticated provider adapter |
| CloudStream | **Repository discovery only** | Needs isolated extension runtime |
| Ultra Low Bandwidth 200–1500 kbps | **Implemented preference/ranking + track constraints** | ABR only on compatible HLS/DASH; no transcoder |
| Anime English dub / Japanese sub filters | **Implemented metadata-based filtering** | Source tags frequently unknown; no false certainty |
| OpenRouter AI chat | **Implemented client** | Needs network/API key and end-to-end testing |
| Local game launcher | **Implemented for Android packages** | Windows games require remote streaming |
| Game torrent downloader | **Not implemented** | Native P2P service and safe storage policies needed |
| Stream games while downloading | **Not implemented** | Source/progressive asset delivery not universal |
| Direct HTTPS downloads | **Implemented via system DownloadManager** | Progress UI and downloads database pending |
| Persistent watchlists / continue-watching | **Local watchlist implemented; progress not implemented** | DataStore-backed local watchlist; profile sync and resume positions still needed |
| Video player | **Media3 direct URL player implemented** | Lifecycle pause/resume, retry and audio focus included; background media session, Cast, PiP and device testing pending |
| Theme Studio | **Native theme only** | Persisted token editing and live preview pending |
| Transparent bottom action buttons | **Raised quick-action buttons** | Visual polish/device screenshots pending |
| Full screen aspect ratio | **FIT / FILL-CROP toggle implemented in source** | No stretching; device tests pending |

**Verification:** See REBUILD_STATUS.md for current build/test results and DEVICE_CHECKLIST.md for target-device verification.
