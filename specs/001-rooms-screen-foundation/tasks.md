---

description: "Task list for 001 Rooms Screen Foundation"
---

# Tasks: Rooms Screen Foundation

**Input**: Design documents from `specs/001-rooms-screen-foundation/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md),
[data-model.md](data-model.md), [contracts/hub-repository.md](contracts/hub-repository.md),
[contracts/rooms-ui.md](contracts/rooms-ui.md), [quickstart.md](quickstart.md)

**Tests**: REQUIRED. Constitution IV (test-first for logic, `MockEngine` repository tests) and spec
FR-013d. Every test task comes before its implementation task and MUST be seen failing first.

**Organization**: grouped by user story (spec priorities: US1 P1, US2 P1, US3 P2, US4 P3).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on an unfinished task)
- **[Story]**: US1–US4 from spec.md

## Path Conventions

- Shared code: `composeApp/src/commonMain/kotlin/ai/sonora/mobile/`
- Shared tests: `composeApp/src/commonTest/kotlin/ai/sonora/mobile/`
- Platform drivers: `composeApp/src/androidMain/kotlin/ai/sonora/mobile/`, `composeApp/src/iosMain/kotlin/ai/sonora/mobile/`
- Android app module: `androidApp/`
- Generated client (never committed or edited): `composeApp/build/generated/openapi/`, package `ai.sonora.mobile.hub.generated`

## General rules for every task

- Nothing Android-only (AndroidX non-multiplatform, `android.*`, `java.*`) in `commonMain`/`commonTest`.
- `ui/` and `domain/` never import `ai.sonora.mobile.hub.generated` (enforced by T006).
- Colours/typography only through `SonoraTheme` tokens (T011).
- If a task needs a decision none of the design documents cover, stop and ask (CLAUDE.md "Workflow").
- Commit after each task or logical group with a Conventional Commit message that explains why.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: AGP 9 KMP project that builds an empty app and generates the hub client.

- [ ] T001 Update docs for the AGP 9 layout (research R2), in one commit. (a) Amend `.specify/memory/constitution.md` via `/speckit-constitution`: in Principle IV replace `./gradlew :composeApp:assembleDebug` with `./gradlew :androidApp:assembleDebug` and `:composeApp:testDebugUnitTest` with `:composeApp:testAndroidHostTest`, PATCH bump to 1.1.1, Last Amended = today. (b) In `CLAUDE.md` update "Project layout (target)" (add `androidApp/` holding MainActivity, AndroidManifest, network_security_config; `composeApp/src/androidMain` holds only platform drivers), "Commands" (`:androidApp:assembleDebug`, `:composeApp:testAndroidHostTest`, `:composeApp:allTests`) and the "Scope" step 6 command. Mark the host-test task name "to be confirmed by T009".
- [ ] T002 Create the Gradle skeleton: wrapper for Gradle 9.8.0 (`gradlew`, `gradlew.bat`, `gradle/wrapper/`). Add `settings.gradle.kts` with rootProject `sonora-mobile` and including `:composeApp`, `:androidApp`, with `google()`, `mavenCentral()`, `gradlePluginPortal()` repositories. Add root `build.gradle.kts` (all plugins `apply false`) and `gradle.properties` (`org.gradle.jvmargs=-Xmx4g`, `kotlin.code.style=official`, `android.useAndroidX=true`, `org.gradle.configuration-cache=true`).
- [ ] T003 Create `gradle/libs.versions.toml` with the versions from research R1, after re-checking each for a newer **stable** patch: Kotlin 2.4.20, AGP 9.4.1, Compose Multiplatform 1.12.1, compose material3 1.9.0, JetBrains lifecycle 2.11.0 (`lifecycle-viewmodel-compose`, `lifecycle-runtime-compose`), JetBrains navigation3-ui 1.1.2, Ktor 3.6.0 (`client-core`, `client-content-negotiation`, `serialization-kotlinx-json`, `client-okhttp`, `client-darwin`, `client-mock`), kotlinx-serialization-json 1.11.0, kotlinx-coroutines 1.11.0 (`core`, `test`), `androidx.datastore:datastore-preferences-core` 1.2.1, `androidx.activity:activity-compose` 1.13.0, OpenAPI Generator plugin 7.14.0. Plugin aliases: `kotlinMultiplatform`, `androidApplication`, `androidMultiplatformLibrary` (`com.android.kotlin.multiplatform.library`), `composeMultiplatform`, `composeCompiler`, `kotlinSerialization`, `openapiGenerator`. Record any version change in research.md R1.
- [ ] T004 Create `composeApp/build.gradle.kts`. Plugins: kotlinMultiplatform, androidMultiplatformLibrary, composeMultiplatform, composeCompiler, kotlinSerialization, openapiGenerator. `kotlin { android { namespace = "ai.sonora.mobile.shared"; compileSdk = <latest stable API>; minSdk = 26; withHostTest {} }; iosArm64(); iosSimulatorArm64() }` with an iOS framework `ComposeApp` declared but not built on Linux. Dependencies: commonMain gets compose runtime/foundation/ui/material3/components-resources, lifecycle viewmodel-compose + runtime-compose, navigation3-ui, ktor core/content-negotiation/serialization-json, kotlinx-serialization-json, coroutines-core, datastore-preferences-core. androidMain gets ktor-client-okhttp. iosMain gets ktor-client-darwin. commonTest gets kotlin("test"), coroutines-test, ktor-client-mock. Compose resources package `ai.sonora.mobile.resources` so the existing `composeResources/font/sora.ttf` and `dm_sans.ttf` resolve as `Res.font.sora` / `Res.font.dm_sans`.
- [ ] T005 Add client generation to `composeApp/build.gradle.kts` (research R3). Task `openApiGenerate`: `generatorName = "kotlin"`, `library = "multiplatform"`, `inputSpec = "$rootDir/api/openapi.json"`, `outputDir = layout.buildDirectory.dir("generated/openapi")`, `packageName = "ai.sonora.mobile.hub.generated"`, `openapiNormalizer = mapOf("FILTER" to "path:/api/v2")`, `configOptions`: `serializationLibrary=kotlinx_serialization`, `dateLibrary=string`, `enumUnknownDefaultCase=false`, `nonPublicApi=true`, `omitGradleWrapper=true`, `sourceFolder=src/commonMain/kotlin`. Set `generateApiTests=false`, `generateModelTests=false`, `generateApiDocumentation=false`, `generateModelDocumentation=false`. Add `build/generated/openapi/src/commonMain/kotlin` to `commonMain` kotlin srcDirs. Make every `KotlinCompilationTask` (and any `*SourcesJar`/metadata task that reads sources) depend on `openApiGenerate`. Run `./gradlew :composeApp:compileKotlinMetadata` (or the android compile task) and confirm `OutputsApi`, `GroupsApi`, `RoutesApi`, `InputsApi`, `MasterMuteApi` and models compile. Confirm no `/api/tts` or v1 operations are generated. On OpenAPI 3.1 generation problems follow research R3's fallback order (normalizers), never hand-edit output, and stop and ask if still failing.
- [ ] T006 Add a `verifyLayering` task to `composeApp/build.gradle.kts`, wired into `check`. It fails with the file name if any `.kt` file under `composeApp/src/commonMain/kotlin/ai/sonora/mobile/ui/` or `.../domain/` contains the text `ai.sonora.mobile.hub.generated`. Use configuration-cache-compatible inputs (`fileTree` as `@InputFiles`).
- [ ] T007 [P] Create the `androidApp` module. `androidApp/build.gradle.kts` uses plugins androidApplication, composeMultiplatform, composeCompiler and no `kotlin-android` (AGP 9 built-in Kotlin), with `namespace`/`applicationId = "ai.sonora.mobile"`, minSdk 26, compileSdk/targetSdk = the same latest stable API as T004, and dependencies `project(":composeApp")` + activity-compose. Add `androidApp/src/main/AndroidManifest.xml` with `<uses-permission android:name="android.permission.INTERNET"/>`, `android:networkSecurityConfig="@xml/network_security_config"`, `android:usesCleartextTraffic="true"` and a launcher `MainActivity`. Add `androidApp/src/main/res/xml/network_security_config.xml` with `<base-config cleartextTrafficPermitted="true"/>` (app-wide, because the address is user-entered). Add `androidApp/src/main/res/values/themes.xml` (NoActionBar, window background `#0E0F12`), `strings.xml` (`app_name` = "Sonora") and an adaptive launcher icon. Add `androidApp/src/main/kotlin/ai/sonora/mobile/android/MainActivity.kt` with `enableEdgeToEdge()` + `setContent { Text("Sonora") }` (replaced in T018).
- [ ] T008 [P] Create the iOS stub. `composeApp/src/iosMain/kotlin/ai/sonora/mobile/MainViewController.kt` has `fun MainViewController() = ComposeUIViewController { }` (filled in T018). Add a minimal `iosApp/` Xcode project stub (`iosApp/iosApp/ContentView.swift`, `iosApp/iosApp/iOSApp.swift`, `iosApp/README.md` saying "not built; iOS builds will run on a GitHub Actions macOS runner").
- [ ] T009 Run `./gradlew :androidApp:assembleDebug` and `./gradlew :composeApp:tasks --all`. Find the real Android host-test task name (expected `testAndroidHostTest`). If it differs, replace it everywhere: `CLAUDE.md`, `.specify/memory/constitution.md` (same PATCH), research.md R2, quickstart.md. Confirm `git status` shows nothing under `composeApp/build/`.

