---

description: "Task list for 002 Now Playing and Move to room"
---

# Tasks: Now Playing and "Move to room…"

**Input**: Design documents from `specs/002-now-playing-move-to-room/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md),
[data-model.md](data-model.md), [contracts/hub-repository.md](contracts/hub-repository.md),
[contracts/now-playing-ui.md](contracts/now-playing-ui.md), [quickstart.md](quickstart.md)

**Tests**: REQUIRED. Constitution IV (test-first for logic, `MockEngine` repository tests) and spec
FR-025. Every test task comes before its implementation task and MUST be seen failing first.

**Organization**: grouped by user story (spec priorities: US1 P1, US2 P2, US3 P2).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on an unfinished task)
- **[Story]**: US1–US3 from spec.md

## Path Conventions

- Shared code: `composeApp/src/commonMain/kotlin/sonora/multiroom/mobile/` (abbreviated `main/` below)
- Shared tests: `composeApp/src/commonTest/kotlin/sonora/multiroom/mobile/` (abbreviated `test/` below)
- Generated client (never committed or edited): `composeApp/build/generated/openapi/`, package `sonora.multiroom.mobile.hub.generated`

## General rules for every task

- Nothing Android-only in `commonMain`/`commonTest`. `ui/` and `domain/` never import the generated
  package (`verifyLayering`).
- Colours, typography and shapes only through `SonoraTheme` tokens. Add a token to `ui/theme/Tokens.kt`
  for any design colour used twice that has none (e.g. `#C9CBD1`, `#E4E3DF`, `#2A1F10`, `#3A3E46`).
- Never call `PUT /api/v2/groups/{id}/volume` (FR-015, SC-005).
- The existing 001 test scenarios MUST stay green. A suite may be moved or adapted, but no scenario
  may be dropped.
- If a task needs a decision none of the design documents cover, stop and ask (AGENTS.md "Workflow").
- Commit after each task or logical group with a Conventional Commit message that explains why.

---

## Phase 1: Setup

**Purpose**: version bump and the one new dependency.

- [ ] T001 Bump the app version as the **first commit** of this feature (AGENTS.md "Workflow"): in
  `gradle.properties` set `sonora.versionName=0.2.0-alpha` and `sonora.versionCode=2`. Run
  `./gradlew :androidApp:assembleDebug` and check that `AppVersionTest` still passes (it must not
  hard-code 0.1.0).
- [ ] T002 Add `lifecycle-viewmodel-navigation3 = { module = "org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-navigation3", version.ref = "lifecycle" }`
  and `lifecycle-viewmodel-savedstate = { module = "org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-savedstate", version.ref = "lifecycle" }`
  to `gradle/libs.versions.toml` (2.11.0, research R2; re-check Maven Central for a newer **stable**
  2.11.x patch and record any change in research.md R2). Add both to `commonMain` dependencies in
  `composeApp/build.gradle.kts`.
  - Install the decorators in `main/ui/nav/AppNavigation.kt`:
    `entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator())`
    (use the exact factory names the 2.11.0 artifact exposes).
  - Entry scoping moves the Rooms and Settings view models into their entries' stores (research
    R2). Change `AppBackStack.selectTab()` in `main/ui/nav/Destinations.kt` so it never removes
    the Rooms root: it removes only the entries above it, then adds the tab unless the tab is Rooms.
  - Extend `test/ui/nav/AppBackStackTest.kt` (failing first): after Rooms → Settings → Rooms, and
    after Rooms → NowPlaying → Settings → Rooms, `stack[0]` is the **same instance** as before
    (`assertSame`), and the existing tab tests still pass.

  `./gradlew :androidApp:assembleDebug` is green.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: shared session, shared volume controller, shared domain helpers and the repository
additions that every story uses. These are refactors with no visible behaviour change, except for
the new repository methods.

**⚠️ CRITICAL**: no user story work begins until this phase is complete.

### Refactor: RoomsBuilder helpers (no behaviour change)

- [ ] T003 In `main/domain/RoomsBuilder.kt`, extract the private `describe(...)`, `statusAndAction(...)` and
  the live-route filter (`status != Stopped`) into `internal` top-level functions in a new
  `main/domain/PlaybackRules.kt`: `liveRoutes(snapshot)`, `describeTarget(target, rooms, groups, masterMuted)`
  (returns the existing `Described` shape, renamed `TargetDescription`, made `internal`), and
  `cardStatus(route, uri): CardStatus` (status only, with the action mapping kept in `RoomsBuilder`).
  Also add `occupancy(snapshot): Map<String, Route>` (room id → the live route occupying it; group
  routes occupy every known member; `Target.Unknown` occupies none). `RoomsBuilder` uses these
  helpers. `RoomsBuilderTest` passes unchanged.

### Shared hub session (research R1, R4)

