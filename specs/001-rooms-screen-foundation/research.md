# Research: Rooms Screen Foundation

**Feature**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md) | **Date**: 2026-10-02

All versions below were looked up on 2026-10-02 from Maven Central, Google Maven and
services.gradle.org (latest **stable**, pre-releases excluded). The implementing session MUST
re-check them before writing `gradle/libs.versions.toml` and take a newer stable patch if one
exists (Constitution VII). It MUST NOT take a newer major/minor without noting it in this file.

## R1. Toolchain versions

| Component | Version | Notes |
|---|---|---|
| Gradle wrapper | 9.8.0 | current release |
| Kotlin (KGP, compose compiler plugin, serialization plugin) | 2.4.20 | |
| Android Gradle Plugin | 9.4.1 | see R2 for the layout consequence |
| Compose Multiplatform plugin + runtime/foundation/ui | 1.12.1 | |
| `org.jetbrains.compose.material3:material3` | 1.9.0 | latest stable; CMP 1.12.1 itself pairs with 1.12.0-alpha03. If 1.9.0 fails to resolve or link against CMP 1.12.1, use foundation-only components instead of moving to an alpha (see R9) |
| `org.jetbrains.androidx.lifecycle` (viewmodel-compose, runtime-compose) | 2.11.0 | matches CMP 1.12.1 release notes |
| `org.jetbrains.androidx.navigation3` (navigation3-ui) | 1.1.2 | stable; navigation 2.x for CMP is only at 2.10.0-beta01 for this CMP version (see R7) |
| Ktor client (core, content-negotiation, serialization-kotlinx-json, okhttp, darwin, mock) | 3.6.0 | |
| kotlinx-serialization-json | 1.11.0 | |
| kotlinx-coroutines (core, test) | 1.11.0 | |
| `androidx.datastore:datastore-preferences-core` | 1.2.1 | multiplatform |
| `androidx.activity:activity-compose` | 1.13.0 | androidApp only |
| OpenAPI Generator Gradle plugin | 7.14.0 | |
| JDK | 17+ | required by AGP 9 |

**Decision**: pin these in `gradle/libs.versions.toml`.
**Rationale**: CLAUDE.md requires current stable versions, looked up, not recalled.
**Alternatives considered**: Kotlin 2.5.0-Beta1, CMP 1.13.0-alpha01, AGP 9.5.0-alpha08 were rejected
because they are pre-releases.

## R2. Project layout under AGP 9