**Checkpoint**: an empty APK builds, the client is generated at build time, and layering is enforced.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: theme, domain types, repository boundary, HTTP client and app shell that every story uses.

**⚠️ CRITICAL**: no user story work can begin until this phase is complete.

- [ ] T010 [P] Create the domain types in `composeApp/src/commonMain/kotlin/ai/sonora/mobile/domain/Models.kt`, exactly per data-model.md: `Room(id, name, volume: Int, muted, enabled, available)`, `Group(id, name, memberIds: List<String>, muted, enabled)`, `enum SourceOrigin { Configured, Runtime }`, `enum SourceKind { Stream, LineIn, File, Link }`, `Source(id, name, uri: String?, origin, pauseable, enabled, kind)`, `sealed Target { Room(id); Group(id) }`, `enum RouteStatus { Starting, Active, Stopping, Stopped, Failed, Unknown }`, `Route(id, inputId, target, status, paused, pauseable, transferable)`, `HubSnapshot(rooms, groups, routes, sources, masterMuted)`. Plain immutable data classes with no logic.
- [ ] T011 [P] Create the theme in `composeApp/src/commonMain/kotlin/ai/sonora/mobile/ui/theme/`. `Tokens.kt` holds `SonoraColors` with every CLAUDE.md token: background `#0E0F12`, surface `#17191E`, surfaceRaised `#22252B`, outline `#2C3038`, text `#F2F1EE`, textMuted `#9A9DA6`, accent `#F2A541`, onAccent `#1A1206`, accentContainer `#4A3412`, selectedBg `#1F1A12`, selectedOutline `#6B4C1F`, warningText `#F2D3A4`, and kind tile bg/icon pairs (stream `#3A2A12`/`#F2A541`, line-in `#12302E`/`#5FD3C4`, file `#261F3A`/`#B49CFF`, link `#3A1E14`/`#FF8A5B`). `Type.kt` holds `SonoraType`: Sora for screenTitle 30 sp and cardTitle 17 sp, DM Sans for 16/15/14/13/12 sp styles, loaded with `Font(Res.font.sora, weight)` / `Font(Res.font.dm_sans, weight)` for weights 400/500/600/700. `Shapes.kt` holds card radius 22 dp, tile 12–14 dp and pill `CircleShape`, plus `minTouchTarget = 44.dp`. `Theme.kt` holds `SonoraTheme {}` providing these via `CompositionLocal` and a dark material3 `ColorScheme` mapped from the tokens. Check exact sizes against `design/screens/Main.dc.html` inline styles.
- [ ] T012 [P] Create the icons in `composeApp/src/commonMain/kotlin/ai/sonora/mobile/ui/theme/Icons.kt`: `ImageVector`s rebuilt from the inline SVG paths in `design/screens/Main.dc.html` and `NowPlaying.dc.html` (speaker, muted speaker, pause, play, stop, plus, rooms, sources, settings, group, kind icons stream/line-in/file/link, back arrow). Use a 24×24 viewport, stroke width 1.8, round caps/joins. No icon library dependency.
- [ ] T013 [P] Create the repository boundary in `composeApp/src/commonMain/kotlin/ai/sonora/mobile/data/HubRepository.kt`, exactly as in contracts/hub-repository.md "Interface": `HubRepository` (snapshot, setRoomVolume, stopRoute, setRoutePaused, setMasterMute), `HubResult` (`Ok`/`Err`), `HubError` (`Unreachable`, `Rejected(status, problemType)`, `Unexpected`) and `fun interface HubRepositoryFactory`. There is deliberately no group-volume method.
- [ ] T014 [P] Create `composeApp/src/commonMain/kotlin/ai/sonora/mobile/domain/HubAddress.kt` with `@JvmInline value class HubAddress(val baseUrl: String)` (parse is added in T022). Create `composeApp/src/commonMain/kotlin/ai/sonora/mobile/data/HubAddressStore.kt` with `interface HubAddressStore { val address: Flow<HubAddress?>; suspend fun save(address: HubAddress) }`, plus `InMemoryHubAddressStore` in `composeApp/src/commonTest/kotlin/ai/sonora/mobile/data/InMemoryHubAddressStore.kt`.
- [ ] T015 Write `composeApp/src/commonTest/kotlin/ai/sonora/mobile/data/HttpClientsTest.kt` (MockEngine), failing first. It covers: a thrown `IOException`-family error / `ConnectTimeoutException` / `HttpRequestTimeoutException` maps to `HubError.Unreachable`; HTTP 404 with an RFC 7807 body (`{"type":"urn:multiroom:error:not-found","title":"Not Found","status":404,"detail":"x"}`) maps to `Rejected(404, "urn:multiroom:error:not-found")`; HTTP 500 with a non-JSON body maps to `Rejected(500, null)`; a 200 with an undecodable body maps to `Unexpected`; `CancellationException` is rethrown. Test through a small `suspend fun <T> hubCall(block: suspend () -> HttpResponse-or-T): HubResult<T>` helper.
- [ ] T016 Implement `composeApp/src/commonMain/kotlin/ai/sonora/mobile/data/HttpClients.kt` to make T015 pass. Add `expect fun httpEngine(): HttpClientEngineFactory<*>` with actuals in `composeApp/src/androidMain/kotlin/ai/sonora/mobile/data/HttpEngine.android.kt` (OkHttp) and `composeApp/src/iosMain/kotlin/ai/sonora/mobile/data/HttpEngine.ios.kt` (Darwin). Add `fun createHubHttpClient(engine: HttpClientEngine)` with `expectSuccess = false`, `HttpTimeout` request/connect/socket = 3000 ms, `ContentNegotiation` with `HubJson = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false; isLenient = true }`, and no retries. Add the `hubCall` error mapping per contracts/hub-repository.md "Error mapping". Problem `title`/`detail` are never put into `HubError`.
- [ ] T017 Create the navigation skeleton in `composeApp/src/commonMain/kotlin/ai/sonora/mobile/ui/nav/Destinations.kt`: `sealed interface Destination { Rooms; Sources; Settings; NowPlaying(routeId: String); StartPlayback(targetId: String?) }`, plus a `class AppBackStack` wrapping a `SnapshotStateList<Destination>` that starts at `[Rooms]` with `push`, `pop(): Boolean` and `selectTab(top)`. `selectTab` replaces the stack with `[Rooms]` for Rooms, or `[Rooms, top]` for Sources/Settings. Add `composeApp/src/commonMain/kotlin/ai/sonora/mobile/ui/nav/AppNavigation.kt` with a Navigation 3 `NavDisplay` over the back stack, rendering a temporary `Text` per destination (real screens come from the stories).
- [ ] T018 Create `composeApp/src/commonMain/kotlin/ai/sonora/mobile/AppGraph.kt` (manual wiring: `HubAddressStore`, `HubRepositoryFactory`, view-model factories; constructor takes the platform store) and `composeApp/src/commonMain/kotlin/ai/sonora/mobile/App.kt` (`@Composable fun App(graph: AppGraph)` = `SonoraTheme { AppNavigation(...) }` on background `#0E0F12`). Wire `androidApp/.../MainActivity.kt` to `setContent { App(remember { AppGraph(...) }) }`, temporarily using an in-memory store until T023, and `MainViewController.kt` likewise. `./gradlew :androidApp:assembleDebug` passes.