- [ ] T004 Write `test/ui/session/HubSessionTest.kt`, failing first. **Move** every polling and
  connection scenario out of `test/ui/rooms/RoomsViewModelPollingTest.kt` and the "no address → no
  requests" scenario out of `RoomsViewModelAddressTest.kt`, rewritten against `HubSession`:
  - a poll every 2.5 s with no overlap
  - a 3 s timeout → `Connection.Unreachable(lastSuccessAt)`
  - stale state keeps the last `snapshot`
  - `requestRefresh()` never overlaps a running refresh
  - no address → zero requests

  Add new cases:
  - holder count 0 → zero requests
  - `acquire()` from 0 refreshes immediately
  - two `acquire()` and one `release()` → still polling
  - release to 0 cancels an in-flight refresh
  - an address change clears `snapshot` and restarts
  - sequence numbers (research R1): `session.startedSeq` increments just before each refresh
    request starts. `Connected.refreshSeq` equals the `startedSeq` of the refresh whose result the
    state carries.
  - **fence case**: with a refresh in flight (`snapshotDelayMs`), capture
    `c = session.startedSeq`. That in-flight refresh's state has `refreshSeq == c`, which does
    **not** pass `refreshSeq > c`; the next refresh's state does.

  Use `TestScope`/virtual time and the existing `FakeRepository`.
- [ ] T005 Implement `main/ui/session/HubSession.kt`. It takes
  `(addressStore: HubAddressStore, repositoryFactory: HubRepositoryFactory, scope: CoroutineScope, now: () -> Long, pollIntervalMillis: Long = 2500)`
  and exposes:
  - `state: StateFlow<SessionState>`, where `SessionState` is `Initial | NoAddress |
    Connected(address, connection, snapshot: HubSnapshot?, refreshSeq: Long)` per data-model.md
    "Session"
  - `val startedSeq: Long` (the latest refresh started, not part of the state)
  - `val repository: HubRepository?`
  - `fun requestRefresh()`, `fun acquire()`, `fun release()`

  Move the loop, the conflated `refreshNow` channel and the `lastSuccessAt` bookkeeping verbatim
  from `RoomsViewModel`. Move `Connection` from `main/ui/rooms/RoomsUiState.kt` to
  `main/ui/session/Connection.kt`, updating imports. Make T004 pass.
- [ ] T006 [P] Implement `main/ui/session/AppMessages.kt`: `class AppMessages { fun post(text: String); val messages: Flow<String> }`
  backed by `Channel<String>(Channel.CONFLATED)` received as a flow (one consumer). Test it in
  `test/ui/session/AppMessagesTest.kt`: one post is delivered once, and the latest wins when
  several are posted before collection.
- [ ] T007 Move Rooms onto the session:
  - `RoomsViewModel(session: HubSession, messages: AppMessages, now: () -> Long = …)` derives
    `RoomsUiState` from `session.state` with `RoomsBuilder.build`, and keeps `volumeOverrides`,
    `inFlight` and `message` as today.
  - `RoomsScreen.kt`'s `repeatOnLifecycle(STARTED)` calls `session.acquire()`/`release()` (through
    view-model methods `onVisible()`/`onHidden()`).
  - Collect `messages.messages` into `RoomsUiState.Connected.message`.
  - Actions use `session.repository` and `session.requestRefresh()`.
  - `AppGraph` creates one `HubSession` (scope:
    `CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)`) and one `AppMessages`, and
    passes them to `roomsViewModel()`.

  Adapt `RoomsViewModelAddressTest`, `RoomsViewModelControlsTest` and the rest of
  `RoomsViewModelPollingTest` to build a `HubSession` on the test scheduler. Add one test: text
  posted to `AppMessages` appears as the Rooms `message`. All 001 scenarios stay green.

### Shared volume controller (research R5)

- [ ] T008 Write `test/ui/session/VolumeDragControllerTest.kt`, failing first. **Move** the volume
  scenarios from `RoomsViewModelControlsTest.kt` that test drag mechanics, rewritten against the
  controller:
  - ≤ 4 sends/s while dragging, plus a final send of the release value
  - a value already sent is not resent
  - dragging back to the start restores the hub values
  - a drag starting right after a release starts from the settled targets and queues behind the
    release's send
  - pending values beat refreshed hub values until a refresh that **started after** the final send
    succeeds
  - a pending value is dropped when its room disappears
  - a failed send calls the error callback once and clears the pending values

  Add new cases:
  - a member drag (`base = {kitchen: 35}`) changes only `kitchen`, and the group max (from
    `{living: 70, kitchen: 35}` overlaid with pending) updates when kitchen passes 70
  - a group drag 70→35 on `{living: 70, kitchen: 35}` makes the pending values `{living: 35,
    kitchen: 18}`
  - a member drag right after a group release uses the group's settled target for that member as
    its base

  A `FakeRepository` handler fails the test on any `/groups/{id}/volume`.
