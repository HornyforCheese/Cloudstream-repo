# Android architecture • Mutiny Media

```text
Phone / tablet / handheld / Android TV (future tested target)
                   │
             MainActivity
                   │
      Jetpack Compose UI layer
  ┌─────────────┬───────────────┐
  │ Responsive  │  Screens      │
  │ navigation  │  Home/Browse/ │
  │ and theme   │  Games/AI/    │
  │             │  Integrations │
  └─────────────┴───────────────┘
                   │
           MutinyViewModel
            StateFlow state
                   │
       Domain: UserSettings,
   StreamFilters, MediaItem, Addon
                   │
  ┌───────────────┬─────────────────┬───────────────────┐
  │ SettingsStore │ StremioClient   │ AndroidServices   │
  │ DataStore     │ HTTPS JSON API  │ DownloadManager   │
  │ SecretsStore  │ catalog/stream  │ PackageManager    │
  │ Keystore      │                 │                   │
  └───────────────┴─────────────────┴───────────────────┘
                   │
          Media3 ExoPlayer
          PlayerView FIT mode
```

## Existing boundaries

- `ui/`: presentation, responsive layouts, screens and native playback surface.
- `domain/`: deterministic policy: anime release labelling, bandwidth ranking, transport-neutral models. Unit test these before touching playback.
- `data/`: HTTPS addon adapters, storage, game and download platform APIs, OpenRouter client.
- `MutinyViewModel.kt`: orchestrates state, errors and async IO with lifecycle-aware coroutines.
- `SettingsStore`: per-app persisted preferences via DataStore, no credentials.
- `SecretsStore`: AES-256-GCM using Android Keystore, private ciphertext storage.

## Production modularisation path

```text
:app                     launch, dependencies, navigation
:core:model              pure Kotlin domain models
:core:settings           DataStore + secret storage
:core:network            validated HTTP APIs, errors, rate limits
:feature:integrations   Stremio, Nuvio, CloudStream setup
:feature:discovery      catalog + provider aggregation + ranking
:feature:player         Media3 session, caching, fullscreen, PiP
:feature:anime          dub/sub matching and metadata detection
:feature:games          installed games, remote gaming handoff
:feature:ai             OpenRouter client + local history
:feature:downloads      WorkManager/DownloadManager, optional P2P
:platform:extensions     sandboxed Android extension runtime [FUTURE]
```

Do **not** create fake connected statuses to mimic the reference screenshot. A provider should report connected only after a real authentication or manifest roundtrip; a validated CloudStream JSON repo is **not** a working video provider.

## Stream decision flow

1. Read addon manifests from secure, user-provided HTTPS URLs.
2. Query catalogs or a specific ID and associate result with anime metadata.
3. Resolve streams from the applicable enabled providers.
4. Apply anime mode only for anime. Unknown language is excluded in strict dub/sub modes, not guessed.
5. Rank *metadata-labelled* streams for resolution, bitrate and codec. If metadata is missing, mark quality unknown.
6. Media3 plays a direct HTTPS URL; adaptive renditions only exist where offered by HLS/DASH.
7. Never hand a magnet or `.torrent` URL directly to Media3 and claim it streams a game/video.

## TV and aspect ratio principles

- Responsive `BoxWithConstraints`: bottom bar at narrow widths; rail at tablet/landscape widths.
- Video uses `AspectRatioFrameLayout.RESIZE_MODE_FIT`; it may letterbox instead of stretching/cropping.
- Android large-screen/DPAD is an initial layout consideration, not certified Android TV compatibility.
- Keep UI focus order logical. Add tested DPAD navigation, Compose focus groups, and landscape window insets before TV release.

## Threat model and deferred work

- Stremio add-on URLs are user-controlled. Require HTTPS, cap JSON response sizes and timeouts. Production should add host allow-list / redirect validation, per-provider rate limits and certificate pinning policy where appropriate.
- No arbitrary CloudStream `.cs3` DEX/JAR execution in-process until sandboxing, signature prompts, capabilities and Android background execution limits are reviewed. Android classloading is not a safe default.
- Nuvio account auth requires documented endpoints, scoped tokens and secure refresh handling. Don't embed third-party credentials or hardcode undocumented keys.
- Media3 is not a universal transcoder; no guarantee a 700 kbps budget can be achieved from a single 1080p source.
- Secrets stored in Android Keystore cannot be automatically migrated from the Windows installation.