**Checkpoint**: foundation ready. The app launches into an empty themed shell.

---

## Phase 3: User Story 1 - Connect the app to my hub (Priority: P1) 🎯 MVP

**Goal**: the user enters the hub address in Settings, it persists across restarts, and Rooms knows whether an address exists.

**Independent Test**: fresh install shows "Set your hub address" with no network request. Saving `multiroom.lan` stores `http://multiroom.lan:8080`, and after restart Settings is pre-filled and Rooms leaves the no-address state (US1 scenarios 1–4).

### Tests for User Story 1 ⚠️ write first, see them fail

- [ ] T019 [P] [US1] Write `composeApp/src/commonTest/kotlin/ai/sonora/mobile/domain/HubAddressTest.kt` for research R6 / FR-002: `"multiroom.lan"` → `http://multiroom.lan:8080`; `"multiroom.lan:9000"` → `http://multiroom.lan:9000`; `"http://multiroom.lan"` → `http://multiroom.lan:8080`; `"http://multiroom.lan:80"` → `http://multiroom.lan:80` (explicit 80 kept); `"https://hub.example"` → `https://hub.example` (used as typed, no port added); `"192.168.1.20"` → `http://192.168.1.20:8080`; `"  multiroom.lan/  "` → trimmed, trailing slash removed; `"http://hub:8080/base/"` → `http://hub:8080/base`. Invalid cases: `""` → "Enter the hub's address, e.g. multiroom.lan"; `"multiroom lan"` → "The address can't contain spaces"; `"ftp://hub"` → invalid; `"http://"` (empty host) → invalid; `"hub?x=1"` / `"hub#a"` → "Enter just the address, e.g. multiroom.lan:8080".
- [ ] T020 [P] [US1] Write `composeApp/src/commonTest/kotlin/ai/sonora/mobile/ui/settings/SettingsViewModelTest.kt` (InMemoryHubAddressStore, `runTest`). Cases: the initial text equals the saved address or is empty; saving valid input stores the normalised address, replaces the text with it and sets `saved = true`; saving invalid input stores nothing and sets `error` to the parse message; editing the text clears `error` and `saved`.
- [ ] T021 [P] [US1] Write `composeApp/src/commonTest/kotlin/ai/sonora/mobile/ui/rooms/RoomsViewModelAddressTest.kt` (fake `HubRepositoryFactory` that counts `create` and calls). With no saved address the state is `RoomsUiState.NoAddress` and zero repository calls happen, even after advancing virtual time by 30 s (FR-003). After `store.save(...)` the state becomes `Connected(address, connection = Loading)` and the factory is asked for that address.

