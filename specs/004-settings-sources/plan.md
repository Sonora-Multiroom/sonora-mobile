# Implementation Plan: Settings Tabs and Sources

**Branch**: `004-settings-sources` | **Date**: 2026-10-04 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/004-settings-sources/spec.md`

## Summary

This replaces the hub-address-only Settings stub with the full Settings screen:
- **Hub row and address sheet**: the connection state and address, plus a sheet with "Test
  connection" and "Save".
- **Four tabs**: Rooms / Groups / Sources / Extensions, with on/off switches for rooms, groups and
  configured sources, removal of runtime sources, and a read-only extension list.
- **Confirmations**: turning off a playing room or group, or removing a runtime source in use,
  asks first.

The bottom bar's Sources item opens Settings on the Sources tab, and the placeholder goes away.

Technically:
- **Contract**: no change. `api/openapi.json` was re-fetched with the new
  `./gradlew refreshOpenApi` and is still 0.1.21. Every call is documented and already generated.
- **Repository**: six new methods (three `/enabled` PUTs, `DELETE /inputs/{id}`,
  `GET /extensions`, and a room count for the connection test).
- **Domain**: what Settings shows is decided in two pure, test-first files, `SettingsBuilder` (rows,
  status lines, the added line, extension rows) and `SettingsConfirm` (whether to ask and what the
  dialog says), plus `sourceDetail()` next to the existing kind rules.
- **App scope**: switch changes and removals run in an app-scoped `SettingsActions`, so they
  outlive the screen. They are optimistic and fenced on `startedSeq`, as volume is. The tab and the
  "open the sheet" request live in an app-scoped `SettingsNavigator`.
- **Extensions**: fetched after each session refresh, and only while the Extensions tab is shown.
  There is no second poll loop.
- **One new dependency**: kotlinx-datetime, for "Added today 14:30" in the phone's time zone.

## Technical Context

**Language/Version**: Kotlin 2.4.20 (KMP), JDK 17+, Gradle 9.8.0 (unchanged)

**Primary Dependencies**:
- Unchanged from 003: Compose Multiplatform 1.12.1, material3 1.9.0, lifecycle 2.11.0,
  navigation3 1.1.2, Ktor 3.6.0, kotlinx.serialization 1.11.0, coroutines 1.11.0, DataStore 1.2.1,
  OpenAPI Generator 7.14.0.
- **Added**: kotlinx-datetime 0.8.0 ([R13](research.md#r13-dependencies)).

**Storage**: no new storage. The hub address stays in DataStore (001). The selected tab is
in-memory, app-scoped.

**Testing**: kotlin-test, kotlinx-coroutines-test (virtual time), Ktor `MockEngine`, `FakeHub`,
all in `commonTest`.

**Target Platform**: Android (minSdk 26). The iOS targets are declared but not built.

**Project Type**: mobile app (KMP: `composeApp` + `androidApp` + `iosApp` stub)

**Performance Goals**:
- A switch change reaches the hub and shows on Rooms within 3 s (SC-002): one request, then an
  immediate refresh.
- The connection test answers within 3 s, or fails at the 3 s timeout.

**Constraints**:
- One poll loop app-wide, and zero requests in the background.
- 3 s timeouts.
- Extensions are requested only while the Extensions tab is visible.
- Never `PUT /groups/{id}/volume`.
- The app never stops a playback itself: the hub does it on turn-off or removal.
- Nothing Android-only in `commonMain`.

**Scale/Scope**: one home hub, ~2–20 rooms, a few groups, ~5–30 configured sources, 0–10 runtime
sources, ~5 extensions. The work is one rebuilt screen, six repository methods and two app-scoped
objects.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | How |
|---|---|---|
| I. API Contract Fidelity | ✅ | Only documented `/api/v2` calls through `HubRepository` ([contract](contracts/hub-repository.md)). The contract was re-fetched on 2026-10-04 and is unchanged ([R1](research.md#r1-contract-and-the-hub-dependency)). The behaviour the spec relies on (turn-off stops playback) is a hub release that is not yet out. It is a precondition of on-device verification, not something built around: `refreshOpenApi` runs before verification and any change is reconciled first. The hub gap "a playback does not report its rooms" is already in AGENTS.md. `countRooms` uses a v2 path, not `/api-docs` |
| II. Truthful UI | ✅ | Every status line, the "Playing" lines, whether to ask and the dialog text come from hub state, decided once in `SettingsBuilder`/`SettingsConfirm` ([R3](research.md#r3-settings-content-one-pure-builder), [R4](research.md#r4-when-to-ask-and-the-dialog-text)). Switches show the user's value only while it is in flight, then the hub's, confirmed by a fenced refresh ([R5](research.md#r5-switch-flow-app-scoped-optimistic-fenced)). Kind uses the single `inferSourceKind`, and the detail uses one new function, `sourceDetail` ([R11](research.md#r11-source-detail-line)). Disabled ("Off") and unavailable ("Not connected") stay distinct. The dialog's "stops playback" is true for the hub version the feature targets (spec Clarifications) |
| III. Shared-First | ✅ | All code is in `commonMain`. Time zones come from a multiplatform library rather than platform code ([R10](research.md#r10-added-when-and-the-time-zone)). No platform source-set changes |
| IV. Test-First | ✅ | Builder, added line, source detail, confirmations, extension rows, text, failure mapping, repository additions (MockEngine), `SettingsActions` and the view model each get tests first ([quickstart](quickstart.md) §1, covering all of FR-021) |
| V. Resilient LAN | ✅ | 3 s timeouts on every new call, including the test against a draft address. Stale state keeps the lists and disables controls. Unknown extension status or connection maps to explicit `Unknown`, as does an unknown source origin (configured). An unparseable `createdAt` never fails a snapshot. Problem details become the app's own copy (`settingsFailureMessage`); `rejectionReason` is never shown |
| VI. Design Fidelity & A11y | ✅ | Layout per `Settings.dc.html` ([UI contract](contracts/settings-ui.md)). The departures are in spec Assumptions: sorting, footer wording and position, undrawn states, extension wording, new tokens. Rows are switches with names, tabs are tabs, there are hub-row and trash labels, alert dialog semantics, ≥ 44 dp targets, and new contrast pairs tested |
| VII. Minimal Dependencies | ✅ | One addition, kotlinx-datetime 0.8.0 (JetBrains, multiplatform), justified in R10/R13, with its version in `libs.versions.toml` |

**Post-design re-check (after Phase 1)**: still passing.
- Generated types stay in `data/`. `ExtensionInventory`/`Extension` are new domain types, with no
  wire type exposed.
- `HubSession` is unchanged. Extensions ride on its refreshes, and `SettingsActions` uses the
  existing fence rule (`startedSeq` captured on completion) and `requestRefresh()`.
- `routesByRoom()` (003) is reused for room status and confirmations. Rooms and Now Playing are
  untouched.
- The undrawn states reuse existing blocks (Rooms' `Message`, `StaleBanner`), as listed in the
  spec's departures.

## Project Structure

### Documentation (this feature)

```text
specs/004-settings-sources/
├── spec.md
├── plan.md              # this file
├── research.md          # Phase 0
├── data-model.md        # Phase 1
├── quickstart.md        # Phase 1
├── contracts/
│   ├── hub-repository.md
│   └── settings-ui.md
└── tasks.md             # /speckit-tasks (not created here)
```

### Source Code (changes against 003)

```text
gradle.properties                          # versionName 0.4.0-alpha, versionCode 4 (first commit)
gradle/libs.versions.toml                  # + kotlinx-datetime 0.8.0
composeApp/build.gradle.kts                # commonMain + kotlinx-datetime
AGENTS.md                                  # Settings endpoints, ui/session list, no Sources placeholder (R14)

