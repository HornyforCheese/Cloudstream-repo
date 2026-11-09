# CloudStream binary compatibility audit (Task 2)

Upstream: https://github.com/recloudstream/cloudstream (branch master, inspected 2026-11-09)

## Current result: NOT BINARY COMPATIBLE

The Android application does not package the upstream CloudStream runtime library. Its existing `CloudStreamHost` only checks for class names and loads DEX metadata. A successfully downloaded `.cs3` is **not** an executable provider.

## Required ABI and upstream locations

| Contract | Upstream source | Mutiny Media |
| --- | --- | --- |
| `com.lagradost.cloudstream3.plugins.BasePlugin` | `library/src/commonMain/kotlin/com/lagradost/cloudstream3/plugins/BasePlugin.kt` | Missing |
| `com.lagradost.cloudstream3.MainAPI` | `library/src/commonMain/kotlin/com/lagradost/cloudstream3/MainAPI.kt` | Missing |
| `com.lagradost.cloudstream3.APIHolder` | `library/src/commonMain/kotlin/com/lagradost/cloudstream3/MainAPI.kt` | Missing |
| `com.lagradost.cloudstream3.utils.ExtractorApi` | `library/src/commonMain/kotlin/com/lagradost/cloudstream3/utils/ExtractorApi.kt` | Missing |
| Plugin lifecycle and class loading | `app/src/main/java/com/lagradost/cloudstream3/plugins/PluginManager.kt` | Metadata inspection only |
| `SearchResponse`, `SearchResponseList`, load and episode models | `library/src/commonMain/kotlin/com/lagradost/cloudstream3/MainAPI.kt` | Missing |
| Networking and HTML parsing | CloudStream library's NiceHTTP, Jsoup, Ksoup and other dependencies | Missing |
| Jackson/Kotlinx JSON and crypto helpers | CloudStream library dependency graph | Missing |

Upstream `BasePlugin.registerMainAPI` assigns `sourcePlugin`, inserts the provider into `APIHolder.allProviders`, and calls `APIHolder.addPluginMapping`. `registerExtractorAPI` inserts into `extractorApis`. A compatible host must implement the same binary descriptors, state and lifecycle semantics.

Upstream `MainAPI` supports `search(query: String)` and `search(query: String, page: Int)`, as well as main-page and load APIs. A simplified interface is not ABI compatible.

## Implementation requirements

1. Choose a **pinned upstream commit** and import its full compatible library and transitive dependencies, rather than reimplementing only a handful of interfaces.
2. Preserve upstream JVM descriptors and Kotlin-generated entry points. Verify using `javap` and an extension compiled against the same upstream commit.
3. Integrate CloudStream host initialization and `app` HTTP client; ensure extractors and serialization work.
4. Load the actual `.cs3` plugin, invoke `load()`, register providers, execute `search()`, then unload with `beforeUnload()` and remove provider/extractor mappings.
5. Validate a real provider search on Android; isolate errors and never mark a metadata-only installation as an active provider.

## Licensing

Upstream CloudStream declares **GNU GPL v3** in https://github.com/recloudstream/cloudstream/blob/master/LICENSE. Importing or adapting its code requires appropriate license compliance, copyright notices, corresponding source availability and consideration of the combined work's distribution obligations. This audit does not import upstream code or change this project's license.

## Verification

Run `./gradlew testDebugUnitTest assembleDebug` and Android instrumentation tests. Until executable provider registration and real Android searches pass, Task 2 must remain **incomplete**.
