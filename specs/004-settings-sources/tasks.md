---

description: "Task list for 004 Settings Tabs and Sources"
---

# Tasks: Settings Tabs and Sources

**Input**: Design documents from `specs/004-settings-sources/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md),
[data-model.md](data-model.md), [contracts/hub-repository.md](contracts/hub-repository.md),
[contracts/settings-ui.md](contracts/settings-ui.md), [quickstart.md](quickstart.md)

**Tests**: REQUIRED. They come from Constitution IV (test-first for logic, `MockEngine` repository
tests) and spec FR-021. Every test task comes before its implementation task and MUST be seen
failing first.

**Organization**: grouped by user story. Spec priorities:
- US1 P1: turn rooms, groups and sources on and off
- US2 P2: remove runtime sources
- US3 P2: change and test the hub address
- US4 P3: see the extensions

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on an unfinished task)
- **[Story]**: US1–US4 from spec.md

## Path Conventions

- Shared code: `composeApp/src/commonMain/kotlin/sonora/multiroom/mobile/` (abbreviated `main/` below)
- Shared tests: `composeApp/src/commonTest/kotlin/sonora/multiroom/mobile/` (abbreviated `test/` below)
- Generated client (never committed or edited): `composeApp/build/generated/openapi/`, package
  `sonora.multiroom.mobile.hub.generated`

## General rules for every task

- Nothing Android-only in `commonMain`/`commonTest`. `ui/` and `domain/` never import the generated
  package (`verifyLayering`).
- Colours, typography and shapes come only through `SonoraTheme` tokens (`main/ui/theme/`), never
  literals. The new tokens are listed in research R12.
- Never call `PUT /api/v2/groups/{id}/volume`. The app never stops a playback itself from Settings:
  turning off or removing is a single request, and the hub stops the playback.
- Every 001–003 test scenario MUST stay green, except 001's `SettingsViewModelTest`, which T013
  replaces, and the `Destination.Sources` cases of `AppBackStackTest`, which T006 rewrites. Rooms,
  Now Playing and Start Playback are not changed, except their "Open Settings" action (T054).
- Every "wait for a fresh snapshot" captures `session.startedSeq`, never the state's `refreshSeq`
  (the fence rule in `HubSession`'s KDoc).
- Error and status copy is written only in `main/ui/Messages.kt` (failures) and
  `main/ui/settings/SettingsText.kt` (everything else), verbatim from the
  [UI contract "Copy" table](contracts/settings-ui.md#copy).
- If a task needs a decision none of the design documents cover, stop and ask (AGENTS.md "Workflow").
- Commit after each task or logical group with a Conventional Commit message that explains why
  (CONTRIBUTING.md).

---

## Phase 1: Setup

**Purpose**: the version bump, the one new dependency, and a check that the generated client has
what the contract docs name.

- [X] T001 Bump the app version as the **first commit** of this feature (AGENTS.md "Workflow"): in
  `gradle.properties` set `sonora.versionName=0.4.0-alpha` and `sonora.versionCode=4`. Run
  `./gradlew :androidApp:assembleDebug`.
- [X] T002 Add kotlinx-datetime (research R10/R13):
  - `gradle/libs.versions.toml`: `kotlinxDatetime = "0.8.0"` under `[versions]`, and
    `kotlinx-datetime = { module = "org.jetbrains.kotlinx:kotlinx-datetime", version.ref = "kotlinxDatetime" }`
    under `[libraries]`.
  - `composeApp/build.gradle.kts`: add `implementation(libs.kotlinx.datetime)` to the `commonMain`
    dependencies.

  Run `./gradlew :androidApp:assembleDebug :composeApp:testAndroidHostTest`: green, with no code
  change.
- [X] T003 Confirm the generated client (research R1). Run `./gradlew :composeApp:openApiGenerate`.
  In `composeApp/build/generated/openapi/`, check these exist:
  - `OutputsApi.setOutputEnabled(outputId, EnabledRequest)`
  - `GroupsApi.setGroupEnabled(groupId, EnabledRequest)`
  - `InputsApi.setInputEnabled(inputId, EnabledRequest)`
  - `InputsApi.deleteInput(inputId)`
  - `ExtensionsApi.listExtensions()` returning `ExtensionInventory(loadingEnabled, extensionsDirectory, extensions)`
  - `Extension(id, name, version, requiredApiVersion, status: ACTIVE|REJECTED|DISABLED|INERT, connectionState: NOT_APPLICABLE|CONNECTED|DISCONNECTED, rejectionReason)`
  - `InputResponse.autoRemove: Boolean?`
  - `InputResponse.createdAt: String?`

  If a name differs, record it in research.md R1; the HTTP column of
  contracts/hub-repository.md stays binding.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: the new Settings frame every story fills in. It covers the tab memory, the
navigation change (no Sources placeholder), theme tokens and icons, the Hub row, and the address
sheet with Save (so the address stays editable from the first commit on), and the shared session
states.

**⚠️ CRITICAL**: no user story work begins until this phase is complete.

### Tab memory and navigation (research R2)

- [X] T004 [P] Write `test/ui/session/SettingsNavigatorTest.kt`, failing first:
  - `tab` starts at `SettingsTab.Rooms`
  - `select(Groups)` → `Groups`
  - `openSheet()` sets `openSheetRequested` to true, and `consumeSheetRequest()` sets it back to false
- [X] T005 Create `main/ui/session/SettingsNavigator.kt` with `enum class SettingsTab { Rooms, Groups,
  Sources, Extensions }` and `class SettingsNavigator` (data-model.md "SettingsNavigator":
  `tab: StateFlow<SettingsTab>`, `openSheetRequested: StateFlow<Boolean>`, `select`, `openSheet`,
  `consumeSheetRequest`). In `main/AppGraph.kt` add `val settingsNavigator = SettingsNavigator()`.
  Make T004 pass.
- [X] T006 [P] Rewrite the `Destination.Sources` cases in `test/ui/nav/AppBackStackTest.kt`, failing
  first:
  - `selectTab` accepts only `Rooms` and `Settings`
  - `[Rooms, Settings]` has `currentTab == Settings` and shows the bottom bar
  - `decodeDestination("sources")` → `Destination.Settings` (an old saved stack restores to
    Settings)
  - round-trips for `rooms`, `settings`, `now:<id>`, `play`, `play:<id>` stay green
- [X] T007 In `main/ui/nav/Destinations.kt` remove `Destination.Sources` from the sealed interface,
  `currentTab`, `showsBottomBar`, `selectTab`'s `require` and `encodeDestination`. In
  `decodeDestination`, map `"sources"` to `Destination.Settings`. Make T006 pass.
- [X] T008 Wire the bottom bar (FR-003):
  - `main/ui/nav/BottomBar.kt`: replace `onSelect: (Destination) -> Unit` with a per-item action.
    Rooms → `selectTab(Rooms)`. Sources → `onOpenSources`. Settings → `selectTab(Settings)`.
    "Selected" is `currentTab == Destination.Settings` for the Settings item only; the Sources item
    is never shown as selected.
  - `main/ui/nav/AppNavigation.kt`: remove the `entry<Destination.Sources>` placeholder and its
    import. `onOpenSources = { graph.settingsNavigator.select(SettingsTab.Sources);
    backStack.selectTab(Destination.Settings) }`.
  - Delete `main/ui/placeholder/PlaceholderScreen.kt`, which has no other user.

  Build green.

### Theme (research R12)

- [X] T009 [P] Extend `test/ui/theme/ContrastTest.kt`, failing first (the tokens don't exist yet),
  with ≥ 4.5:1 for each of:
  - `danger #FF8A7A` on `dangerContainer #3A1A16`
  - `onDanger #2A0D08` on `danger`
  - `positive #5FD3C4` on `positiveContainer #12302E`
  - `positive` on `surface`
  - `danger` on `surface`
  - `textSoft` on `surfaceRaised`
  - `accent` on `surface` (the amber "Playing · …" line)
  - `text` on `outline` (the selected tab)