### Implementation for User Story 1

- [ ] T022 [US1] Implement `HubAddress.parse(input: String): ParseResult` (`Valid(HubAddress)` / `Invalid(message)`) in `composeApp/src/commonMain/kotlin/ai/sonora/mobile/domain/HubAddress.kt` per research R6 steps 1–6, using Ktor `URLBuilder`/`Url` (multiplatform), until T019 passes.
- [ ] T023 [US1] Implement `DataStoreHubAddressStore` in `composeApp/src/commonMain/kotlin/ai/sonora/mobile/data/HubAddressStore.kt`, using `PreferenceDataStoreFactory.createWithPath { dataStorePath().toPath() }` and the single key `stringPreferencesKey("hub_address")`. Add `expect fun dataStorePath(): String`, with the Android actual in `composeApp/src/androidMain/kotlin/ai/sonora/mobile/data/DataStorePath.android.kt` (needs a `Context`: take it via an `initDataStorePath(context)` call from `MainActivity`, or pass the path into `AppGraph`; pick one and keep `commonMain` free of `Context`) and the iOS actual in `composeApp/src/iosMain/kotlin/ai/sonora/mobile/data/DataStorePath.ios.kt` (`NSDocumentDirectory` + `sonora.preferences_pb`). File name `sonora.preferences_pb`. Replace the in-memory store in `AppGraph`/`MainActivity` with this one.
- [ ] T024 [US1] Implement `composeApp/src/commonMain/kotlin/ai/sonora/mobile/ui/settings/SettingsViewModel.kt` (`SettingsUiState(text, savedAddress, error, saved)`, `onTextChange`, `onSave`) until T020 passes.
- [ ] T025 [US1] Implement `composeApp/src/commonMain/kotlin/ai/sonora/mobile/ui/settings/SettingsScreen.kt` per contracts/rooms-ui.md "Settings": title "Settings" (Sora 30 sp), material3 `TextField` (or `BasicTextField` fallback, research R9) labelled "Hub address" with placeholder "multiroom.lan:8080" and helper "The hub's name or IP on your home network", URI keyboard, IME action Done = save. "Save" button ≥ 44 dp in accent/onAccent, inline error under the field, "Saved" confirmation. Register it for `Destination.Settings` in `AppNavigation.kt`.
- [ ] T026 [US1] Create `composeApp/src/commonMain/kotlin/ai/sonora/mobile/ui/rooms/RoomsUiState.kt` (`NoAddress`, `Connected(address, connection: Loading|Live|Unreachable(lastSuccessAt), content: RoomsContent?, volumeOverrides, inFlight, message)` per data-model.md "View-model state"; `RoomsContent` is a placeholder type until T034). Create `composeApp/src/commonMain/kotlin/ai/sonora/mobile/ui/rooms/RoomsViewModel.kt` that observes `HubAddressStore.address` and creates a repository via the factory on each new address, until T021 passes.
- [ ] T027 [US1] Implement the "No hub set" state in `composeApp/src/commonMain/kotlin/ai/sonora/mobile/ui/rooms/RoomsScreen.kt` per contracts/rooms-ui.md: title "Rooms", "Set your hub address", "Rooms will appear here once the app knows where your hub is.", button "Open Settings" calling `backStack.selectTab(Settings)`. While `Connected`, show the "Connecting to <address>…" text (replaced in US2). Register it for `Destination.Rooms`.

**Checkpoint**: US1 works on its own. The address persists, and Rooms reacts to its presence without contacting the hub before it exists.

---

## Phase 4: User Story 2 - See what is playing in every room (Priority: P1)

**Goal**: Rooms shows now-playing cards, Idle rows and the header from live hub data, polled every 2.5 s in the foreground only, with unreachable/stale handling.

**Independent Test**: with a hub holding one group route, one single-room route, one idle room and one disabled room, every card and row matches the hub. A change from another client appears within ~3 s, and backgrounding stops requests (US2 scenarios 1–8). In the cloud: T028–T031 pass.

### Tests for User Story 2 ⚠️ write first, see them fail

- [ ] T028 [P] [US2] Write `composeApp/src/commonTest/kotlin/ai/sonora/mobile/domain/SourceKindTest.kt` for `inferSourceKind(origin, uri)` (data-model.md "SourceKind"): Runtime + `http://x` → Link (origin wins); Configured + `http://x`, `HTTPS://x` → Stream; `file:///a.mp3`, `/music/a.flac`, `./a.mp3`, `../a`, `~/a`, `C:\a.mp3`, `C:/a.mp3`, `\\nas\a` → File; `alsa:hw:1`, `line-in`, `""`, `null` → LineIn.
- [ ] T029 [P] [US2] Write `composeApp/src/commonTest/kotlin/ai/sonora/mobile/domain/RoomsBuilderTest.kt` for `RoomsBuilder.build(snapshot)` against data-model.md "Algorithm", "CardStatus", "CardAction". Use the design sample: rooms Living Room 70, Kitchen 55, Office 40, Bedroom, Patio (disabled); group Downstairs = [living, kitchen]; route r1 Radio Paradise (http, not pauseable) → group Downstairs ACTIVE; route r2 Morning playlist (file, pauseable, paused) → Office. Expect 2 cards sorted [Downstairs, Office] and header 3 of 5. Downstairs: isGroup, memberNames [Living Room, Kitchen], volume 70, status LiveStream, action Stop. Office: status Paused, action Resume. Idle rows: [Bedroom NothingPlaying, Patio TurnedOff]. Living Room and Kitchen are not idle. Additional cases, one test each:
  - STOPPED route ignored (no card, room idle, not counted)
  - FAILED route → card with status Failed and action Stop, room occupied
  - Unknown status + pauseable → Pause disabled
  - Starting / Stopping + pauseable → Pause disabled
  - Active pauseable not paused → Pause enabled
  - Active non-pauseable line-in → Playing + Stop
  - route to unlisted group → title = group id, no members, volume 0, occupies nothing
  - group with one unlisted member → member skipped
  - route to unlisted room → title = room id
  - route with unlisted input → sourceName = inputId, kind LineIn
  - room in two groups, only one routed → occupied once and counted once
  - idle state precedence: disabled + unavailable → TurnedOff; unavailable only → NotConnected
  - single-room card with available=false → notConnected = true; disabled room with a route keeps its card (not "Off")
  - muted: room muted → card muted; group muted → card muted; masterMuted → every card muted
  - sorting case-insensitive (`"bedroom"` before `"Kitchen"`), id tiebreak for equal names
  - no rooms → `RoomsContent.NoRooms`