- [ ] T009 Implement `main/ui/session/VolumeDragController.kt`. It takes
  `(scope: CoroutineScope, session: HubSession, onError: (targetName: String, HubError) -> Unit, throttleMillis: Long = 250)`.
  - `pending: StateFlow<Map<String, Int>>`, keyed by **room id**
  - `fun start(key: String, targetName: String, base: Map<String, Int>)`,
    `fun drag(key: String, value: Int)`, `fun end(key: String, value: Int)`
  - at each final send, capture `session.startedSeq` per room
  - `fun onRefresh(refreshSeq: Long, knownRoomIds: Set<String>)` drops rooms whose captured value
    is `< refreshSeq` (confirmed) or that are not in `knownRoomIds` (vanished). Never capture
    `state.refreshSeq` (research R1 fence rule).
  - `fun clear()` for an address change
  - `fun shown(roomIds: Collection<String>, hub: Map<String, Int>): Int` returns the max over the
    rooms of pending-else-hub

  Port `Drag`/`Settled`/`sendVolume` from `RoomsViewModel` and change them from per-card to
  per-room. Sending uses `GroupVolume.scale(base, value)` and `HubRepository.setRoomVolume` only.
  Make T008 pass.
- [ ] T010 Move `RoomsViewModel` onto `VolumeDragController`:
  - Replace `volumeOverrides` with the controller's `pending` and compute each card's shown value
    with `shown(...)` (group: member ids from `memberVolumes.keys`; single room: `roomId`).
  - Call `onRefresh` from the session's successful refreshes.
  - Map `onError` to `actionErrorMessage(UserAction.Volume, …)`.

  `RoomsViewModelControlsTest` (remaining non-mechanics scenarios: stale disables, in-flight
  blocks, failure messages, the group drag never hits `/groups/{id}/volume`) is green, and so is
  the Rooms UI.

### Shared domain helpers

- [ ] T011 [P] Write `test/domain/NamesTest.kt` (failing), then `main/domain/Names.kt` with
  `fun joinNames(names: List<String>): String`: `[]` → `""`, `[A]` → `"A"`, `[A, B]` → `"A and B"`,
  `[A, B, C]` → `"A, B and C"`.
- [ ] T012 [P] Write `test/domain/AddressDetailTest.kt` (failing), then add to
  `main/domain/SourceKind.kt` (research R7, the only place this is derived):
  - `fun kindLabel(kind: SourceKind): String`: "Stream", "Line-in", "File", "Link".
  - `fun addressDetail(kind: SourceKind, uri: String?): String?`:
    - Stream/Link with `http(s)` → host only (strip scheme, user-info, port, path, query):
      `https://stream.radioparadise.com/aac-320` → `stream.radioparadise.com`,
      `http://u:p@host:8000/x` → `host`, `http://[::1]:8000/` → `::1`.
    - Link with a non-http address → the address as typed (trimmed).
    - File: `file:///music/Morning.mp3` → `Morning.mp3`, `/music/a b.flac` → `a b.flac`,
      `C:\music\x.wav` → `x.wav` (no percent-decoding).
    - LineIn → `null`.
    - Blank or null → `null`. Unparseable (e.g. `http://`) → the trimmed address as typed.

### Repository additions (contracts/hub-repository.md)

- [ ] T013 Extend `test/data/KtorHubRepositoryActionsTest.kt` (and the payloads in `test/data/Fixtures.kt`)
  with contract tests 1–4 from contracts/hub-repository.md, failing first:
  - `setRoomMute("bedroom", true/false)` → `PUT /api/v2/outputs/bedroom/mute` `{"muted":…}`
  - `setGroupMute("downstairs", …)` → `PUT /api/v2/groups/downstairs/mute`
  - `transferRoute("r1", Target.Room("kitchen"))` → `POST /api/v2/routes/r1/transfer`
    `{"targetId":"kitchen","targetType":"SINGLE_OUTPUT"}`, and `Target.Group` → `OUTPUT_GROUP`
  - a 200 `RouteResponse` with `routeId: "r2"` → `Ok(Route(id = "r2", …))`
  - RFC 7807 400/404/422 → `Rejected(status, type)`
  - an IO failure → `Unreachable`
  - a 200 garbage body → `Unexpected`
  - `Target.Unknown` → throws `IllegalArgumentException` with no request recorded
- [ ] T014 Add `setRoomMute`, `setGroupMute` and `transferRoute(routeId, target): HubResult<Route>` to
  `main/data/HubRepository.kt` (with KDoc per the contract) and implement them in
  `main/data/KtorHubRepository.kt` via the generated `OutputsApi.setOutputMute`,
  `GroupsApi.setGroupMute` and `RoutesApi.transferRoute`. Map the response with the existing
  `RouteResponse.toRoute()`, where `null` → `Unexpected`. Add the three methods to
  `FakeRepository` in `test/ui/rooms/FakeHub.kt` (recorded as `Call`s;
  `transferResult: () -> HubResult<Route>`, configurable). Make T013 pass.
- [ ] T015 [P] Extend `test/ui/MessagesTest.kt` (failing), then `main/ui/Messages.kt`: add
  `UserAction.Mute(on: Boolean)` and `UserAction.Move(destination: String)` with exactly the copy in
  contracts/hub-repository.md "User-facing messages":
  - Mute: "Couldn't mute X. Can't reach the hub." / "X is no longer on the hub." /
    "Couldn't mute X." (and the unmute variants)
  - Move: "Couldn't move X to D. Can't reach the hub." for `Unreachable`, and "Couldn't move X to D."
    for every other error, including 404