- [X] T010 In `main/ui/theme/Tokens.kt` add to `SonoraColors`, with a comment "Settings
  (design/screens/Settings.dc.html)":
  - `danger = Color(0xFFFF8A7A)`, `dangerContainer = Color(0xFF3A1A16)`, `onDanger = Color(0xFF2A0D08)`
  - `positive = Color(0xFF5FD3C4)`, `positiveContainer = Color(0xFF12302E)`
  - `chevron = Color(0xFF6E717A)`, `rowDivider = Color(0xFF22252B)`
  - `switchTrackOff = Color(0xFF2C3038)`, `switchThumbOff = Color(0xFF9A9DA6)`

  In `main/ui/theme/Icons.kt` add, from the design's inline SVG (`Settings.dc.html`):
  - `Server`: two `rect(3,4,18,7,2)` / `rect(3,13,18,7,2)` and `"M7 7.5h.01M7 16.5h.01"`
  - `Chevron`: `"M9 6l6 6-6 6"`
  - `Trash`: `"M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3"`
  - `GroupStack`: `rect(3,7,10,14,2)` and `"M8 3h11a2 2 0 0 1 2 2v12"`
  - `StopOutline`: the stroked `rect(6,6,12,12,2)`

  Make T009 pass.

### Hub row and address sheet with Save (research R9; US3 adds the test)

- [X] T011 [P] Write `test/ui/settings/SettingsTextTest.kt` (hub part), failing first. For
  `hubRow(state: SessionState)`:
  - `Initial` → `HubStatus.Hidden`, no address
  - `NoAddress` → `NotSet` with address line "Set the hub address to start"
  - `Connected` + `Loading` → `Connecting` "Connecting…"
  - `Live` → `Connected` "Connected"
  - `Unreachable` → `NotConnected` "Not connected"

  The address is `HubAddress.baseUrl`. The accessibility label is "Hub connection: <status>,
  <address>. Change address", or "Hub connection: Not set. Change address" without an address
  (FR-023).
- [X] T012 Create `main/ui/settings/SettingsText.kt` with `HubStatus`, `HubRow` (data-model.md
  `SettingsUiState.hub`), `hubRow(state)` and `hubRowLabel(row)`. Make T011 pass.
- [X] T013 Replace `test/ui/settings/SettingsViewModelTest.kt` (001) with the new view model's
  foundation, failing first. Use `runViewModelTest`, `HubSession` with `FakeFactory`, and
  `InMemoryHubAddressStore`:
  - **visibility**: `onVisible()` acquires the session (a snapshot is requested), and `onHidden()`
    releases it
  - **hub row and tab**: `state.hub` follows the session (T011 values), and `state.tab` follows
    `SettingsNavigator.tab`; `onTabSelected(Groups)` updates the navigator, and a new view model
    starts on `Groups` (FR-002)
  - **body**: `Initial`, `NoAddress`, a `Lists` body while `Live` with `controlsEnabled = true`,
    and `stale = true, controlsEnabled = false` when `Unreachable` with an earlier snapshot
  - **sheet (001 address rules, unchanged)**:
    - `onHubRowTapped()` opens the sheet with the saved address as the draft; with no address the
      draft is empty
    - `onDraftChange` clears the error
    - `onSave()` with "" or "a b" shows the 001 messages and saves nothing
    - `onSave()` with "multiroom.lan" saves `http://multiroom.lan:8080` and closes the sheet
    - `onSheetClosed()` discards the draft, and reopening shows the saved address
  - **sheet request**: when `openSheetRequested` is true at `onVisible()`, the sheet opens and the
    request is consumed
- [X] T014 Rewrite `main/ui/settings/SettingsViewModel.kt` and create
  `main/ui/settings/SettingsUiState.kt` (data-model.md "SettingsUiState": `hub`, `tab`, `body`,
  `sheet`, `message`; `confirm` comes in US1). Constructor
  `(session: HubSession, store: HubAddressStore, navigator: SettingsNavigator)`. More parameters are
  added by later tasks. Build `Lists` from the session state without content for now.
  `controlsEnabled = connection == Live`. Update `AppGraph.settingsViewModel()`. Make T013 pass.