- [ ] T030 [P] [US2] Write `composeApp/src/commonTest/kotlin/ai/sonora/mobile/data/KtorHubRepositorySnapshotTest.kt` (MockEngine + fixtures in `composeApp/src/commonTest/kotlin/ai/sonora/mobile/data/Fixtures.kt` shaped by `api/openapi.json` 0.1.20). It covers contract tests 1, 2, 3 and 6:
  - full decode to the expected `HubSnapshot`
  - unknown field ignored
  - `"status":"PAUSING"` → `RouteStatus.Unknown`
  - `"targetType":"ZONE"` → route dropped
  - `"source":"DYNAMIC"` → `SourceOrigin.Configured`
  - missing optional fields take the data-model.md defaults: volume null → 0, volume 150 → 100, enabled null → true, available null → true, displayName null → id, outputIds null → empty, blank ids dropped
  - one of the five GETs failing (IO or 500) → `Err`
  - requests go to exactly `GET {base}/api/v2/outputs`, `/groups`, `/routes`, `/inputs`, `/master-mute`
- [ ] T031 [P] [US2] Write `composeApp/src/commonTest/kotlin/ai/sonora/mobile/ui/rooms/RoomsViewModelPollingTest.kt` (fake repository with a controllable delay/result, `runTest` virtual time). Cases:
  - after an address exists, the first refresh is immediate, then one every 2500 ms
  - a refresh taking 4 s does not overlap the next one: max 1 concurrent call (FR-005)
  - a 3 s timeout surfaces as Unreachable via the repository result
  - first failure with no content → `Unreachable(null)`, content null
  - failure after success → `Unreachable(lastSuccessAt)`, content kept
  - success after Unreachable → `Live`
  - `stopPolling()` (background) → zero calls while stopped; `startPolling()` → immediate refresh (SC-006)
  - an address change restarts polling against the new repository

### Implementation for User Story 2

- [ ] T032 [P] [US2] Implement `inferSourceKind(origin: SourceOrigin, uri: String?): SourceKind` in `composeApp/src/commonMain/kotlin/ai/sonora/mobile/domain/SourceKind.kt`, with KDoc stating it is the single place kind is decided (hub gap: no kind field), until T028 passes.
- [ ] T033 [US2] Implement `composeApp/src/commonMain/kotlin/ai/sonora/mobile/data/ApiMapping.kt` (generated `OutputResponse`/`GroupResponse`/`InputResponse`/`RouteResponse`/`MasterMuteResponse` → domain, applying every rule in the data-model.md tables, e.g. "volume clamped 0..100, null → 0", "enabled null → true", "null/unknown type → route dropped") and `snapshot()` in `composeApp/src/commonMain/kotlin/ai/sonora/mobile/data/KtorHubRepository.kt`. The five calls run concurrently in `coroutineScope` via generated `OutputsApi.listOutputs`, `GroupsApi.listGroups`, `RoutesApi.listRoutes`, `InputsApi.listInputs`, `MasterMuteApi.getMasterMute`, constructed with `(baseUrl, httpClient)` from T016, and the whole snapshot is `Err` if any call fails. Leave the action methods as `TODO()` for US3. Provide `KtorHubRepositoryFactory` in `AppGraph.kt`. Iterate until T030 passes.
- [ ] T034 [US2] Implement `composeApp/src/commonMain/kotlin/ai/sonora/mobile/domain/RoomsBuilder.kt`: `RoomsContent` (`NoRooms` | `Rooms(inUse, total, masterMuted, cards, idle)`), `NowPlayingCard(key, title, isGroup, memberNames, sourceName, kind, status: CardStatus, volume, memberVolumes: Map<String, Int>, muted, notConnected, action: CardAction, actionEnabled)`, `IdleRow(roomId, name, state: IdleState)`, `enum CardStatus { Playing, Paused, LiveStream, Starting, Stopping, Failed, Unknown }`, `enum CardAction { Stop, Pause, Resume }`, `enum IdleState { NothingPlaying, TurnedOff, NotConnected }`, and `build(snapshot)` following data-model.md steps 1–9, until T029 passes. Remove the placeholder `RoomsContent` from T026.
- [ ] T035 [US2] Extend `composeApp/src/commonMain/kotlin/ai/sonora/mobile/ui/rooms/RoomsViewModel.kt` with polling (research R5): `startPolling()`/`stopPolling()`, a loop `while (isActive) { refreshOnce(); delay(2500) }` in `viewModelScope`, connection transitions per data-model.md "Transitions" table, `content = RoomsBuilder.build(snapshot)` and `lastSuccessAt` from an injectable clock, until T031 passes.
- [ ] T036 [P] [US2] Implement `composeApp/src/commonMain/kotlin/ai/sonora/mobile/ui/rooms/StatusText.kt`: `fun statusLine(card): String` per data-model.md CardStatus texts. Kind labels are "Stream", "Line-in", "File", "Link". Single room = "<Kind> · Playing|Paused", "Live stream", "Starting…", "Stopping…", "Couldn't play", "Unknown", with " · Not connected" appended when `notConnected`. Group = members joined " + " then " · " + the status word ("Playing", "Paused", "Live stream", …). Add `composeApp/src/commonTest/kotlin/ai/sonora/mobile/ui/rooms/StatusTextTest.kt` covering "File · Paused" and "Living Room + Kitchen · Live stream".
- [ ] T037 [P] [US2] Implement a display-only `composeApp/src/commonMain/kotlin/ai/sonora/mobile/ui/rooms/VolumePill.kt` (research R9): 44 dp tall, fully rounded `surfaceRaised` track, `accentContainer` fill proportional to value, speaker icon left (muted-speaker when muted), "NN%" right with tabular numbers. No thumb and no thin track, so it never resembles a progress bar (FR-012). Parameters `value`, `muted`, `enabled`, `contentDescription`, plus no-op drag callbacks to be wired in US3.
- [ ] T038 [US2] Implement `composeApp/src/commonMain/kotlin/ai/sonora/mobile/ui/rooms/NowPlayingCard.kt` per contracts/rooms-ui.md "Now playing" and `design/screens/Main.dc.html`. Use a surface card (radius 22) with a kind tile in the kind colours, title in Sora 17 sp, a "Group" badge, source name, the status line (single line, ellipsis), `VolumePill`, and one 44 dp action button showing Stop/Pause/Resume per `card.action`. Labels: "Stop <title>" / "Pause <title>" / "Resume <title>" and the pill's "<title> volume". The button is disabled when `!actionEnabled`. Clicks are callbacks (no-op until US3/US4).
- [ ] T039 [P] [US2] Implement `composeApp/src/commonMain/kotlin/ai/sonora/mobile/ui/rooms/IdleRow.kt` per the contracts/rooms-ui.md "Idle" table. NothingPlaying shows "Nothing playing" and a play icon button labelled "Play something in <name>" (callback). TurnedOff is dimmed with "Turned off" and an "Off" label. NotConnected is dimmed with "Not connected" and a distinct icon. Dimming uses `textMuted`, keeping ≥ 4.5:1 contrast.
- [ ] T040 [US2] Complete `composeApp/src/commonMain/kotlin/ai/sonora/mobile/ui/rooms/RoomsScreen.kt` per contracts/rooms-ui.md "Screen states" and "Header":
  - Connecting, Can't reach ("Can't reach the hub", "Tried <address>. Retrying…", "Open Settings") and Stale (content dimmed + banner "Can't reach the hub · showing last known state"; controls disabled) states
  - No rooms state: "Your hub has no rooms configured."
  - header with "Rooms", "N of M rooms in use" ("room" when M == 1) and a master-mute icon button showing the hub state (labels "Mute all rooms"/"Unmute all rooms"; the toggle is wired in US3)
  - "Now playing" section of `NowPlayingCard`s (hidden when empty) and "Idle" section of `IdleRow`s plus a "Play something" button (hidden when empty), in a `LazyColumn`
  - polling bound to the lifecycle: `LaunchedEffect(lifecycleOwner) { lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.startPolling(); try { awaitCancellation() } finally { viewModel.stopPolling() } } }` with `LocalLifecycleOwner` from JetBrains lifecycle-runtime-compose
  - the displayed volume is `volumeOverrides[key] ?: card.volume`