**Checkpoint**: Rooms behaves exactly as in 001 on the shared session and controller. The
repository can mute and transfer, and all tests are green.

---

## Phase 3: User Story 1 - See and control one playback in detail (Priority: P1) 🎯 MVP

**Goal**: Tapping a Rooms card opens a real Now Playing screen showing the source, kind and
address, status and target. It offers Stop for every route, Pause/Resume when pauseable and the
live-stream line, follows hub changes and returns to Rooms when the playback ends.

**Independent Test**: With a pauseable route, a live stream and a line-in route on the hub, open
each from Rooms, compare the screen with the hub, pause/resume and stop each, and stop one from
another client (quickstart §2 steps 2–5).

### Tests for User Story 1 ⚠️ write first, see them fail

- [ ] T016 [P] [US1] Write `test/domain/NowPlayingBuilderTest.kt` (content part, `volume` ignored
  here). Cover each rule from data-model.md "Validation / rules":
  - absent route → `Gone`; `STOPPED` → `Gone`
  - `FAILED` → status Failed, `moveVisible = false`, `pauseEnabled = false`
  - "A live stream → `status = Playing`, `live = true`" (non-pauseable + `https://…`)
  - line-in (non-pauseable, no http) → `live = false`, `pauseVisible = false`
  - pauseable Active paused → status Paused, `paused = true`, `pauseEnabled = true`,
    `moveVisible = false`
  - pauseable Active → Playing, `pauseEnabled = true`, `moveVisible = transferable`
  - Starting / Stopping / Unknown → `pauseEnabled = false`, `moveVisible = false`
  - `kindLabel` and `addressDetail` filled from the source; a missing source → `sourceName = inputId`
  - target line: room → name; group → name + `memberNames` "in `outputIds` order, skipping unknown
    ids"; a missing room/group → the id; `Target.Unknown` → the id
  - `notConnected` when the room or any listed member has `available = false`
- [ ] T017 [P] [US1] Write `test/ui/nowplaying/NowPlayingViewModelTest.kt` (US1 part) with
  `HubSession` + `FakeRepository` on virtual time:
  - opening shows the session's existing snapshot before any new request
  - `onVisible()` acquires polling
  - Stop → `stopRoute(id)`; `Ok` → `exit = Stopped` with nothing posted to `AppMessages`
  - Stop → `Rejected(404)` → `exit = Ended(name)` and "Playback on <name> ended" posted
  - Stop → `Unreachable` → stays open, message "Couldn't stop <name>. Can't reach the hub."
  - the route disappears from a refresh → `exit = Ended`, message posted
  - a route id absent from the first snapshot → `Ended`
  - Pause/Resume → `setRoutePaused(id, true/false)`
  - a repeated tap while in flight sends nothing (FR-013)
  - Pause tapped while `!pauseEnabled` sends nothing
  - stale (`Unreachable`) → every action is ignored and the state reports controls disabled
  - after any action a refresh is requested

### Implementation for User Story 1

- [ ] T018 [US1] Implement `main/domain/NowPlayingBuilder.kt` with the `NowPlayingContent`,
  `TargetLine`, `VolumeSection`, `PillModel` and `MuteModel` types exactly as in data-model.md.
  `build(snapshot, routeId)` uses the T003 helpers (`liveRoutes`, `describeTarget`, `cardStatus`)
  and maps `CardStatus.LiveStream` → `Playing` + `live = true`. Leave `volume = null` for now
  (T024 fills it). Make T016 pass.
- [ ] T019 [US1] Implement `main/ui/nowplaying/NowPlayingUiState.kt` and
  `main/ui/nowplaying/NowPlayingViewModel.kt`. The view model takes
  `(routeId: String, savedState: SavedStateHandle, session: HubSession, messages: AppMessages)`:
  - It holds `followedRouteId` in `savedState["followedRouteId"]` (initialised to `routeId` when
    absent, research R3) and derives `NowPlayingUiState` from
    `session.state` per data-model.md "View-model state". `sheet`, `pending` and the Mute/Move
    actions stay unused until US2/US3.
  - `exit` is decided per research R4, behind a fence `fenceSeq: Long?`: a missing route counts as
    ended only when the state's `refreshSeq > fenceSeq`. The fence is null in US1 and is set by
    T033 from `session.startedSeq`.
  - `lastKnownTargetName` is kept for the "ended" text.
  - `onVisible()`/`onHidden()` acquire and release the session.
  - Exposes `onStop()`, `onPauseResume()`, `consumeMessage()` and `consumeExit()`.

  Make T017 pass.
- [ ] T020 [P] [US1] Implement `main/ui/nowplaying/KindPanel.kt` per contracts/now-playing-ui.md
  "Layout" 2:
  - 248 dp, radius 28, three concentric 1 dp rings (380/276/176 dp) and a 92 dp disc with a 42 dp
    kind icon (`iconForKind`).
  - Stream colours: panel `#2A1F10` (add a token), rings accent at alpha 0.14/0.24/0.38, disc
    `accent`, icon `onAccent`. Other kinds: panel `forKind(kind).bg`, rings and disc
    `forKind(kind).icon`, icon `background`.
  - "Live stream" badge when `live`.
  - Wrapped in `clearAndSetSemantics {}`.