composeApp/src/commonMain/kotlin/sonora/multiroom/mobile/
├── AppGraph.kt                            # + SettingsNavigator, SettingsActions; settingsViewModel(...)
├── domain/
│   ├── Models.kt                          # Source(+autoRemove, +createdAt); ExtensionInventory, Extension, enums
│   ├── SourceKind.kt                      # + sourceDetail()
│   ├── SettingsBuilder.kt                 # SettingsContent, rows, RoomStatus, AddedLine, extensionRows()
│   └── SettingsConfirm.kt                 # ItemKind, ItemKey, Confirmation, turnOff/remove/keepsPlaying
├── data/
│   ├── HubRepository.kt                   # + set{Room,Group,Source}Enabled, removeSource, extensions, countRooms
│   ├── KtorHubRepository.kt               # + the six calls
│   └── ApiMapping.kt                      # autoRemove, createdAt; Extension mapping
├── ui/
│   ├── Messages.kt                        # + SettingsFailure, settingsFailure(), settingsFailureMessage()
│   ├── session/
│   │   ├── SettingsNavigator.kt           # tab + open-sheet request (app-scoped)
│   │   └── SettingsActions.kt             # switch/removal runner, overrides, messages (app-scoped)
│   ├── nav/
│   │   ├── Destinations.kt                # − Sources; "sources" decodes to Settings
│   │   ├── BottomBar.kt                   # Sources item → onOpenSources
│   │   └── AppNavigation.kt               # Settings entry; Sources tab; "Set your hub address" opens the sheet
│   ├── placeholder/                       # deleted (no remaining user)
│   ├── rooms/RoomsScreen.kt               # Message / StaleBanner reused (already internal)
│   ├── theme/Tokens.kt, Icons.kt          # new tokens and icons (R12)
│   └── settings/
│       ├── SettingsViewModel.kt           # rewritten
│       ├── SettingsUiState.kt
│       ├── SettingsScreen.kt              # header, hub row, tab bar, tab content, footer, snackbar
│       ├── SettingsText.kt                # every string of the UI contract "Copy" table
│       ├── HubRow.kt
│       ├── HubAddressSheet.kt
│       ├── SettingsTabs.kt
│       ├── SettingRows.kt                 # room, group, configured, runtime, extension rows
│       ├── SonoraSwitch.kt
│       └── ConfirmDialog.kt