**Finding**: AGP 9 makes `com.android.application` / `com.android.library` incompatible with
`org.jetbrains.kotlin.multiplatform` in the same module
([KMP AGP 9 migration guide](https://kotlinlang.org/docs/multiplatform/multiplatform-project-agp-9-migration.html)).
The current JetBrains wizard generates `composeApp` (KMP library with the
`com.android.kotlin.multiplatform.library` plugin, configured in `kotlin { android { … } }`) plus
a separate `androidApp` module that applies `com.android.application` and holds the Android entry
point. AGP 9 has Kotlin support built in, so `androidApp` does not apply `kotlin-android`.

**Decision** (user, 2026-10-02): use AGP 9 and the split layout:

- `composeApp/`: all shared code (domain, data, UI) in `commonMain`, platform drivers in
  `androidMain` / `iosMain`, tests in `commonTest`.
- `androidApp/`: `MainActivity`, `AndroidManifest.xml` (INTERNET permission, application id
  `ai.sonora.mobile`), `res/xml/network_security_config.xml`, launcher resources.
- Commands become:
  - `./gradlew :androidApp:assembleDebug` (APK)
  - `./gradlew :composeApp:testAndroidHostTest` (Android host tests. Enable host tests with
    `withHostTest {}` in `kotlin { android { } }`. Confirm the exact task name with
    `./gradlew :composeApp:tasks --all` and record it here and in CLAUDE.md)
  - `./gradlew :composeApp:allTests` (all KMP tests; iOS skipped off macOS)
- CLAUDE.md ("Project layout", "Commands") and Constitution IV (which lists
  `:composeApp:assembleDebug` / `:composeApp:testDebugUnitTest`) MUST be updated as the first
  implementation task (constitution PATCH bump to 1.1.1 via `/speckit-constitution`, CLAUDE.md
  synced in the same commit). This is tracked in the plan's Complexity Tracking.

**Alternatives considered**: pinning AGP 8.13.2 to keep a single module. Rejected by the user: it
starts the project on a superseded major version and needs the split anyway before AGP 10.

## R3. Generating the hub client

**Decision**: OpenAPI Generator Gradle plugin 7.14.0, generator `kotlin`, `library=multiplatform`,
run as a task in `composeApp` whose output (`build/generated/openapi/src/commonMain/kotlin`) is added
to `commonMain`'s Kotlin source dirs. Every Kotlin compile task depends on it. Only the
generated **sources** are used; the generated `build.gradle.kts` and wrapper are ignored
(`omitGradleWrapper=true`). Configuration:

| Option | Value | Why |
|---|---|---|
| `inputSpec` | `$rootDir/api/openapi.json` | single source of truth |
| `openapiNormalizer` | `FILTER=path:/api/v2` | generate only v2 operations (Constitution I). Supported by the FILTER normalizer (`path:` criterion) |
| `packageName` | `ai.sonora.mobile.hub.generated` | one package screens must never import |
| `serializationLibrary` | `kotlinx_serialization` | multiplatform |
| `dateLibrary` | `string` | timestamps are not used by this feature. Avoids pinning kotlinx-datetime to the generator's expected version |
| `enumUnknownDefaultCase` | `false` | the multiplatform library does not decode into it. Unknown enums are handled by the JSON config (R4) |
| `nonPublicApi` | `true` | generated types are `internal` to `composeApp` |
| `generateApiTests` / `generateModelTests` / docs | `false` | |

The multiplatform template targets Ktor 3.x (`ApiClient(baseUrl, httpClient)` constructor), so it
compiles against Ktor 3.6.0. The generator produces one API class per tag: `OutputsApi`,
`GroupsApi`, `RoutesApi`, `InputsApi`, `MasterMuteApi`, `ExtensionsApi`.

**Risk**: `api/openapi.json` is OpenAPI 3.1 (`type: ["string","null"]`). Generator 3.1 support is
marked beta. If a model fails to generate or compile, first try `openapiNormalizer`
`SIMPLIFY_ONEOF_ANYOF=true` / `REF_AS_PARENT_IN_ALLOF=true`, then
`LOOSE_NULL_DEFINITIONS`. Hand-editing generated code is forbidden (Constitution I). If nothing
works, stop and ask.

**Alternatives considered**: hand-written Ktor calls (forbidden by Constitution I),
Fabrikt / other generators (no multiplatform Ktor output).

**Enforcing "screens never touch generated types"**: a Gradle `verifyLayering` task in `composeApp`
(wired into `check`) fails if any file under `ui/` or `domain/` contains
`ai.sonora.mobile.hub.generated`. This is cheaper than a separate module and keeps the rule
mechanical.

## R4. JSON tolerance for a newer hub

All schema properties are optional in `openapi.json`, so generated models have nullable
properties defaulting to `null`. The app builds the `HttpClient` itself and passes it to the
generated API classes, with:

```kotlin
Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false; isLenient = true }
```

`coerceInputValues = true` turns an unknown enum value (e.g. a new route `status`) into the
property's default (`null`), so one unrecognised value never fails the whole response. The
repository maps `null` enums to explicit `Unknown` domain values (Constitution V). A `MockEngine`
test feeds an unknown `status` and an unknown field to prove it.

## R5. Timeouts, polling and foreground detection

- **Timeouts**: Ktor `HttpTimeout` with `requestTimeoutMillis = 3000`,
  `connectTimeoutMillis = 3000`, `socketTimeoutMillis = 3000` (FR-005).
- **Engine**: OkHttp on Android (`ktor-client-okhttp`), Darwin on iOS (`ktor-client-darwin`, stub).
  Provided by an `expect fun httpEngine(): HttpClientEngineFactory<*>`.
- **Refresh**: one refresh = the five GETs (`outputs`, `groups`, `routes`, `inputs`,
  `master-mute`) run concurrently inside `coroutineScope`. All five must succeed for the
  snapshot to count, because a partial snapshot would misplace rooms.
- **Loop**: `while (isActive) { refreshOnce(); withTimeoutOrNull(2500) { refreshNow.receive() } }`.
  The loop is the **only** caller of `refreshOnce()`. Sequential by construction, so a slow refresh
  delays the next one instead of overlapping (FR-005 "polls never pile up").
- **Refresh after an action**: "trigger a refresh" everywhere (R10, data-model.md Transitions)
  means `refreshNow.trySend(Unit)` on a `Channel<Unit>(Channel.CONFLATED)`. It ends the loop's
  current wait early; it never starts a refresh itself. If a refresh is running, the signal is
  picked up right after it, so at most one extra refresh follows. If the loop is stopped
  (background), nothing runs; the immediate refresh on return covers it (SC-006). A signal left
  in the channel while stopped is drained when the loop starts, since that start refreshes anyway.
- **Address change**: cancels the loop (and any in-flight refresh against the old address) and
  starts a new one against the new repository, which refreshes immediately.
- **Foreground only**: the loop runs inside `repeatOnLifecycle(Lifecycle.State.STARTED)` on the
  Compose `LocalLifecycleOwner` (JetBrains lifecycle-runtime-compose, multiplatform). Leaving
  STARTED cancels the loop and any in-flight refresh (SC-006). Returning restarts it with an
  immediate refresh. The lifecycle owner is the Rooms screen's, so polling also pauses while
  another screen (Settings, a placeholder) is on top and resumes with an immediate refresh when
  Rooms shows again. Nothing else displays hub state in this feature, so FR-005 is met.

**Alternatives considered**: Android `ProcessLifecycleOwner` (Android-only, would leak into
`commonMain`), a ticker flow (harder to guarantee no overlap).

## R6. Hub address persistence and parsing

**Decision**: `androidx.datastore:datastore-preferences-core` (multiplatform). `commonMain`
declares a `HubAddressStore` interface with `val address: Flow<HubAddress?>` and
`suspend fun save(address: HubAddress)`. `DataStoreHubAddressStore` lives in `commonMain`, and
only the file path comes from platforms: Android `context.filesDir`, iOS `NSDocumentDirectory`
in the stub. Tests use an in-memory fake.

**Rationale**: AndroidX/JetBrains first (Constitution VII). DataStore is multiplatform and
coroutine-native.
**Alternatives considered**: `multiplatform-settings` (third-party, would need justification),
`SharedPreferences` (Android-only).

**Address normalisation** (FR-002), one pure function `HubAddress.parse(input): Result`:

1. Trim. Empty, or containing whitespace, → `Invalid("Enter the hub's address, e.g. multiroom.lan")`
   or `Invalid("The address can't contain spaces")`.
2. No scheme → prefix `http://`. A scheme other than `http`/`https` → invalid.
3. Parse with Ktor `Url`/`URLBuilder`. Empty host → invalid. A query or fragment → invalid
   ("Enter just the address, e.g. multiroom.lan:8080").
4. Without an explicit port: `http` → port 8080, `https` → port 8443 (spec FR-002, clarified
   2026-10-02). An explicit port (including 80 and 443) is always kept.
5. Trailing `/` removed. A non-root path is kept as a base path prefix.
6. Stored value = the normalised URL string, e.g. `http://multiroom.lan:8080`.

Covered by `HubAddressTest`.

## R7. Navigation

**Decision**: Navigation 3 for Compose Multiplatform (`org.jetbrains.androidx.navigation3:navigation3-ui`
1.1.2). The back stack is an app-owned `SnapshotStateList<Destination>` with destinations
`Rooms`, `Sources`, `Settings` (top level, bottom bar) and `NowPlaying(routeId)`,
`StartPlayback(targetId?)` (placeholders). Bottom-bar taps replace the stack root. Back pops.
System back on Android works through `NavDisplay`'s back handling.

**Rationale**: stable for CMP 1.12.1. The state model is a plain list, easy to reason about and test.
**Alternatives considered**: `org.jetbrains.androidx.navigation:navigation-compose` (only beta for
this CMP line), a hand-rolled `when(screen)` (would need its own back handling per platform).

## R8. State management

**Decision**: JetBrains `lifecycle-viewmodel-compose` `ViewModel`s (`RoomsViewModel`,
`SettingsViewModel`) exposing `StateFlow<UiState>`. Dependencies are wired by hand in a small
`AppGraph`: no DI library, five or six objects in total. Platforms pass only the DataStore file
path (`AppGraph(dataStorePath: String)`), so `commonMain` never sees an Android `Context`.

**Rationale**: multiplatform `viewModelScope` survives configuration changes on Android. Manual
wiring avoids a dependency (Constitution VII).
**Alternatives considered**: Koin (unnecessary at this size).

## R9. UI toolkit pieces

- **Material3**: used only for `TextField` (Settings), `SnackbarHost` (action errors) and
  `Surface`/`Scaffold` scaffolding. Colours and typography come from our own `SonoraTheme`
  tokens (`SonoraColors`, `SonoraType`, `SonoraShapes` via `CompositionLocal`) and are mapped into a
  dark `ColorScheme` so material components pick them up. If material3 1.9.0 does not work with
  CMP 1.12.1, use `BasicTextField` plus a hand-made snackbar instead of an alpha artifact.
- **Icons**: the design's inline SVG paths (speaker, muted speaker, pause, play, stop, plus, rooms,
  sources, settings, group) are rebuilt as `ImageVector`s in `ui/theme/Icons.kt`. No
  `material-icons-extended` (deprecated, large).
- **Fonts**: `Res.font.sora` and `Res.font.dm_sans` (already committed, variable fonts) through
  `Font(resource, weight = …)` for the weights the design uses (400/500/600/700).
- **Volume pill**: a custom composable built from `Box` + `pointerInput(detectHorizontalDragGestures
  + tap)`. It has a 44 dp height, a fully rounded `surfaceRaised` track and an `accentContainer`
  fill proportional to value, with a speaker icon on the left and the percentage on the right.
  There is no thumb and no thin track, so it never reads as a progress bar (FR-012). Accessibility:
  `semantics { progressBarRangeInfo; setProgress {…}; contentDescription = "<Room> volume" }`, so
  TalkBack can adjust it. When disabled (muted / stale) it drops `setProgress` and is announced
  as such.

## R10. Volume throttling and drag ownership

- One `VolumeController` per card key (route id) inside `RoomsViewModel`:
  - `onDragStart`: snapshot the base volumes. For a group, every known member's volume at drag start
    (FR-013b).
  - `onDrag(value)`: set the local override (UI shows it immediately; refreshes don't touch it,
    FR-014) and push into a `MutableStateFlow<Int>`. A collector sends at most once per 250 ms,
    always the latest value (≤ 4/s).
  - `onDragEnd(value)`: send the final value immediately (cancels the pending throttle tick), wait
    for it to complete, then clear the override and trigger a refresh.
- Single room: `PUT /outputs/{id}/volume`. Group: compute member targets with
  `GroupVolume.scale(base, newValue)` and send one `PUT /outputs/{memberId}/volume` per member
  whose target differs from the **last value sent to it in this drag** (the drag-start base if
  nothing was sent yet), concurrently. Comparing with the base alone would skip the final send
  after dragging down and back to the start, leaving the hub at the lowered values. The
  throttle limits these batches to ≤ 4/s. **`PUT /groups/{id}/volume` is never called**
  (FR-013c). The repository does not even expose it.
- Cards with `volumeAdjustable = false` (unlisted room/group, group without known members) ignore
  drags (FR-014a).
- Rounding (FR-013b): `floor(v * new / top + 0.5)` clamped to 0..100. With `top == 0`, every
  member gets `new`. Integer arithmetic only, so all platforms give the same result.

## R11. Error presentation

The binding definitions are in [contracts/hub-repository.md](contracts/hub-repository.md)
("Interface", "Error mapping", "User-facing messages"); this section only records the decision.

- `HubResult<T>` = `Ok(T)` | `Err(HubError)`, where `HubError` is one of `Unreachable`
  (connect/timeout/IO), `Rejected(status, problemType)` (`problemType` from the RFC 7807 body
  when it decodes into the generated `ErrorResponse`), `Unexpected` (bad JSON, other).
- Messages are written in one function `actionErrorMessage(action, target, error)` with the exact
  texts from the contract table, e.g. "Couldn't stop Downstairs. Can't reach the hub." and
  "Kitchen is no longer on the hub.". The problem's `title`/`detail` never enters `HubError`, so
  it can't reach the UI (Constitution V).
- Shown through a snackbar, one at a time.

## R12. Test tooling

- `kotlin("test")`, `kotlinx-coroutines-test` (`runTest`, virtual time for polling/throttle),
  `ktor-client-mock` (`MockEngine`) in `commonTest`.
- No Turbine: `StateFlow.value` assertions under `runTest` + `advanceTimeBy` are enough.
- Fixtures: JSON strings in `commonTest` shaped by `openapi.json` (copied example values), including
  an RFC 7807 body, an unknown enum value and an unknown field.