- [ ] T021 [US1] Implement `main/ui/nowplaying/NowPlayingScreen.kt` (stateless
  `NowPlayingContentView(state, actions)` + a stateful wrapper) per contracts/now-playing-ui.md:
  - back control "Rooms"
  - screen states Loading / Can't reach / Stale (banner + dimmed + all controls disabled) / Live
  - `KindPanel`
  - title Sora 26 sp
  - subtitle `"<Kind> · <detail>"`
  - status chip + "on …" line with " · Not connected"
  - actions row: Stop (accent, label "Stop") and Pause/Resume (surface, label
    "Pause <target>"/"Resume <target>", disabled when `!pauseEnabled`). Move to room… is added in
    T036.
  - "Live streams can't be paused" when `live`
  - snackbar for `message`

  Reuse the chip colours from the Rooms status text (`main/ui/rooms/StatusText.kt`). Lifecycle:
  `repeatOnLifecycle(STARTED) { onVisible(); try { awaitCancellation() } finally { onHidden() } }`.
  When `exit` is non-null, call `onExit()` then `consumeExit()`. No progress bar, position or
  artwork (FR-008).
- [ ] T022 [US1] Wire navigation in `main/ui/nav/AppNavigation.kt` and `main/AppGraph.kt`:
  - `AppGraph.nowPlayingViewModel(routeId, savedState: SavedStateHandle)`.
  - The `entry<Destination.NowPlaying>` renders `NowPlayingScreen(viewModel { graph.nowPlayingViewModel(key.routeId, createSavedStateHandle()) }, onBack = { backStack.pop() }, onExit = { backStack.pop() })`,
    replacing the placeholder (FR-001). The view model is per entry via T002's decorator.
  - Update the `PlaceholderScreen` description text only where it mentions Now Playing.

  Extend `test/ui/nav/AppBackStackTest.kt`: push NowPlaying, then pop → Rooms; the saver round-trip
  keeps `now:<id>`.

**Checkpoint**: US1 is fully functional. Build the APK and hand it over for quickstart §2 steps 1–5.

---

## Phase 4: User Story 2 - Set volume and mute per room on Now Playing (Priority: P2)

**Goal**: Now Playing shows the room pill with its mute button, or for a group the group pill
(proportional), the group mute and one pill per member, with master mute respected.

**Independent Test**: With a group route on two rooms at different volumes, drag the group pill,
then each room pill, then mute and unmute the group, comparing each value with the hub
(quickstart §2 step 6).

### Tests for User Story 2 ⚠️ write first, see them fail

- [ ] T023 [P] [US2] Extend `test/domain/NowPlayingBuilderTest.kt` with the volume rules from
  data-model.md:
  - single room → `main` pill (`key = "main"`, label = room name, `roomVolumes = {id: v}`),
    `members = []`, `MuteModel(Target.Room, name, muted, enabled)`
  - group → `main` covers all known members, plus `members` "in `outputIds` order, skipping unknown
    ids" (`key = "member:<roomId>"`)
  - a group with no known members → `volume = null`; `Target.Unknown` or a missing target →
    `volume = null`
  - "`mute.muted = masterMuted || (room|group).muted`. `mute.enabled = !masterMuted`"
  - "A member pill's `muted = masterMuted || member.muted`. The main pill's
    `muted = masterMuted || target.muted`"
  - the group `muted` comes only from the hub's `GroupResponse.muted` (one muted member out of two
    → group not muted, that member pill muted)
- [ ] T024 [US2] Fill `volume: VolumeSection?` in `main/domain/NowPlayingBuilder.kt` per T023.
  Make T023 pass.
- [ ] T025 [P] [US2] Extend `test/ui/nowplaying/NowPlayingViewModelTest.kt` (US2 part):
  - main-pill drag on a group → only `setRoomVolume` per member, scaled (70/35 → 35 gives 35/18)
    and throttled; the member pills' shown values follow
  - member drag → only that room changes and the shown group value is the new max
  - a muted pill (target, member or master) ignores `onVolumeDragStart`
  - mute → `setRoomMute(id, !shown)` for a room, `setGroupMute(id, !shown)` for a group, shown state
    unchanged until a refresh (no optimistic flip, research R9); in flight blocks a repeat
  - master mute → mute is ignored and the state flags "All rooms are muted"
  - a failed mute → "Couldn't mute <name>. Can't reach the hub."
  - stale → drags and mute are ignored
  - no `/groups/{id}/volume` is ever requested
- [ ] T026 [US2] In `main/ui/nowplaying/NowPlayingViewModel.kt`:
  - Own a `VolumeDragController`, exposing its `pending` in `NowPlayingUiState.pending`.
  - Add `onVolumeDragStart(pillKey)`, `onVolumeDrag(pillKey, v)` and `onVolumeDragEnd(pillKey, v)`.
    The base is the pill's `roomVolumes` overlaid with `pending`, and the target name is the
    pill's label.
  - Add `onMuteToggle()` using `MuteModel` and `UserAction.Mute`.
  - Call `controller.onRefresh` on each successful session refresh.

  Make T025 pass.
