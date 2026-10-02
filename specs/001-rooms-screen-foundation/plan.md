# Implementation Plan: Rooms Screen Foundation

**Branch**: `001-rooms-screen-foundation` | **Date**: 2026-10-02 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/001-rooms-screen-foundation/spec.md`

## Summary

Scaffold the Kotlin Multiplatform app (Android shipping, iOS stub) with the bundled fonts and
design tokens. Generate the hub client from `api/openapi.json` at build time (v2 paths only) and
wrap it in a small `HubRepository`. Add a Settings stub that persists the hub address, and build
the Rooms screen. The screen polls the hub every 2.5 s while in the foreground. It shows
now-playing cards (volume pill, one Stop or Pause/Resume action), Idle rows (nothing playing /
turned off / not connected), "N of M rooms in use" and master mute, with bottom navigation and
placeholders for later screens. The join of outputs + groups + routes + inputs into cards, source
kind inference, group volume scaling and address parsing are pure functions written test-first in
`commonTest`.

## Technical Context

**Language/Version**: Kotlin 2.4.20 (KMP), JDK 17+, Gradle 9.8.0

**Primary Dependencies**: Compose Multiplatform 1.12.1, AGP 9.4.1
(`com.android.kotlin.multiplatform.library` + separate `androidApp`), Ktor client 3.6.0
(OkHttp / Darwin), kotlinx-serialization 1.11.0, kotlinx-coroutines 1.11.0, JetBrains lifecycle
2.11.0 (ViewModel, runtime-compose), JetBrains Navigation 3 1.1.2, AndroidX DataStore preferences
1.2.1, Compose material3 1.9.0 (limited use), OpenAPI Generator 7.14.0 (build time). See
[research.md](research.md) R1.

**Storage**: DataStore Preferences file holding a single key, the hub address.

**Testing**: kotlin-test, kotlinx-coroutines-test (virtual time), Ktor `MockEngine`, all in
`commonTest`, run as Android host tests and via `allTests`.

**Target Platform**: Android (minSdk 26, target/compile SDK = latest stable API at
implementation time). iOS targets declared, not built.

**Project Type**: mobile app (KMP: shared `composeApp` library + `androidApp` + `iosApp` stub)

**Performance Goals**: hub change visible ≤ 3 s (SC-002). Unreachable state ≤ 6 s (SC-005).
Volume drag at 60 fps with ≤ 4 requests/s.

**Constraints**: 3 s timeout per request. No overlapping polls. Zero requests in the background.
No group-volume endpoint. Plain HTTP on the LAN. Nothing Android-only in `commonMain`.

**Scale/Scope**: one home hub, ~2–20 rooms, a handful of groups/routes. 1 real screen + Settings
stub + 3 placeholders.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | How |
|---|---|---|
| I. API Contract Fidelity | ✅ | Client generated at build time with `FILTER=path:/api/v2`, output in `build/` (not committed, never edited). Generated package is `internal` and banned from `ui/`/`domain/` by `verifyLayering`. All hub access goes through `HubRepository` ([contract](contracts/hub-repository.md)). No group-volume call. Hub gaps (no group volume, no kind, no push, no mDNS) are already in spec + CLAUDE.md |
| II. Truthful UI | ✅ (see note) | No progress/position/metadata. Pause/Resume only when `pauseable`. Distinct Off / Not connected / Idle / Playing / Paused / Couldn't play. Local value wins while dragging, refresh reconciles. Throttled volume. Kind derived only in `inferSourceKind` |
| III. Shared-First | ✅ | Everything in `commonMain`. `androidMain` only has the OkHttp engine + DataStore path. `androidApp` has the activity, manifest and network config. iOS stub keeps targets compiling-ready |
| IV. Test-First | ✅ | Pure logic suites + `MockEngine` repository tests + ViewModel tests with virtual time, written before implementation ([quickstart](quickstart.md) §1). Done = `:androidApp:assembleDebug` + all tests green (commands per constitution 1.1.1) |
| V. Resilient LAN | ✅ | 3 s timeouts, sequential poll loop, STARTED-only lifecycle, lenient JSON with unknown-enum coercion → `Unknown`, RFC 7807 mapped to plain messages, cleartext app-wide, address user-entered with no default |
| VI. Design Fidelity & A11y | ✅ | Tokens in `SonoraTheme`. Pill per design (not a progress bar). States the design does not draw reuse the card/row styles (spec Assumptions). ≥ 44 dp, labels per [rooms-ui contract](contracts/rooms-ui.md) |
| VII. Minimal Dependencies | ✅ | Each dependency justified in research R1–R9. Only JetBrains/AndroidX/Ktor/kotlinx + build-time OpenAPI Generator. No DI, icon pack or Turbine |

**Note on II**: Rooms follows Principle II as amended in constitution 1.1.1 ("Live stream" status
on cards; the full "Live streams can't be paused" line belongs to Now Playing).

**Post-design re-check (after Phase 1)**: still passing. The data model keeps generated types in
`data/` only. The repository contract omits group volume. The UI contract keeps the pill and the
one-action-per-card rule.

## Project Structure

### Documentation (this feature)

```text
specs/001-rooms-screen-foundation/
├── spec.md
├── plan.md              # this file
├── research.md          # Phase 0
├── data-model.md        # Phase 1
├── quickstart.md        # Phase 1
├── contracts/
│   ├── hub-repository.md
│   └── rooms-ui.md
└── tasks.md             # /speckit-tasks (not created here)
```

### Source Code (repository root)

```text
settings.gradle.kts                 # includes :composeApp, :androidApp
build.gradle.kts                    # plugins apply false
gradle.properties
gradle/libs.versions.toml           # all versions (research R1)
gradlew, gradlew.bat, gradle/wrapper/
api/openapi.json                    # generator input (existing)