**Checkpoint**: US1 + US2 make a working read-only remote. `./gradlew :androidApp:assembleDebug` and all tests are green.

---

## Phase 5: User Story 3 - Control playback from the Rooms screen (Priority: P2)

**Goal**: volume (single and balanced group), Pause/Resume, Stop and master mute work from Rooms, with throttling, drag ownership, in-flight protection and plain error messages.

**Independent Test**: each control changes the hub, and the screen confirms it on the next refresh (US3 scenarios 1–10). In the cloud: T041–T044 pass, and no request ever hits `/api/v2/groups/{id}/volume`.

### Tests for User Story 3 ⚠️ write first, see them fail

- [ ] T041 [P] [US3] Write `composeApp/src/commonTest/kotlin/ai/sonora/mobile/domain/GroupVolumeTest.kt` for `GroupVolume.scale(base: Map<String, Int>, newValue: Int): Map<String, Int>` (FR-013b, FR-013d, research R10), using the formula `floor(v * new / top + 0.5)` clamped 0..100, where top = max(base). Cases:
  - {a:70, b:35} → 35 gives {a:35, b:18} (17.5 rounds up)
  - {a:70, b:35} → 70 gives unchanged
  - {a:0, b:0} → 40 gives {a:40, b:40}
  - {a:50, b:25} → 100 gives {a:100, b:50}
  - {a:60, b:1} → 0 gives {a:0, b:0}
  - drag down then back up from the same base restores {a:70, b:35}
  - newValue outside 0..100 is clamped first
  - empty base → empty
- [ ] T042 [P] [US3] Write `composeApp/src/commonTest/kotlin/ai/sonora/mobile/data/KtorHubRepositoryActionsTest.kt` (MockEngine), contract test 4: `setRoomVolume("kitchen", 42)` → `PUT /api/v2/outputs/kitchen/volume` body `{"volume":42}`; `setRoomVolume(x, 130)` sends 100; `stopRoute("r1")` → `DELETE /api/v2/routes/r1` (204 → Ok); `setRoutePaused("r1", true)` → `PUT /api/v2/routes/r1/pause` `{"paused":true}`; `setMasterMute(true)` → `PUT /api/v2/master-mute` `{"muted":true}`. Contract test 5: a 404/422 RFC 7807 response → `Rejected(status, type)` for each action.
- [ ] T043 [P] [US3] Write `composeApp/src/commonTest/kotlin/ai/sonora/mobile/ui/MessagesTest.kt` for `actionErrorMessage(action, targetName, error)` with every cell of the contracts/hub-repository.md "User-facing messages" table, e.g. `(Stop, "Downstairs", Unreachable)` → "Couldn't stop Downstairs. Can't reach the hub."; `(Volume, "Kitchen", Rejected(404))` → "Kitchen is no longer on the hub."; `(MasterMute(on=false), _, Unexpected)` → "Couldn't unmute all rooms.". Assert no message contains the problem `title`/`detail`.
- [ ] T044 [P] [US3] Write `composeApp/src/commonTest/kotlin/ai/sonora/mobile/ui/rooms/RoomsViewModelControlsTest.kt` (fake repository recording calls with virtual timestamps). Cases:
  - **Drag ownership (FR-014, US3-2)**: during a drag a refresh with a different volume does not change the displayed value. After drag end and request completion the override clears and a refresh runs.
  - **Throttle**: 40 drag events over 2 s → at most 4 `setRoomVolume` calls per rolling second, the last sent value equals the latest, and drag end sends the final value once immediately.
  - **Group drag**: Downstairs {living:70, kitchen:35} dragged to 35 → `setRoomVolume(living,35)` + `setRoomVolume(kitchen,18)`. All-zero members dragged to 40 → both 40. Only members whose value changed are sent. The base is taken at drag start (down to 35 then up to 70 in one drag restores 70/35).
  - **Never group volume**: a MockEngine-backed run of the group drag fails if any request path matches `/api/v2/groups/.+/volume` (contract test 7).
  - **Muted (FR-014a)**: a drag on a muted card (room muted, group muted or master mute) is ignored with no calls.
  - **Stale (FR-004)**: with `Unreachable` + content, drag/Stop/Pause/Resume/master mute are ignored with no calls.
  - **In-flight (FR-019)**: tapping Stop twice while the first call is pending → one call, and the key sits in `inFlight` until completion.
  - **Pause/Resume/Stop/master mute** call the right repository methods with the right ids/values (Pause → paused=true, Resume → false, master mute toggles the current `masterMuted`).
  - **Failure (FR-018)**: an `Err(Unreachable)` result sets `message` to the T043 text, clears in-flight and triggers a refresh. `consumeMessage()` clears it.
  - **Disabled Pause**: an action on a card with `actionEnabled = false` is ignored.