- [ ] T027 [US2] Implement `main/ui/nowplaying/VolumeSection.kt` per contracts/now-playing-ui.md
  "Layout" 7, reusing `main/ui/rooms/VolumePill.kt`. Add `height`, `showIcon` and `labelStyle`
  parameters if needed, keeping Rooms' look unchanged.
  - Main row: 52 dp pill (speaker icon, label 14 sp 600, percentage), plus a 52 dp mute button
    (label "Mute <target>"/"Unmute <target>", disabled while master muted or in flight).
  - Group: a 2-column grid of 44 dp member pills (label `#E4E3DF` 14 sp, no icon).
  - Pill a11y "<name> volume", range 0–100.
  - "All rooms are muted" line when `masterMuted`.

  Add it to `NowPlayingScreen.kt` below the actions. Hide it when `volume == null`.

**Checkpoint**: US1 + US2 work. Rooms' group pills are unchanged.

---

## Phase 5: User Story 3 - Move playback to another room (Priority: P2)

**Goal**: "Move to room…" opens the Move playback sheet listing rooms, "<Room> only" options and
groups, each with its consequence note. Confirming moves the playback, and Now Playing follows the
new route.

**Independent Test**: With a stream on a group and another source in one room, open the sheet,
check every note, then move to an idle room, then to the busy room, then to a group, then to one
member via "<Room> only" (quickstart §2 steps 7–10).

### Tests for User Story 3 ⚠️ write first, see them fail

- [ ] T028 [P] [US3] Write `test/domain/MoveDestinationsTest.kt` covering **all 19 cases** of the
  data-model.md "FR-025 test matrix" one-to-one, using research R8 rules:
  - precedence `TurnedOff` > `NotConnected` > `WillStop` > `WillStopOnGroup` > `Idle`
  - "<Room> only" members follow the same precedence: a turned-off or not-connected member reads
    `TurnedOff`/`NotConnected` (not `OthersStop`), is unselectable, sorts with the unselectable
    rooms and keeps its " only" label (case 19, spec FR-021/FR-022)
  - a group with no known members → `NoRooms`, unselectable (case 18, spec FR-020a)
  - group `WillStop` sources are distinct and in member order; members occupied by the current
    route never count as "will stop"
  - `notConnected` = names of unavailable members, and such a group stays selectable
  - room ordering: selectable non-members, then "only", then unselectable, each case-insensitive
    with the id as tiebreaker; groups: selectable, then unselectable
  - `ctaName` = the room name for "X only"
  - `null` for a gone route; an empty list for a `Target.Unknown` route
  - `sourceName`/`currentTargetName`
- [ ] T029 [P] [US3] Write `test/ui/nowplaying/DestinationTextTest.kt` for every row of the research
  R8 copy table:
  - "Idle"
  - "Jazz24 will stop"
  - "Jazz24 and Morning playlist will stop"
  - "Jazz24 will stop on Downstairs"
  - "Kitchen stops"
  - "Kitchen and Patio stop"
  - "Office, Kitchen and Patio stop"
  - "Living Room + Kitchen"
  - "Living Room + Kitchen · Patio not connected"
  - "Jazz24 will stop · Patio and Office not connected"
  - "Turned off", "Not connected", "No rooms"
  - the warning flag only for `WillStop` and `WillStopOnGroup`