- [X] T015 Build the frame in `main/ui/settings/` per the [UI contract](contracts/settings-ui.md)
  "Layout":
  - `SettingsScreen.kt`:
    - polls while visible: `repeatOnLifecycle(STARTED)` → `onVisible`/`onHidden`, as `RoomsScreen`
      does
    - header "Settings"
    - hub row, then the tab bar
    - a scrolling column with the selected tab's content and the version footer at its end
      (`appVersionLabel()`, `label12`, `textMuted`, centred, FR-001)
    - a `SnackbarHost` for `state.message`
    - the session states of the UI contract "States" table: `NoAddress` → Rooms'
      `Message("Set your hub address", …, actionLabel = "Set hub address", onAction = open sheet)`,
      stale → `StaleBanner()` above the content, unreachable without a snapshot → Rooms'
      unreachable `Message("Can't reach the hub", "Tried <address>. Retrying…", actionLabel =
      "Change address", onAction = open sheet)` (spec Assumptions, departures)
  - `HubRow.kt`:
    - one `Role.Button` with the T012 label
    - tile 40 r12 with `Server`; "Hub" 15 sp 600, the status with its 6 dp dot coloured `positive`
      / `danger` / `textMuted`
    - the address 13 sp textMuted, one line, ellipsis; `Chevron` in `chevron`
  - `SettingsTabs.kt`: a 4-way segmented bar (`Role.Tab`, `selected`, 40 dp pill, touch area
    spanning the 48 dp bar)
  - `HubAddressSheet.kt`:
    - `ModalBottomSheet(scrimColor = scrim)`, with title, Close ("Close"), the "URL" field
      (placeholder "http://192.168.1.10:8080", URI keyboard, no autocorrect), the hint "Your phone
      and the hub need to be on the same network." (or the 001 error in `warningText`) and "Save"
    - "Test connection" is drawn but disabled until T048
    - Back and swipe call `onSheetClosed`
  - Tab bodies are empty placeholders, filled by each story.

  Make `RoomsScreen.kt`'s `Message` and `StaleBanner` reachable from `ui/settings` (they are
  `internal`, same module: no change needed). Wire `entry<Destination.Settings>` in
  `AppNavigation.kt` to `SettingsScreen(viewModel { graph.settingsViewModel() })`. Build green.

**Checkpoint**: the app builds and all tests pass. Settings shows the Hub row, the tabs and an
address sheet that saves. The bottom-bar Sources item opens Settings on the Sources tab, which is
empty for now.

---

## Phase 3: User Story 1 - Turn rooms, groups and sources on and off (Priority: P1) 🎯 MVP

**Goal**: on the Rooms, Groups and Sources tabs, every room, group and configured source has a
switch. Turning off a playing room or group asks first. Failures say why.

**Independent Test**: turn one room, one group and one source off and back on. The hub reports
each change within 3 s, Rooms shows the room "Off", and a playing room asks first (quickstart §2
rows 1–8).

### Tests for User Story 1 (write first, see them fail)

- [X] T016 [P] [US1] Write `test/domain/SourceDetailTest.kt` for `sourceDetail(kind, uri)`
  (research R11):
  - Stream `https://stream.radioparadise.com/mp3-192` → "stream.radioparadise.com"
  - File `file:/home/x/morning.flac` → "morning.flac"
  - Line-in `alsa://plughw:CARD=ReceiverSolid,DEV=0` → "plughw:CARD=ReceiverSolid,DEV=0"
  - Line-in `signal://sine?frequency=440` → "sine?frequency=440"
  - Line-in `hw:1,0` (no scheme) → "hw:1,0"
  - blank or `null` → `null`
  - `addressDetail` (Now Playing) is unchanged: line-in still → `null`
- [X] T017 [P] [US1] Write `test/domain/SettingsBuilderTest.kt` (rooms, groups, configured sources)
  against `SettingsBuilder.build(snapshot, now, zone)` (data-model.md "Settings content").
  - **room status** (FR-009), each a test:
    - `!enabled` → `Off`
    - `!available` → `NotConnected`
    - both → `Off`
    - own route Active → `Playing(["Radio Paradise"])`
    - covered only by a group route → `Playing` with that source
    - own route and group route with different sources → both, in hub order
    - same source twice → once
    - `Failed`/`Starting`/`Unknown` routes count; a `Stopped` route doesn't
    - off while playing → `Off`
    - in one group → `InGroups(["Downstairs"])`
    - in two groups → A→Z
    - no group → `Speaker`
  - **group rows** (FR-010):
    - members in `memberIds` order with unknown ids skipped
    - no known members → empty list
    - own live route → `playing = [source]`
    - idle → empty
    - turned off with its own route → empty
    - members playing on their own → empty
  - **configured sources** (FR-011): origin `Configured` and `Unknown` are listed, `Runtime` isn't;
    `kind` from `inferSourceKind`; `detail` from `sourceDetail`. `ConfiguredSourceRow` has no
    removal field at all, so configured sources can never offer removal (FR-016); assert this with
    a source that is both configured and unused
  - **ordering**: rooms, groups and configured sources A→Z case-insensitive ("alpha" < "Beta"),
    with the id as tie-break
- [X] T018 [P] [US1] Write `test/domain/SettingsConfirmTest.kt` (turn-off part) for
  `turnOffConfirmation(key, snapshot)` and `keepsPlaying(sourceId, snapshot)` (research R4,
  data-model.md "Confirmations" table):
  - playing room → `TurnOffRoom("Living Room", ["Radio Paradise"])`
  - room playing only through a group → `TurnOffRoom` with that source
  - two sources on the room → both
  - idle room → `null`
  - room that is off (turning on) → `null`
  - group with its own route → `TurnOffGroup("Downstairs", sources, ["Living Room", "Kitchen"])`
  - group whose members play on their own → `null`
  - idle group → `null`
  - group that is off → `null`
  - any `ItemKind.Source` key → `null`
  - `keepsPlaying`: a live route uses the source → true; only a `Stopped` route → false; unused
    → false