composeApp/src/commonTest/kotlin/sonora/multiroom/mobile/
├── domain/       SettingsBuilderTest, AddedLineTest, SourceDetailTest, SettingsConfirmTest, ExtensionRowsTest
├── data/         KtorHubRepositorySettingsTest (new), KtorHubRepositorySnapshotTest (extended), Fixtures
├── ui/           MessagesTest (extended)
├── ui/session/   SettingsActionsTest
├── ui/nav/       AppBackStackTest (extended)
├── ui/rooms/     FakeHub.kt (FakeRepository: + the six methods, per-call results)
├── ui/settings/  SettingsViewModelTest (rewritten), SettingsTextTest
└── ui/theme/     ContrastTest (extended)
```

**Structure Decision**: the existing KMP wizard layout. Domain rules go in `domain/`, wire access in
`data/`, app-scoped state in `ui/session/` and the screen in `ui/settings/`.

## Implementation notes for tasks

1. **First commit**: version bump to `0.4.0-alpha` / `4` (AGENTS.md workflow).
2. **Dependency**: add kotlinx-datetime 0.8.0 and build once before anything uses it.
3. **Test-first order**:
   1. `sourceDetail`
   2. the `Source` mapping (`autoRemove`, `createdAt`) and the Extension mapping
   3. `SettingsBuilder`, including the added line, then `extensionRows`
   4. `SettingsConfirm`
   5. text (`SettingsText`, `settingsFailureMessage`)
   6. the repository additions (MockEngine)
   7. `SettingsActions`
   8. `SettingsNavigator` and the back-stack change
   9. `SettingsViewModel`

   Watch each one fail first.
4. **UI**: build the screen per the [UI contract](contracts/settings-ui.md). Then wire the
   navigation (remove the Sources destination and the placeholder, add the bottom-bar Sources
   item, and make "Set your hub address" open the sheet), and sync AGENTS.md (R14).
5. Finish with [quickstart](quickstart.md) §1 all green. Hand over for §2 **after the hub
   release**: `refreshOpenApi` first, then the device rows. Record the results in
   `verification.md`.

## Open points for the user (not blocking the plan)

- **The hub release** (spec Dependency): stopping playback when a group is turned off is not yet
  in the hub, and the room change is on an unreleased branch. §2 rows 5–7 can only pass after the
  release. Everything else can be verified before it.
- **A runtime source without `createdAt`** (not covered by the spec): the plan drops the "Added
  <when>" part, keeps the other parts and sorts it last ([R10](research.md#r10-added-when-and-the-time-zone)).
  The hub always sets it for runtime inputs, so this is defensive only.
- **The dialog scrim** is the platform's dim, not exactly `rgba(5,6,8,0.72)`
  ([R12](research.md#r12-ui-building-blocks)). If it looks visibly different on the device, record
  it in `verification.md`; it is not worked around with platform code.

## Complexity Tracking

There are no constitution deviations. Each of the two app-scoped objects (`SettingsActions`,
`SettingsNavigator`) is the smallest way to meet a spec requirement: requests and their messages
outlive the screen, and the tab is remembered across the entry being popped. The one new
dependency is justified in R13.