### Implementation for User Story 3

- [ ] T045 [P] [US3] Implement `GroupVolume.scale` in `composeApp/src/commonMain/kotlin/ai/sonora/mobile/domain/GroupVolume.kt` (integer arithmetic only) until T041 passes.
- [ ] T046 [US3] Implement `setRoomVolume` (clamp 0..100), `stopRoute`, `setRoutePaused` and `setMasterMute` in `composeApp/src/commonMain/kotlin/ai/sonora/mobile/data/KtorHubRepository.kt` via generated `OutputsApi.setOutputVolume`, `RoutesApi.deleteRoute`, `RoutesApi.setPauseState` and `MasterMuteApi.setMasterMute`, ignoring response bodies and wrapped in `hubCall`, until T042 passes. Do not call `GroupsApi.setGroupVolume` anywhere.
- [ ] T047 [P] [US3] Implement `composeApp/src/commonMain/kotlin/ai/sonora/mobile/ui/Messages.kt` (`sealed UserAction { Volume; Stop; Pause; Resume; MasterMute(on) }`, `actionErrorMessage`) until T043 passes.
- [ ] T048 [US3] Implement the controls in `composeApp/src/commonMain/kotlin/ai/sonora/mobile/ui/rooms/RoomsViewModel.kt` (research R10, data-model.md "Transitions"), until T044 passes:
  - `onVolumeDragStart(key)`, `onVolumeDrag(key, value)`, `onVolumeDragEnd(key, value)` with a per-card throttled sender (≤ 1 send / 250 ms, latest value wins, final value sent immediately on end)
  - group targets computed by `GroupVolume.scale(card.memberVolumes-at-drag-start, value)`, one `setRoomVolume` per changed member, sent concurrently
  - `onCardAction(key)` (Stop / Pause / Resume per `card.action`) and `onMasterMuteToggle()`
  - `inFlight` guarding, a refresh after each completion, `message` on failure, `consumeMessage()`
  - every control a no-op when `connection != Live`, the card is muted (drag only), or `!actionEnabled`
- [ ] T049 [US3] Make `composeApp/src/commonMain/kotlin/ai/sonora/mobile/ui/rooms/VolumePill.kt` interactive:
  - `pointerInput` with horizontal drag + tap-to-set. The value comes from the x position over the width, clamped 0..100, and the fill follows the finger.
  - callbacks `onDragStart`/`onDrag`/`onDragEnd`; disabled when `muted || !enabled`
  - semantics: `contentDescription = "<title> volume"`, `progressBarRangeInfo(0..100)`, and `setProgress` only when enabled, so TalkBack can adjust it
  - minimum height 44 dp
- [ ] T050 [US3] Wire the controls in `NowPlayingCard.kt` and `RoomsScreen.kt` (`composeApp/src/commonMain/kotlin/ai/sonora/mobile/ui/rooms/`):
  - pill callbacks → view model
  - action button → `onCardAction`, disabled while `(key, action)` is in `inFlight` or stale
  - master mute button → `onMasterMuteToggle`, disabled while in flight or stale, accent state when muted
  - a `SnackbarHost` showing `message`, one at a time, then `consumeMessage()`

**Checkpoint**: US1–US3 are complete. The remote controls the house.

---

## Phase 6: User Story 4 - Navigate the app shell (Priority: P3)

**Goal**: bottom navigation (Rooms, Sources, Settings) and reachable placeholders for Sources, Now Playing and Start Playback.

**Independent Test**: each tab, card tap, room play button and "Play something" opens the right destination or placeholder. Back returns to the previous screen, and Back on Rooms leaves the app (US4 scenarios 1–2).

### Tests for User Story 4 ⚠️ write first, see them fail

- [ ] T051 [P] [US4] Write `composeApp/src/commonTest/kotlin/ai/sonora/mobile/ui/nav/AppBackStackTest.kt`. Cases:
  - starts at `[Rooms]`
  - `selectTab(Settings)` → `[Rooms, Settings]`; then `selectTab(Sources)` → `[Rooms, Sources]`; `selectTab(Rooms)` → `[Rooms]`
  - selecting the current tab is a no-op
  - `push(NowPlaying("r1"))` from Rooms then `pop()` → `[Rooms]`
  - `pop()` on `[Rooms]` returns false (the app exits)
  - `push(StartPlayback(null))` then `pop()` returns to the previous screen
  - the current top-level tab for highlighting is derived from the stack

### Implementation for User Story 4

- [ ] T052 [US4] Finish `AppBackStack` in `composeApp/src/commonMain/kotlin/ai/sonora/mobile/ui/nav/Destinations.kt` (add `currentTab`) until T051 passes.
- [ ] T053 [P] [US4] Implement `composeApp/src/commonMain/kotlin/ai/sonora/mobile/ui/nav/BottomBar.kt` per contracts/rooms-ui.md "Bottom navigation" and `design/screens/Main.dc.html`: three items (Rooms, Sources, Settings) with icons from T012 and labels, the selected item in accent, each item ≥ 44 dp, and the bar on `surface`.
- [ ] T054 [P] [US4] Implement `composeApp/src/commonMain/kotlin/ai/sonora/mobile/ui/placeholder/PlaceholderScreen.kt` (title, "Coming soon", one-line description, and a back arrow labelled "Back" when opened from a card/row). Descriptions: Sources — "Your saved stations, line-ins and files will be listed here."; Now Playing — "Full controls for this room will be here, including Move to room…"; Start Playback — "Pick a source to play here."
- [ ] T055 [US4] Wire navigation in `composeApp/src/commonMain/kotlin/ai/sonora/mobile/ui/nav/AppNavigation.kt` and `RoomsScreen.kt`. Render the `BottomBar` on top-level destinations. Map `Sources`/`NowPlaying`/`StartPlayback` to `PlaceholderScreen`. Card body tap → `push(NowPlaying(card.key))`, idle play button → `push(StartPlayback(roomId))`, "Play something" → `push(StartPlayback(null))`. Handle system Back through `NavDisplay`'s `onBack` = `pop()`, finishing the activity when it returns false.