- [X] T019 [P] [US1] Extend `test/ui/settings/SettingsTextTest.kt` (rows and dialog) with each
  string of the UI contract "Copy" table for:
  - `RoomStatus`: "Off", "Not connected", "Speaker", "Playing · A + B", "In Downstairs,
    Everywhere"
  - group members "Living Room, Kitchen" / "No rooms", and the group playing line
  - configured lines "Stream · stream.radioparadise.com", "File · morning.flac",
    "Line-in · plughw:…", and "Line-in" with a `null` detail
  - room dialog title and body, with one source and with two ("A and B is playing in Living
    Room. Turning the room off stops playback there.")
  - group dialog body
  - the "keeps playing" message "<name> is off. What's playing from it keeps playing."
  - the configured empty text "No sources in the hub's configuration."
- [X] T020 [P] [US1] Extend `test/ui/MessagesTest.kt` with `settingsFailure(SettingsAction.Turn(on),
  name, error)` → `settingsFailureMessage` (FR-013):
  - `Rejected(404)` → "<name> is no longer on the hub" and `needsRefresh = true`
  - `Unreachable` → "Couldn't reach the hub"
  - `Rejected(400)`, `Rejected(500)` and `Unexpected` → "Couldn't turn <name> off" (on = false)
    and "Couldn't turn <name> on" (on = true)

  No hub `title`/`detail` text can appear.
- [X] T021 [P] [US1] Create `test/data/KtorHubRepositorySettingsTest.kt` with contract tests 1, 2,
  6 (for the three PUTs) and 7 from [contracts/hub-repository.md](contracts/hub-repository.md):
  - exact method and path for `setRoomEnabled` / `setGroupEnabled` / `setSourceEnabled`
  - the body is exactly `{"enabled":false}` or `{"enabled":true}`
  - a 200 with the documented body, or an empty body → `Ok(Unit)`
  - id `"a b/c"` arrives path-encoded
  - 404 RFC 7807 → `Rejected(404, type, null, null)`
  - IO failure → `Unreachable`
  - no request to `/groups/{id}/volume`
- [X] T022 [US1] Write `test/ui/session/SettingsActionsTest.kt` (switch part) against
  `SettingsActions(scope, session, messages: AppMessages)` (research R5, data-model.md state
  diagram), with virtual time and `FakeRepository`:
  - `setEnabled(Room "a", off)` → `pending[a] = Pending(false, InFlight)` at once, then one
    `setRoomEnabled("a", false)` call
  - a second `setEnabled` on the same key while pending → ignored (one call)
  - on success → `AwaitingRefresh(fence = startedSeq at completion)` and a refresh requested; a
    snapshot from a refresh started **before** completion keeps the override; the first refresh
    started after it removes it, even if the hub still says on (the hub decides)
  - on failure → the override is removed at once and the FR-013 message is emitted; a 404 also
    requests a refresh
  - `keepsPlaying = true` on a source turned off successfully → the message "<name> is off.
    What's playing from it keeps playing."; on failure → only the failure message
  - **routing**: attached → messages arrive on `actions.messages`; detached → posted to
    `AppMessages` and not to `messages` (R7)
  - an address change clears every override
- [X] T023 [US1] Extend `test/ui/settings/SettingsViewModelTest.kt` (US1), failing first:
  - **rows**: `Lists` content carries the T017 rows with `shownEnabled` = override or hub value,
    `inFlight` while `InFlight`, and `controlsEnabled = false` when stale
  - **switch**:
    - turning on, or turning off an idle room, calls `SettingsActions.setEnabled` at once
    - turning off a playing room opens `confirm` with `TurnOffRoom` and sends nothing; the switch
      still shows on
    - `onConfirmCancel()` closes it and sends nothing
    - `onConfirm()` closes it and sends `setEnabled(…, false)`
    - for a group → `TurnOffGroup`
    - a source turned off while in use → no dialog, `keepsPlaying = true` passed
  - **dialog text follows the hub**: while `confirm` is open, a new snapshot with another source
    updates the text; a snapshot where nothing plays keeps the last text (spec Edge Cases); a
    snapshot where the item is gone also keeps the dialog and its last text, and "Turn off" still
    sends the request, whose 404 then gives "<name> is no longer on the hub"
  - taps are ignored while `controlsEnabled` is false or the row is in flight
  - `onVisible`/`onHidden` attach and detach `SettingsActions`; its `messages` set
    `state.message`, and `consumeMessage()` clears it

### Implementation for User Story 1

- [X] T024 [P] [US1] Add `fun sourceDetail(kind: SourceKind, uri: String?): String?` to
  `main/domain/SourceKind.kt`, next to `addressDetail`. Stream and File delegate to
  `addressDetail`. Line-in strips `scheme://`, or keeps the text as typed without one. Blank →
  `null`. KDoc: "the Settings row detail (research R11); Now Playing keeps `addressDetail`". Make
  T016 pass.
- [X] T025 [US1] Create `main/domain/SettingsBuilder.kt` with `SettingsContent`, `RoomRow`,
  `RoomStatus`, `GroupRow` and `ConfiguredSourceRow` (data-model.md), and
  `object SettingsBuilder { fun build(snapshot: HubSnapshot, now: Instant, zone: TimeZone):
  SettingsContent }`. Use `routesByRoom()` and `liveRoutes()` from `PlaybackRules.kt`. Leave
  `runtimeSources` empty (US2) and keep `now`/`zone` in the signature. Make T017 pass.
- [X] T026 [US1] Create `main/domain/SettingsConfirm.kt` with `ItemKind`, `ItemKey`, `Confirmation`
  (`TurnOffRoom`, `TurnOffGroup`; `Remove` comes in US2), `turnOffConfirmation()` and
  `keepsPlaying()`. Reuse the room and group status rules from `SettingsBuilder` (one rule, not a
  copy). Make T018 pass.
- [X] T027 [P] [US1] In `main/ui/settings/SettingsText.kt` add `roomStatusText`,
  `groupMembersText`, `groupPlayingText`, `configuredLine`, `confirmTitle`/`confirmBody` (room and
  group) and `keepsPlayingMessage`. Make T019 pass.
- [X] T028 [P] [US1] In `main/ui/Messages.kt` add `sealed interface SettingsAction { data class
  Turn(val on: Boolean); data object Remove }`, `SettingsFailure` (`NoLongerOnHub(name)`,
  `HubUnreachable`, `CouldNotTurn(name, on)`; removal cases come in US2), `settingsFailure(action,
  name, error)` with a `needsRefresh` flag, and `settingsFailureMessage(failure)`. The copy is
  verbatim from FR-013, with no trailing full stop. Make T020 pass.
- [X] T029 [US1] In `main/data/HubRepository.kt` add `setRoomEnabled`, `setGroupEnabled` and
  `setSourceEnabled` (KDoc per contracts/hub-repository.md). In `main/data/KtorHubRepository.kt`
  implement them with `hubCallUnit { outputs.setOutputEnabled(id, EnabledRequest(enabled)) }`, and
  likewise with `GroupsApi`/`InputsApi`. In `test/ui/rooms/FakeHub.kt` (`FakeRepository`) record
  them through `action(...)`. Make T021 pass; every existing suite stays green.
- [X] T030 [US1] Create `main/ui/session/SettingsActions.kt` (data-model.md "SettingsActions":
  `pending`, `messages`, `setEnabled(key, name, value, keepsPlaying)`, `attach()`/`detach()`).
  - It collects `session.state` to drop `AwaitingRefresh` overrides once `refreshSeq > fence`, and
    to clear everything on an address change.
  - Requests run in the app scope. Messages go to a `Channel(CONFLATED)` while attached, else to
    `AppMessages`.
  - In `main/AppGraph.kt` add `val settingsActions = SettingsActions(appScope, session, messages)`
    and pass it to `settingsViewModel()`.

  Make T022 pass.
- [X] T031 [US1] Extend `main/ui/settings/SettingsViewModel.kt` and `SettingsUiState.kt`:
  - **rows**: `Lists` content from `SettingsBuilder.build(snapshot, Clock.System.now(),
    TimeZone.currentSystemDefault())`, built once per snapshot as `RoomsViewModel.contentOf` does,
    merged with `SettingsActions.pending` into `shownEnabled`/`inFlight`
  - `onToggle(key, name, value)` with the `turnOffConfirmation` check and the `confirm` state
    (`key` + last `Confirmation`, refreshed per snapshot and kept when it becomes `null`)
  - `onConfirm`, `onConfirmCancel`, `consumeMessage`
  - attach and detach in `onVisible`/`onHidden`
  - inject `now: () -> Instant` and `zone: () -> TimeZone` for tests

  Make T023 pass.
- [X] T032 [P] [US1] Create `main/ui/settings/SonoraSwitch.kt`: a 48×28 track and a 22 dp thumb,
  coloured `switchTrackOff`/`switchThumbOff` off and `accent`/`onAccent` on, moving 150 ms. It is
  purely visual; the row owns the toggle semantics.
- [X] T033 [P] [US1] Create `main/ui/settings/ConfirmDialog.kt` (UI contract "Confirmation dialog"):
  - a `Dialog` holding the card: `surface`, r24, padding 24 20 20, gap 10
  - a tile 44 r14 `dangerContainer` with `StopOutline` in `danger`
  - the title Sora 20 sp 600, and the body 15 sp `textSoft` with 1.5 line height
  - two 52 dp buttons: "Keep playing" (`text` on `surfaceRaised`) and the confirm label (`onDanger`
    on `danger`)
  - `semantics { paneTitle = title }`, with the title and body read on open
  - Back and a tap outside call `onDismiss`
- [X] T034 [US1] Create `main/ui/settings/SettingRows.kt` with `SettingsCard` (`surface`, r22,
  padding 4 14, 1 dp `rowDivider` between rows) and these rows:
  - `RoomSettingRow`: min-h 64, `Room` tile, name 16 sp 600 (`textMuted` when off), status 13 sp
    in `accent` for Playing, else `textMuted`, and a switch
  - `GroupSettingRow`: min-h 72, `GroupStack` tile, members 13 sp ellipsis, an optional amber
    playing line, and a switch
  - `ConfiguredSourceRow`: min-h 60, kind tile 38 r10 via `forKind`/`iconForKind`, name 15 sp 600,
    a 12 sp one-line ellipsis line, and a switch

  Each row is `Modifier.toggleable(value = shownEnabled, role = Role.Switch, enabled =
  controlsEnabled && !inFlight)` with merged semantics and `contentDescription = name` (FR-023). It
  is dimmed to 0.5 when disabled.
- [X] T035 [US1] Fill the Rooms, Groups and Sources ("FROM CONFIGURATION" heading and card, or the
  configured empty text) tabs in `main/ui/settings/SettingsScreen.kt` with the intro texts of
  FR-009/FR-010 (13 sp textMuted). Show `ConfirmDialog` while `state.confirm != null`, with the
  "Turn off" label. Wire `onToggle`/`onConfirm`/`onConfirmCancel`. Build green.

**Checkpoint**: US1 works on its own: switches, the turn-off dialog and failure messages. The MVP
can be handed over for quickstart §2 rows 1–8 once the hub release is in.

---

## Phase 4: User Story 2 - Remove sources added at runtime (Priority: P2)

**Goal**: the Sources tab lists runtime sources under "Added from apps" with when they were added.
Removing an idle one is one tap; a playing one asks first.

**Independent Test**: play two links, stop one, remove both. The stopped one goes at once; the
playing one asks, then its playback stops (quickstart §2 rows 9–11).

### Tests for User Story 2 (write first, see them fail)

- [X] T036 [P] [US2] Extend `test/data/KtorHubRepositorySnapshotTest.kt` (and payloads in
  `test/data/Fixtures.kt`) with contract test 8:
  - `autoRemove` true / false / missing → `true` / `false` / `false`
  - `createdAt` `"2026-10-04T12:30:00Z"` → that `Instant`
  - `null`, missing, `""` and `"yesterday"` → `null`
  - the snapshot never fails because of it
- [X] T037 [P] [US2] Extend `test/data/KtorHubRepositorySettingsTest.kt` with contract test 3 and 6
  for `removeSource`:
  - `DELETE /api/v2/inputs/{id}`; 204 → `Ok(Unit)`
  - a 400 RFC 7807 body → `Rejected(400, type, null, null)`
  - 404 → `Rejected(404, …)`
  - IO failure → `Unreachable`
- [X] T038 [P] [US2] Write `test/domain/AddedLineTest.kt` for the added line (research R10), with
  `now` and `TimeZone.of("Europe/Kyiv")` fixed. `AddedLine` + `addedLineText` give:
  - the same local date → "Added today 14:30"
  - the previous local date → "Added yesterday 09:12", including across local midnight where UTC
    is still the same day
  - older → "Added 2 Oct 14:30"
  - a date across the last-Sunday-of-October DST change keeps the local wall time
  - `autoRemove` → + " · removed when it stops"
  - turned off → "Off · " + …
  - undated → "Off · removed when it stops", and the whole line `null` when nothing is left
- [X] T039 [P] [US2] Extend `test/domain/SettingsBuilderTest.kt` with runtime sources (FR-015):
  - only `origin == Runtime` is listed
  - newest `createdAt` first, undated last A→Z
  - each row carries `enabled`, `autoRemove` and the `AddedAt` value
- [X] T040 [P] [US2] Extend `test/domain/SettingsConfirmTest.kt` with `removeConfirmation`:
  - unused source → `null`
  - used by a room route → `Remove(name, ["Bedroom"])`
  - used by a group route → `["Downstairs"]`
  - used by both → both in hub order, without duplicates
  - only a `Stopped` route → `null`
- [X] T041 [P] [US2] Extend `test/ui/MessagesTest.kt` with `SettingsAction.Remove` (FR-018):
  - 400 → "<name> comes from the hub's configuration and can't be removed"
  - `Unreachable` → "Couldn't reach the hub"
  - 500 / `Unexpected` → "Couldn't remove <name>"
  - 404 → not a failure (`settingsFailure` returns `null`, so the caller treats it as removed)
- [X] T042 [P] [US2] Extend `test/ui/settings/SettingsTextTest.kt` with:
  - the remove dialog title "Remove <name>?" and body "<name> is playing in Bedroom and
    Downstairs. Removing it stops playback there." with the button "Remove"
  - the runtime empty text
  - the "Removing…" line
  - the trash label "Remove <name>"
- [X] T043 [US2] Extend `test/ui/session/SettingsActionsTest.kt` with removal (research R6):
  - `remove(id, name)` → `removing` contains the id and one `removeSource` call; a second call
    while removing → ignored
  - 204 → `removed` contains it and a refresh is requested; it stays hidden for a snapshot from a
    refresh started before completion, and is dropped from `removed` once a fenced snapshot no
    longer lists it
  - 404 → the same as success
  - 400 / unreachable / other → the T041 message, and the id back in neither set
- [X] T044 [US2] Extend `test/ui/settings/SettingsViewModelTest.kt` (US2):
  - runtime rows exclude `removed` ids and are marked `removing`
  - `onRemove(id)` on an unused source calls `SettingsActions.remove` at once
  - on a used source it opens `confirm` with `Remove` and sends nothing
  - `onConfirm` → `remove`; `onConfirmCancel` → nothing
  - the check uses the snapshot at the time of the tap (FR-017)
  - trash taps are ignored while stale

### Implementation for User Story 2

- [X] T045 [US2] In `main/domain/Models.kt` add `Source.autoRemove: Boolean = false` and
  `Source.createdAt: Instant? = null` (`kotlin.time.Instant`). In `main/data/ApiMapping.kt` map
  `autoRemove ?: false` and `createdAt?.let { runCatching { Instant.parse(it) }.getOrNull() }`
  (blank → `null`). Make T036 pass.
- [X] T046 [US2] Repository, domain and text, as four steps in this order, each a commit that
  makes its own tests pass before the next starts (one test-first unit at a time):
  1. **Data**: `main/data/HubRepository.kt` + `KtorHubRepository.kt`: `removeSource(sourceId) =
     hubCallUnit { inputs.deleteInput(sourceId) }`. `FakeRepository`: record it through `action`.
     Makes T037 pass.
  2. **Builder**: `main/domain/SettingsBuilder.kt`: add `RuntimeSourceRow`, `AddedLine`, `AddedAt`
     and the runtime list, built with `kotlinx.datetime` (`toLocalDateTime(zone)`, with today and
     yesterday compared on local dates). Makes the T038 values and T039 pass.
  3. **Confirmation**: `main/domain/SettingsConfirm.kt`: add `Confirmation.Remove` and
     `removeConfirmation()`, with `where` named via `describeTarget(...).title`. Makes T040 pass.
  4. **Copy**: `main/ui/settings/SettingsText.kt`: `addedLineText`, the remove dialog strings, the
     empty text and "Removing…" (English month abbreviations from a fixed table); and
     `main/ui/Messages.kt`: the removal cases. Makes the T038 strings, T041 and T042 pass.
- [X] T047 [US2] Extend `main/ui/session/SettingsActions.kt` with `removing`, `removed` and
  `remove(id, name)` (fenced like the switches). Extend `SettingsViewModel` with the runtime rows,
  `onRemove` and the `Remove` confirmation. Make T043–T044 pass.
- [X] T048 [US2] In `main/ui/settings/SettingRows.kt` add `RuntimeSourceRow`:
  - min-h 64, card padding 4 6 4 14, link tile, name 15 sp 600, the added line 12 sp textMuted
    ("Removing…" while removing)
  - a 44×44 trash `IconButton` in `textSoft` labelled "Remove <name>", disabled while stale or
    removing
  - the row dimmed while removing

  In `SettingsScreen.kt` add the "ADDED FROM APPS" heading (margin-top 4) and one card with every
  runtime row, or the empty text. Show `ConfirmDialog` with the "Remove" label for
  `Confirmation.Remove`. Build green.

**Checkpoint**: US1 and US2 both work. The Sources tab is complete.

---

## Phase 5: User Story 3 - Change and test the hub address (Priority: P2)

**Goal**: "Test connection" checks a drafted address without saving it, and "Set your hub
address" elsewhere opens the sheet directly.

**Independent Test**: test a wrong address (fails), test the right one ("Hub found · N rooms"),
save, and Rooms loads (quickstart §2 rows 13–15).

### Tests for User Story 3 (write first, see them fail)

- [ ] T049 [P] [US3] Extend `test/data/KtorHubRepositorySettingsTest.kt` with contract tests 5 and 6
  for `countRooms`:
  - `GET /api/v2/outputs?includeDisabled=true`
  - three outputs, one disabled → `Ok(3)`; `[]` → `Ok(0)`
  - an HTML 200 body → `Unexpected`; a JSON object 200 body → `Unexpected`
  - 500 → `Rejected(500…)`
  - answering after 4 s (virtual time) → `Unreachable`
  - IO failure → `Unreachable`
- [ ] T050 [P] [US3] Extend `test/ui/settings/SettingsTextTest.kt`: "Checking…", "Hub found · 1
  room", "Hub found · 5 rooms" and "Can't reach the hub at this address".
- [ ] T051 [US3] Extend `test/ui/settings/SettingsViewModelTest.kt` (US3). The view model now takes
  `repositoryFactory: HubRepositoryFactory`; use `FakeFactory`.
  - `onTest()` with an invalid draft → the 001 message, no repository created, nothing saved
  - a valid draft → `test = Checking`, then `countRooms()` on a repository created for the
    **normalised draft** (not the session's): `Ok(5)` → `Found(5)`; `Unreachable`, `Rejected(503)`
    and `Unexpected` → `Failed`
  - `countRooms` taking 4 s with the 3 s limit is the repository's job; here a delayed `Err` →
    `Failed`
  - nothing is saved in any case
  - `onDraftChange` during `Checking` → the test is cancelled and the result cleared
  - `onSave` and `onSheetClosed` cancel it too
  - a second `onTest` replaces the first

### Implementation for User Story 3

- [ ] T052 [US3] `main/data/HubRepository.kt` + `KtorHubRepository.kt`: `countRooms()` =
  `hubCall { outputs.listOutputs(includeDisabled = true) }`, mapped to the count of entries whose
  `toRoom()` is non-null. `FakeRepository`: `var countRoomsResult: () -> HubResult<Int>` with a
  delay. In `SettingsText.kt` add `testText(test)`. Make T049–T050 pass.
- [ ] T053 [US3] In `SettingsViewModel` add `onTest()` with one cancellable test job and the
  cancellation rules of T051. In `AppGraph.settingsViewModel()` pass `repositoryFactory`. Make T051
  pass.
- [ ] T054 [US3] In `main/ui/settings/HubAddressSheet.kt`:
  - enable "Test connection" (`text` on `surfaceRaised`)
  - add the status box: padding 12 14, r14, 14 sp 500, an 8 dp dot, `textSoft`/`surfaceRaised`
    while checking, `positive`/`positiveContainer` when found, `danger`/`dangerContainer` when
    failed, announced as a polite live region

  In `main/ui/nav/AppNavigation.kt` change Rooms' and Start Playback's `onOpenSettings` to
  `{ graph.settingsNavigator.openSheet(); backStack.selectTab(Destination.Settings) }` (spec Edge
  Cases: "opens Settings with the sheet already open"). Build green.

**Checkpoint**: US1–US3 work. The hub address is fully managed from Settings.

---

## Phase 6: User Story 4 - See which extensions the hub runs (Priority: P3)

**Goal**: a read-only Extensions tab with a badge and connection line per extension, kept current
while the tab is open.

**Independent Test**: open the tab and compare every row with `GET /api/v2/extensions`
(quickstart §2 row 12).

### Tests for User Story 4 (write first, see them fail)

- [ ] T055 [P] [US4] Extend `test/data/KtorHubRepositorySettingsTest.kt` with contract tests 4 and 6
  for `extensions`, using the live sample of 2026-10-04 (5 extensions; copy the JSON into
  `test/data/Fixtures.kt`):
  - it maps to 5 `Extension`s, with `ACTIVE`/`CONNECTED` and `NOT_APPLICABLE` mapped
  - `INERT` → `Inactive`
  - `"SOMETHING_NEW"` status or connection → `Unknown`, and the call still succeeds
  - missing `name` → the id; blank id and name → dropped
  - `{"loadingEnabled":false,"extensions":[]}` → `loadingEnabled = false`
  - missing `loadingEnabled` → `true`
  - IO failure → `Unreachable`
- [ ] T056 [P] [US4] Write `test/domain/ExtensionRowsTest.kt` for `extensionRows(inventory)`
  (FR-019, FR-020):
  - **badge** = status for each of Active / Disabled / Rejected / Inactive / Unknown
  - **line**: Rejected → `CouldNotLoad`, Disabled → `TurnedOffInConfig` and Inactive → `NotInUse`,
    whatever the connection; Active + Connected / Disconnected / NotApplicable / Unknown →
    `Connected` / `Disconnected` / `NoConnectionNeeded` / `ConnectionUnknown`
  - A→Z by name
  - `loadingEnabled = false` → `LoadingOff`; empty → `Empty`
- [ ] T057 [P] [US4] Extend `test/ui/settings/SettingsTextTest.kt`:
  - the badges "Active", "Disabled", "Rejected", "Inactive", "Unknown"
  - the lines "Couldn't be loaded", "Turned off in configuration", "Not in use", "Connected",
    "Disconnected", "No connection needed", "Connection unknown"
  - the tab texts: the read-only note, "Extensions are turned off in the hub's configuration." and
    "No extensions installed on the hub."
- [ ] T058 [US4] Extend `test/ui/settings/SettingsViewModelTest.kt` (US4) (research R8):
  - on the Extensions tab, `extensions()` is called once when the tab is shown and once after each
    successful session refresh
  - on any other tab, and while hidden, it is never called
  - before the first answer → `extensions = null`
  - a failure keeps the last inventory and shows no message
  - switching away and back calls it again at once

### Implementation for User Story 4

- [ ] T059 [US4] In `main/domain/Models.kt` add `ExtensionInventory`, `Extension`,
  `ExtensionStatus` and `ExtensionConnection` (data-model.md "Extensions"). In
  `main/data/ApiMapping.kt` map `ExtensionInventory`/`Extension` per research R8 (no
  `rejectionReason`). In `main/data/HubRepository.kt` + `KtorHubRepository.kt` add `extensions()`
  via `ExtensionsApi.listExtensions()`. `FakeRepository`: add `var extensionsResult` and a call
  counter. Make T055 pass.
- [ ] T060 [US4] In `main/domain/SettingsBuilder.kt` add `ExtensionsContent`, `ExtensionRow`,
  `ExtensionBadge`, `ExtensionLine` and `extensionRows()`. In `SettingsText.kt` add the badge and
  line texts and the tab texts. Make T056–T057 pass.
- [ ] T061 [US4] In `SettingsViewModel` fetch the extensions as T058 describes: a job that runs
  while visible and on the Extensions tab, collecting `session.state` changes of `refreshSeq` on
  `Live`. Expose `extensions: ExtensionsContent?`. Make T058 pass.
- [ ] T062 [US4] In `SettingRows.kt` add `ExtensionRow`: min-h 60, name 15 sp 600, the line 12 sp
  textMuted, and the badge pill (padding 4 10, 12 sp 600, 6 dp dot). Its colours are `positive` on
  `positiveContainer` (Active), `danger` on `dangerContainer` (Rejected), and `textMuted` on
  `surfaceRaised` otherwise. The row is not clickable. In `SettingsScreen.kt` fill the Extensions
  tab: the read-only note, then the card, the off text or the empty text. Build green.

**Checkpoint**: every story works on its own and together.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [ ] T063 [P] Sync `AGENTS.md` (research R14):
  - Endpoint table "Settings" row: add `GET /api/v2/outputs?includeDisabled=true` (connection
    test) and note that extensions are fetched only on the Extensions tab.
  - "Project layout": add `SettingsActions` and `SettingsNavigator` to the `ui/session/` comment.
  - "Scope of the first feature" item 4: leave it as history.
  - Check that no text still calls Sources a placeholder.
- [ ] T064 [P] Accessibility pass against [UI contract "Accessibility"](contracts/settings-ui.md):
  - rows are announced as switches with name and state, and as disabled when stale or in flight
  - tabs are `Role.Tab` + `selected`
  - the hub row label is right
  - trash buttons read "Remove <name>"
  - the sheet's Close reads "Close"
  - the test status is a live region
  - the dialog `paneTitle` is set
  - every target is ≥ 44 dp

  Fix any gap in `main/ui/settings/`.
- [ ] T065 [P] Design pass: compare each tab, the sheet and the dialog with
  `design/screens/Settings.dc.html` (sizes, radii, gaps, tokens). Only the spec's listed departures
  are allowed: sorting, footer wording and position, undrawn states (including "Change address"
  when unreachable), extension wording, one card for runtime rows, and the platform dialog dim.
- [ ] T066 Run [quickstart.md](quickstart.md) §1: `./gradlew :androidApp:assembleDebug
  :composeApp:testAndroidHostTest :composeApp:allTests :composeApp:check`, reading Gradle's own
  exit code. Everything MUST be green, with no generated files in `git status`.
- [ ] T067 Create `specs/004-settings-sources/verification.md` from 003's layout (build, device,
  one row per quickstart §2 step, "Issues found"). Leave the results empty for the local session.
  Note at the top that §2 waits for the hub release and starts with `./gradlew refreshOpenApi`
  (plan "Open points").

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: T001 first (version commit), then T002, then T003.
- **Foundational (Phase 2)**: needs Phase 1.
  - T004 → T005
  - T006 → T007 → T008 (T008 needs T005)
  - T009 → T010
  - T011 → T012
  - T013 → T014 (needs T005, T012) → T015 (needs T008, T010, T014)
- **US1 (Phase 3)**: needs Phase 2.
  - Tests T016–T021 in parallel. T022 and T023 can be written alongside them, but they reference
    types from T028 (`SettingsFailure`) and T030 (`SettingsActions`), so they compile only once
    those exist: seeing them fail means failing assertions after T028/T030's stubs, not a compile
    error.
  - Then T024 → T025 → T026, with T027 and T028 in parallel, and T029.
  - T030 needs T028 and T029; T031 needs T025–T030.
  - T032 ∥ T033 ∥ T034 (T034 needs T032); T035 needs T031–T034.
- **US2 (Phase 4)**: needs US1 (`SettingsActions`, the view model, `SettingRows`,
  `ConfirmDialog`).
  - T036–T042 in parallel; T043 and T044 after them.
  - T045 → T046 → T047 → T048.
- **US3 (Phase 5)**: needs Phase 2 only (the sheet). Independent of US1/US2 except for edits to
  the same view model file: do it after US2 or merge carefully.
  - T049 ∥ T050 → T051 → T052 → T053 → T054.
- **US4 (Phase 6)**: needs Phase 2 only, with the same file caveat.
  - T055 ∥ T056 ∥ T057 → T058 → T059 → T060 → T061 → T062.
- **Polish (Phase 7)**: needs every story. T063 ∥ T064 ∥ T065 → T066 → T067.

### User Story Dependencies

- **US1 (P1)**: Foundational only.
- **US2 (P2)**: US1. Its domain and data parts (T036–T042, T045–T046) can start right after
  Foundational.
- **US3 (P2)**: Foundational only.
- **US4 (P3)**: Foundational only.

### Within Each User Story

- Tests first, seen failing → pure domain → data → session/view model → composables → wiring.

### Parallel Opportunities

- Phase 2: T004 ∥ T006 ∥ T009 ∥ T011 ∥ T013.
- US1: T016–T021 together, then T024 ∥ T027 ∥ T028 ∥ T029, then T032 ∥ T033.
- US2: T036–T042 together.
- US3: T049 ∥ T050. US4: T055 ∥ T056 ∥ T057.
- Across stories, once Foundational is done: the domain and data tasks of US2 (T036–T042),
  US3 (T049–T050, T052's repository part) and US4 (T055–T057, T059–T060) can run alongside US1's
  UI work. View-model and screen tasks of different stories touch the same files and run one
  after another.
- Polish: T063 ∥ T064 ∥ T065.

---

## Parallel Example: User Story 1

```bash
# Tests first (all different files):
Task: "Write SourceDetailTest in composeApp/src/commonTest/kotlin/sonora/multiroom/mobile/domain/SourceDetailTest.kt"
Task: "Write SettingsBuilderTest in composeApp/src/commonTest/kotlin/sonora/multiroom/mobile/domain/SettingsBuilderTest.kt"
Task: "Write SettingsConfirmTest in composeApp/src/commonTest/kotlin/sonora/multiroom/mobile/domain/SettingsConfirmTest.kt"
Task: "Extend SettingsTextTest in composeApp/src/commonTest/kotlin/sonora/multiroom/mobile/ui/settings/SettingsTextTest.kt"
Task: "Extend MessagesTest in composeApp/src/commonTest/kotlin/sonora/multiroom/mobile/ui/MessagesTest.kt"
Task: "Write KtorHubRepositorySettingsTest in composeApp/src/commonTest/kotlin/sonora/multiroom/mobile/data/KtorHubRepositorySettingsTest.kt"

# Then independent implementations:
Task: "Add sourceDetail in composeApp/src/commonMain/kotlin/sonora/multiroom/mobile/domain/SourceKind.kt"
Task: "Add row and dialog texts in composeApp/src/commonMain/kotlin/sonora/multiroom/mobile/ui/settings/SettingsText.kt"
Task: "Add SettingsFailure in composeApp/src/commonMain/kotlin/sonora/multiroom/mobile/ui/Messages.kt"
Task: "Create SonoraSwitch and ConfirmDialog in composeApp/src/commonMain/kotlin/sonora/multiroom/mobile/ui/settings/"
```

---

## Implementation Strategy

### MVP First

1. Phase 1 → Phase 2. The Settings frame replaces the stub, and the address is still editable.
2. US1: on/off for rooms, groups and configured sources, with the turn-off dialog.
3. **Stop and validate**: build, run all tests, and hand over the APK. Device rows 1–4 and 8 can
   run now. Rows 5–7 wait for the hub release.

### Incremental Delivery

1. Setup + Foundational → new Settings frame, no Sources placeholder.
2. US1 → switches (MVP).
3. US2 → runtime-source cleanup; the Sources tab is complete.
4. US3 → the connection test, and "Set your hub address" opening the sheet.
5. US4 → the Extensions tab.
6. Polish → AGENTS.md, accessibility, design pass, verification sheet.

---

## Notes

- [P] = different files, no dependency on unfinished tasks.
- Verify each test fails before implementing (Constitution IV).
- Never commit `composeApp/build/` or hand-edit generated code (Constitution I).
- The app never decides that a playback stopped. After a turn-off or removal, Rooms and Now
  Playing follow the hub at their next refresh.
- Quickstart §2 starts with `./gradlew refreshOpenApi` after the hub release. If the contract
  changed, reconcile it before running the device rows, and ask before changing behaviour.
