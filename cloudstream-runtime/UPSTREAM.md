# CloudStream runtime provenance

Copyright belongs to the CloudStream contributors. Distributed under the included
GPL-3.0 license. Source: https://github.com/recloudstream/cloudstream
Pinned tag v4.5.4, commit 91652da9203cd566648f1eaf27cac4e5f12c7a61.

The complete library commonMain and androidMain sources are included. Adaptations:
Android-only compilation replaces expect declarations with the corresponding actual
implementations (actual keywords removed); WebViewResolver default arguments are
transferred from expect to the Android implementation. BuildConfig is supplied
without optional third-party metadata keys. Gradle uses the application's toolchain.
Android Plugin and its action API/payloads are copied from the Android app, with
Android activity dispatch adapted to Mutiny. No MainAPI response models,
constructors, networking interfaces or extractor signatures are approximated.

Current upstream master was also inspected during implementation. It introduces a
newer multiplatform runtime and dependencies (Kotlin 2.4, serialization, Ktor, Ksoup,
cryptography). This version intentionally does not claim that ABI. New extensions
using those symbols require a future runtime baseline update. Package manifests do
not contain a reliable universal API version, so structural checks alone cannot
establish compatibility; actual load and provider calls must also succeed.

The original Android Plugin action registration method is preserved.
VideoClickAction, its holder and action payload models (UiText, ResultEpisode,
LinkLoadingResult, SubtitleData) are copied from the pinned Android app. The action
holder starts with extensions only; upstream built-in external players are not
included. VideoClickAction dispatches Android UI launches through PluginActivityBridge
instead of CloudStream's own Activity classes. Registered actions are retained and
cleaned up; Mutiny does not yet offer an action picker. Extensions depending on other
CloudStream-specific screens, resources or app-only helpers remain unsupported.
WebView TLS failures cancel, WebView debugging is disabled, API error logs omit
exception messages and stack traces, registration logs omit provider URLs, and extractor/WebView debug prints of
URLs, response bodies and encryption material are removed.
These behavioral changes preserve the public provider binary interface.