**Checkpoint**: all user stories are functional.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [ ] T056 [P] Accessibility pass over `composeApp/src/commonMain/kotlin/ai/sonora/mobile/ui/` (FR-023, contracts/rooms-ui.md "Accessibility"):
  - every clickable ≥ 44 dp (`Modifier.minimumInteractiveComponentSize()` or explicit size)
  - every icon-only control has the exact label from the contract
  - verify contrast ≥ 4.5:1 for each text/background token pair used (text/textMuted/warningText on background/surface/surfaceRaised, onAccent on accent, kind icon colours on their tiles), with a small `composeApp/src/commonTest/kotlin/ai/sonora/mobile/ui/theme/ContrastTest.kt` computing WCAG relative luminance from the token values
- [ ] T057 [P] Compare the Rooms screen with `design/screens/Main.dc.html` (sizes, spacing, radii, font sizes, colours) and fix drift in `composeApp/src/commonMain/kotlin/ai/sonora/mobile/ui/rooms/`. Add `@Preview`s (Compose Multiplatform `org.jetbrains.compose.ui.tooling.preview` if available in CMP 1.12.1, else skip) with the design sample data in `composeApp/src/commonMain/kotlin/ai/sonora/mobile/ui/rooms/RoomsPreviews.kt`.
- [ ] T058 Confirm FR-012 / Constitution II by searching `composeApp/src/` for `LinearProgressIndicator`, `Slider` and `CircularProgressIndicator`; none may appear. Confirm the string `groups/` + `volume` appears only in the generated client and the T044 negative test.
- [ ] T059 Run the [quickstart.md](quickstart.md) §1 commands: `./gradlew :androidApp:assembleDebug`, `./gradlew :composeApp:testAndroidHostTest` (or the name confirmed in T009), `./gradlew :composeApp:allTests`, `./gradlew :composeApp:check`. All must be green, and `git status` must show no generated files. Record the final versions actually used in research.md R1 if any differ.
- [ ] T060 Update `CLAUDE.md` if anything implemented differs from what it states (commands, layout, versions), and mark the spec `**Status**: Implemented` in `specs/001-rooms-screen-foundation/spec.md`.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: T001 first (docs), then T002 → T003 → T004 → T005 → T006, with T007 and T008 parallel after T004. T009 needs T005–T008.
- **Foundational (Phase 2)**: needs Phase 1. T010–T014 are parallel. T015 → T016. T017 then T018 (T018 needs T011, T013, T014, T016, T017).
- **US1 (Phase 3)**: needs Phase 2.
- **US2 (Phase 4)**: needs Phase 2. In practice it uses US1's address flow (T026 RoomsViewModel, T023 store) for the app, but its tests use fakes and run without US1.
- **US3 (Phase 5)**: needs US2 (cards, RoomsViewModel polling, VolumePill, KtorHubRepository).
- **US4 (Phase 6)**: needs Phase 2. T055 touches `RoomsScreen.kt`, so do it after T040/T050 to avoid conflicts.
- **Polish (Phase 7)**: needs all stories.

### User Story Dependencies

- **US1 (P1)**: Foundational only.
- **US2 (P1)**: Foundational only for logic. App wiring builds on T026/T027 (same files: `RoomsViewModel.kt`, `RoomsScreen.kt`).
- **US3 (P2)**: US2.
- **US4 (P3)**: Foundational. Integrates with Rooms screen callbacks from US2/US3.

### Within Each User Story

- Tests first, seen failing → pure domain → data → view model → composables → wiring.

### Parallel Opportunities

- Phase 1: T007 ∥ T008.
- Phase 2: T010 ∥ T011 ∥ T012 ∥ T013 ∥ T014.
- US1: T019 ∥ T020 ∥ T021.
- US2: T028 ∥ T029 ∥ T030 ∥ T031, then T032 ∥ (T033, T034 sequential where they share types), T036 ∥ T037 ∥ T039.
- US3: T041 ∥ T042 ∥ T043 ∥ T044, then T045 ∥ T047.
- US4: T051, then T053 ∥ T054.
- Polish: T056 ∥ T057.

---

## Parallel Example: User Story 2

```bash
# Tests first (all different files):
Task: "Write SourceKindTest in composeApp/src/commonTest/kotlin/ai/sonora/mobile/domain/SourceKindTest.kt"
Task: "Write RoomsBuilderTest in composeApp/src/commonTest/kotlin/ai/sonora/mobile/domain/RoomsBuilderTest.kt"
Task: "Write KtorHubRepositorySnapshotTest in composeApp/src/commonTest/kotlin/ai/sonora/mobile/data/KtorHubRepositorySnapshotTest.kt"
Task: "Write RoomsViewModelPollingTest in composeApp/src/commonTest/kotlin/ai/sonora/mobile/ui/rooms/RoomsViewModelPollingTest.kt"

# Then independent UI pieces:
Task: "Implement StatusText in composeApp/src/commonMain/kotlin/ai/sonora/mobile/ui/rooms/StatusText.kt"
Task: "Implement display-only VolumePill in composeApp/src/commonMain/kotlin/ai/sonora/mobile/ui/rooms/VolumePill.kt"
Task: "Implement IdleRow in composeApp/src/commonMain/kotlin/ai/sonora/mobile/ui/rooms/IdleRow.kt"
```

---

## Implementation Strategy

### MVP First

1. Phase 1 Setup → Phase 2 Foundational.
2. US1 (address) + US2 (live Rooms): both P1, together they form the smallest useful app (a read-only house view).
3. **Stop and validate**: build, run all tests, and let the user install the APK against the real hub (quickstart §2 steps 1–7, 9).

### Incremental Delivery

1. Setup + Foundational → themed shell builds.
2. US1 → address persists (demoable on a device, no hub needed).
3. US2 → live house view (MVP).
4. US3 → controls.
5. US4 → full navigation shell with placeholders.
6. Polish → accessibility, design fidelity, final verification.

---

## Notes

- [P] = different files, no dependency on unfinished tasks.
- Verify each test fails before implementing (Constitution IV).
- Never commit `composeApp/build/` or hand-edit generated code (Constitution I).
- Never call `PUT /api/v2/groups/{id}/volume` (FR-013c).
- Stop and ask when a task needs a decision none of the design documents cover.