composeApp/                         # KMP library: com.android.kotlin.multiplatform.library
├── build.gradle.kts                # kotlin { android { withHostTest {} }; iosArm64(); iosSimulatorArm64() }
│                                   # openApiGenerate task → build/generated/openapi, verifyLayering task
└── src/
    ├── commonMain/
    │   ├── composeResources/font/  # sora.ttf, dm_sans.ttf (existing)
    │   └── kotlin/ai/sonora/mobile/
    │       ├── App.kt              # SonoraTheme + navigation root
    │       ├── AppGraph.kt         # manual wiring: store, repository factory, view models
    │       ├── domain/
    │       │   ├── Models.kt       # Room, Group, Source, Route, Target, RouteStatus, HubSnapshot
    │       │   ├── SourceKind.kt   # inferSourceKind (the only one)
    │       │   ├── RoomsBuilder.kt # pure join → RoomsContent, NowPlayingCard, IdleRow, CardStatus, CardAction
    │       │   ├── GroupVolume.kt  # scale(base, new)
    │       │   └── HubAddress.kt   # parse/normalise
    │       ├── data/
    │       │   ├── HubRepository.kt       # interface, HubResult, HubError, factory
    │       │   ├── KtorHubRepository.kt   # uses generated *Api classes
    │       │   ├── ApiMapping.kt          # generated → domain
    │       │   ├── HttpClients.kt         # HttpClient config; expect fun httpEngine()
    │       │   └── HubAddressStore.kt     # interface + DataStore impl; path passed in via AppGraph
    │       └── ui/
    │           ├── theme/          # Tokens.kt, Type.kt, Shapes.kt, Theme.kt, Icons.kt
    │           ├── nav/            # Destinations.kt, AppNavigation.kt (Navigation 3), BottomBar.kt
    │           ├── rooms/          # RoomsViewModel.kt, RoomsUiState.kt, RoomsScreen.kt,
    │           │                   # NowPlayingCard.kt, IdleRow.kt, VolumePill.kt, StatusText.kt
    │           ├── settings/       # SettingsViewModel.kt, SettingsScreen.kt
    │           ├── placeholder/    # PlaceholderScreen.kt
    │           └── Messages.kt     # actionErrorMessage
    ├── commonTest/kotlin/ai/sonora/mobile/
    │   ├── domain/                 # SourceKindTest, RoomsBuilderTest, GroupVolumeTest, HubAddressTest
    │   ├── data/                   # HttpClientsTest, KtorHubRepositorySnapshotTest, KtorHubRepositoryActionsTest (MockEngine), Fixtures.kt
    │   ├── ui/                     # MessagesTest; nav/AppBackStackTest; settings/SettingsViewModelTest; theme/ContrastTest
    │   └── ui/rooms/               # RoomsViewModelAddressTest, RoomsViewModelPollingTest, RoomsViewModelControlsTest, StatusTextTest
    ├── androidMain/kotlin/ai/sonora/mobile/   # httpEngine() = OkHttp
    └── iosMain/kotlin/ai/sonora/mobile/       # httpEngine() = Darwin, MainViewController (stub, passes the DataStore path)

androidApp/                         # com.android.application, applicationId ai.sonora.mobile
├── build.gradle.kts
└── src/main/
    ├── AndroidManifest.xml         # INTERNET, networkSecurityConfig, MainActivity
    ├── kotlin/ai/sonora/mobile/android/MainActivity.kt   # setContent { App(AppGraph(dataStorePath = filesDir…)) }
    └── res/                        # xml/network_security_config.xml (cleartextTrafficPermitted=true), launcher icon, themes

iosApp/                             # Xcode project stub, not built
```

**Structure Decision**: the current JetBrains KMP wizard layout under AGP 9 (research R2), chosen by
the user on 2026-10-02: shared `composeApp` library, `androidApp` application module, `iosApp`
stub. All logic and UI live in `composeApp/src/commonMain`.

## Implementation notes for tasks

1. **First task**: update CLAUDE.md "Project layout" + "Commands" and amend Constitution IV
   commands (PATCH → 1.1.1, `/speckit-constitution`) to the AGP 9 layout, in one commit.
2. Write test suites before each logic unit: SourceKind → GroupVolume → HubAddress → RoomsBuilder →
   KtorHubRepository → RoomsViewModel. Watch each one fail first (Constitution IV).
3. Generated client: verify it compiles against `openapi.json` 0.1.20 early. On generator issues
   follow research R3's fallback order, then stop and ask.
4. Polling, throttling and drag ownership follow research R5/R10. Error copy follows the
   [hub-repository contract](contracts/hub-repository.md). Screen copy and labels follow the
   [rooms-ui contract](contracts/rooms-ui.md).
5. Finish with the [quickstart](quickstart.md) §1 commands green.

## Complexity Tracking

None. The AGP 9 command change (`androidApp` module, see research R2) was folded into constitution
1.1.1 and CLAUDE.md on 2026-10-02.