- [ ] T030 [P] [US3] Extend `test/ui/nowplaying/NowPlayingViewModelTest.kt` (US3 part):
  - `onOpenMove()` opens the sheet only when Live and `moveVisible`, with `selected = null`
  - `onSelect(target)` ignores unselectable targets
  - the CTA is disabled with nothing selected
  - a refresh making the selected destination unselectable or absent → `selected = null`
  - a refresh making the route Paused (or not Playing) → `sheet = null`
  - `onDismissMove()` → `sheet = null` with no request
  - `onConfirmMove()` → `transferRoute(followedId, selected)`; a repeated confirm while in flight
    sends nothing
  - `Ok(Route("r2"))` → `followedRouteId = "r2"`, `sheet = null`, and **no** `exit` even when the
    next refresh (started before the switch) lacks both r1 and r2; a refresh started after the
    switch that contains r2 shows r2's target; a later refresh without r2 → `Ended`
  - **in-flight fence**: a refresh that was already running when `Ok(r2)` returned completes
    without r2 → still no `exit` (`fenceSeq` was captured from `session.startedSeq`)
  - `followedRouteId` is written to `SavedStateHandle`. A new view model built with that handle
    (process-death restore) follows r2, shows r2's target and does not post "ended"
  - `Err` → `sheet = null` and message "Couldn't move <source> to <ctaName>." (or the "Can't reach
    the hub." variant)
  - stale → confirm is ignored

### Implementation for User Story 3

- [ ] T031 [US3] Implement `main/domain/MoveDestinations.kt`: `MoveSheetContent`, `MoveDestination`,
  `MoveDestinationKind { Room, MemberOnly, Group }`, `MoveDestinationNote` (with `val warning`) exactly as
  in data-model.md, and `object MoveDestinations { fun build(snapshot, routeId): MoveSheetContent? }`
  using `occupancy()` from T003 with the current route excluded. Make T028 pass.
- [ ] T032 [US3] Implement `main/ui/nowplaying/DestinationText.kt`: `fun destinationNoteText(note: MoveDestinationNote): String`
  using `joinNames`. Group `Members` are joined with " + ", and `notConnected` is appended as
  `" · ${joinNames(it)} not connected"`. Make T029 pass.
- [ ] T033 [US3] Add the sheet to `main/ui/nowplaying/NowPlayingViewModel.kt`:
  - `MoveSheetState(content, selected)` rebuilt from each snapshot with `MoveDestinations.build`
  - `onOpenMove()`, `onSelect(target)`, `onDismissMove()` and `onConfirmMove()`
  - after `Ok(route)`: set `savedState["followedRouteId"] = route.id` and
    `fenceSeq = session.startedSeq` (research R1/R3; **not** the state's `refreshSeq`), close the
    sheet and request a refresh
  - after `Err`: close the sheet and set the message via `UserAction.Move(ctaName)` with the
    source name as X
  - close the sheet automatically per FR-012

  Make T030 pass.
- [ ] T034 [US3] **Design gate (FR-026, research R12)**. Confirm the canvas
  https://claude.ai/artifact/R7e4yABDYUuytc64XxNNKK (`project/Transfer.dc.html`) and
  `design/screens/Transfer.dc.html` contain a "Groups" section: a legend in the "Move to" style,
  room-row style with a group icon, and notes as in research R8.
  - If they do, sync `design/screens/Transfer.dc.html` from the canvas and commit it.
  - If they do not and this session can edit the canvas, add the section (sample: "Upstairs ·
    Bedroom + Office" with "Morning playlist will stop", and "Outdoor · Patio + Garden" with
    "Turned off"), then sync the offline copy.
  - Otherwise **stop and ask the user** before T035. Everything up to T033 is done regardless.
- [ ] T035 [US3] Implement `main/ui/nowplaying/MoveSheet.kt` per contracts/now-playing-ui.md "Move
  playback sheet" and the updated `Transfer.dc.html`:
  - Material 3 `ModalBottomSheet` with tokens (container `surface`, top radius 28, handle 36×4
    `#3A3E46`, scrim `rgba(5,6,8,0.72)`)
  - title "Move playback" (Sora 22 sp), subtitle "<source> · now on <target>"
  - a scrollable list in a `selectableGroup()`: legend "MOVE TO", room rows, then a "GROUPS"
    legend and group rows (hidden when empty)
  - row: min 58 dp, radius 14, 38 dp icon tile, label 15 sp 600, note 12 sp in `warningText` when
    `note.warning`, else `textMuted`, radio on the right; selected → bg `selectedBg` + 1 dp inset
    `selectedOutline`; unselectable → alpha 0.5 and not selectable
  - pinned below: "Keeps playing while it moves, no restart" with a `#5FD3C4` 16 dp check icon, the
    primary button (56 dp, "Move to <ctaName>", or "Move" disabled with nothing selected; also
    disabled while stale or in flight), and Cancel (44 dp)
  - rows use `selectable(role = Role.RadioButton)` with the description "<label>, <note>"
  - swipe, scrim and Back call `onDismissMove()`
- [ ] T036 [US3] Add "Move to room…" to the actions row in `main/ui/nowplaying/NowPlayingScreen.kt`:
  76 dp `surface` round button, arrow icon, caption "Move to room…", label "Move to room". Shown
  only when `moveVisible`, disabled while stale. Show `MoveSheet` when `state.sheet != null`.
  System Back closes the sheet before popping.

**Checkpoint**: all three stories work. Build the APK for quickstart §2 steps 7–10.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [ ] T037 [P] Accessibility pass over `main/ui/nowplaying/` (FR-027, contracts/now-playing-ui.md
  "Accessibility summary"):
  - every clickable ≥ 44 dp
  - exact labels per the table
  - the sheet announced as a single-choice list

  Extend `test/ui/theme/ContrastTest.kt` with every new text pair: `#C9CBD1`, `#E4E3DF`,
  `#F2D3A4` and `textMuted` on `surface`/`background`, `#F2D3A4` on the badge background (black
  40 % over `#2A1F10`), accent on `#2A1F10` (chip), and `onAccent` on `accent`. Text ≥ 4.5:1,
  icons ≥ 3:1.
- [ ] T038 [P] Compare Now Playing and the sheet with `design/screens/NowPlaying.dc.html` and
  `Transfer.dc.html` (sizes, spacing, radii, fonts, colours) and fix drift. Add previews in
  `main/ui/nowplaying/NowPlayingPreviews.kt` with the design sample data: the stream on Downstairs
  with the sheet's five rooms plus groups, a pauseable file source, and a line-in. Use the same
  preview mechanism as `RoomsPreviews.kt`, if one exists.
- [ ] T039 Search `composeApp/src/` for `LinearProgressIndicator`, `Slider` and
  `CircularProgressIndicator`; none may appear (FR-008). The string `groups/` + `volume` may appear
  only in the generated client and the negative tests. `setGroupMute` is the only group call
  besides listing.
- [ ] T040 Run the [quickstart.md](quickstart.md) §1 commands: `./gradlew :androidApp:assembleDebug`,
  `:composeApp:testAndroidHostTest`, `:composeApp:allTests`, `:composeApp:check`. All must be
  green, every suite in the §1 table must exist, and `git status` must show no generated files.
- [ ] T041 Update `AGENTS.md` where the implementation differs from what it states (e.g. the
  "Project layout" mention of `ui/session/` if useful, and the new dependency if versions are
  listed), and mark `specs/002-now-playing-move-to-room/spec.md` `**Status**: Implemented`.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: T001 first (version commit), then T002.
- **Foundational (Phase 2)**:
  - T003 first.
  - Session: T004 → T005 → T007, with T006 parallel to T005.
  - Volume: T008 → T009 → T010 (T010 after T007, same file).
  - T011, T012 and T015 are parallel at any point. T013 → T014.
- **US1 (Phase 3)**: needs Phase 2. T016 ∥ T017 → T018 → T019. T020 is parallel to T018/T019.
  T021 needs T019 + T020. T022 needs T021.
- **US2 (Phase 4)**: needs US1 (same view model and screen). T023 → T024. T025 → T026 (after T024).
  T027 after T026.
- **US3 (Phase 5)**: needs US1 (view model, screen). It is independent of US2, but since
  `NowPlayingViewModel.kt` and `NowPlayingScreen.kt` are shared, do it after US2 or merge
  carefully. T028 ∥ T029 ∥ T030 → T031, T032 → T033. T034 (gate) → T035 → T036.
- **Polish (Phase 6)**: needs all stories.

### User Story Dependencies

- **US1 (P1)**: Foundational only.
- **US2 (P2)**: US1 (screen + view model to extend). Uses T009's controller.
- **US3 (P2)**: US1. The sheet UI also needs the design gate T034.

### Within Each User Story

- Tests first, seen failing → pure domain → view model → composables → wiring.

### Parallel Opportunities

- Phase 2: T006 ∥ T005; T011 ∥ T012 ∥ T015 ∥ T013 alongside the session/volume chain.
- US1: T016 ∥ T017, then T020 ∥ T018/T019.
- US2: T023 ∥ T025 (different files).
- US3: T028 ∥ T029 ∥ T030, then T031 ∥ T032.
- Polish: T037 ∥ T038.

---

## Parallel Example: User Story 3

```bash
# Tests first (all different files):
Task: "Write MoveDestinationsTest in composeApp/src/commonTest/kotlin/sonora/multiroom/mobile/domain/MoveDestinationsTest.kt"
Task: "Write DestinationTextTest in composeApp/src/commonTest/kotlin/sonora/multiroom/mobile/ui/nowplaying/DestinationTextTest.kt"
Task: "Extend NowPlayingViewModelTest (US3 part) in composeApp/src/commonTest/kotlin/sonora/multiroom/mobile/ui/nowplaying/NowPlayingViewModelTest.kt"

# Then the two pure implementations:
Task: "Implement MoveDestinations in composeApp/src/commonMain/kotlin/sonora/multiroom/mobile/domain/MoveDestinations.kt"
Task: "Implement destinationNoteText in composeApp/src/commonMain/kotlin/sonora/multiroom/mobile/ui/nowplaying/DestinationText.kt"
```

---

## Implementation Strategy

### MVP First

1. Phase 1 Setup → Phase 2 Foundational (Rooms unchanged, on the shared session).
2. US1: Now Playing with Stop for every route closes 001's "pauseable can't be stopped" gap.
3. **Stop and validate**: build, run all tests, and hand over the APK for quickstart §2 steps 1–5.

### Incremental Delivery

1. Setup + Foundational → no visible change, refactor proven by the 001 suites.
2. US1 → the detail screen (MVP).
3. US2 → per-room volume and mute.
4. US3 → Move to room… (sheet UI after the design gate).
5. Polish → accessibility, design fidelity, final verification.

---

## Notes

- [P] = different files, no dependency on unfinished tasks.
- Verify each test fails before implementing (Constitution IV).
- Never commit `composeApp/build/` or hand-edit generated code (Constitution I).
- "Move to room…" never shows while Paused (FR-012). Allowing it is a later follow-up, once the
  hub's behaviour is checked.
- The two Move-sheet rules confirmed on 2026-10-03 (spec FR-020a, FR-021/FR-022: an unavailable
  "<Room> only" member is unselectable; a group with no known members reads "No rooms") are covered
  by cases 18–19 of the FR-025 matrix.
- Fences on the session always capture `session.startedSeq`, never the state's `refreshSeq`
  (research R1).
