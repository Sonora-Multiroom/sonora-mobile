---

description: "Task list for 003 Start Playback"
---

# Tasks: Start Playback

**Input**: Design documents from `specs/003-start-playback/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md),
[data-model.md](data-model.md), [contracts/hub-repository.md](contracts/hub-repository.md),
[contracts/start-playback-ui.md](contracts/start-playback-ui.md), [quickstart.md](quickstart.md)

**Tests**: REQUIRED. They come from Constitution IV (test-first for logic, `MockEngine` repository
tests) and spec FR-017. Every test task comes before its implementation task and MUST be seen
failing first.

**Organization**: grouped by user story. Spec priorities: US1 P1 (play a configured source), US2 P1
(know what will stop), US3 P2 (paste a link).

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
- Colours, typography and shapes only through `SonoraTheme` tokens (`main/ui/theme/Tokens.kt`).
  The tokens this screen needs already exist (`selectedBg`, `selectedOutline`, `warningText`,
  `textSoft`, kind colours).
- Never send `joinMode`, `volume` or `displayName` in a start request. Never call
  `PUT /api/v2/groups/{id}/volume`.
- Every 001 and 002 test scenario MUST stay green. Rooms, Now Playing and the Move sheet keep
  their one-route view (`occupancy()` unchanged). Only Start Playback uses `routesByRoom()`.
- Every "wait for a fresh snapshot" captures `session.startedSeq`, never the state's `refreshSeq`
  (002 research R1 fence rule).
- If a task needs a decision none of the design documents cover, stop and ask (AGENTS.md "Workflow").
- Commit after each task or logical group with a Conventional Commit message that explains why.

---

## Phase 1: Setup

**Purpose**: version bump and the contract reconciliation that Constitution I requires before any
call is touched.

- [ ] T001 Bump the app version as the **first commit** of this feature (AGENTS.md "Workflow"): in
  `gradle.properties` set `sonora.versionName=0.3.0-alpha` and `sonora.versionCode=3`. Run
  `./gradlew :androidApp:assembleDebug`.
- [ ] T002 Reconcile the 0.1.21 contract (research R1). Run `./gradlew :composeApp:openApiGenerate`.
  In `composeApp/build/generated/openapi/`, confirm these fields exist with these names and types:
  - `RouteResponse.joinMode`
  - `InputResponse.defaultJoinMode`
  - `ErrorResponse.reason` / `outputId`
  - `CreateRouteRequest(inputId, targetId, targetType, joinMode?)`
  - `PlaybackRequest(uri, targetId, targetType, displayName?, volume?, joinMode?)`
  - `PlaybackResponse.route`
  - `PlaybackApi.playback`
  - `RoutesApi.createRoute`

  Then run `./gradlew :androidApp:assembleDebug :composeApp:testAndroidHostTest
  :composeApp:allTests :composeApp:check`: everything MUST be green with no code change. If a
  generated name differs from the contract docs, record it in research.md R1 (the HTTP shape in
  contracts/hub-repository.md stays binding).

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: domain and data changes from 0.1.21, the multi-route helper, and the navigation
hand-off to Now Playing, which every story uses.

**⚠️ CRITICAL**: no user story work begins until this phase is complete.

### Join modes (research R3)

- [ ] T003 [P] Extend `test/data/KtorHubRepositorySnapshotTest.kt` (and payloads in
  `test/data/Fixtures.kt`), failing first, with contract test 7:
  - routes with `"joinMode"` `REPLACE`, `MIX`, `DUCK_OTHERS`, missing and `"SOMETHING_NEW"` map to
    `JoinMode.Replace`, `Mix`, `Announcement`, `Unknown` and `Unknown` (Constitution V)
  - inputs with `"defaultJoinMode"` the same values map to `Replace`, `Mix`, `Announcement`, `null`
    and `null` (unknown is coerced to `null`, the recorded deviation in plan Complexity Tracking)
  - the snapshot never fails because of the unknown value
- [ ] T004 In `main/domain/Models.kt` add `enum class JoinMode { Replace, Mix, Announcement, Unknown }`,
  `Route.joinMode: JoinMode = JoinMode.Replace` and `Source.defaultJoinMode: JoinMode? = null`.
  The defaults keep existing constructors compiling. Map both fields in `main/data/ApiMapping.kt`
  in the style of `RouteStatus` (`null -> Unknown`): "missing/unknown → `Unknown`" for routes and
  "missing/unknown → `null`" for sources (data-model.md, research R3). Make T003 pass.

### Admission refusals (research R6)

- [ ] T005 [P] Extend `test/data/KtorHubRepositoryActionsTest.kt`, failing first:
  - an RFC 7807 body with `"reason":"ROUTE_LIMIT_REACHED","outputId":"kitchen"` on 409 and on 422
    (use `transferRoute`, an existing call) → `Rejected(status, type, "ROUTE_LIMIT_REACHED",
    "kitchen")`
  - a problem body without these fields → both `null`
  - a non-JSON error body → `Rejected(status, null, null, null)`
- [ ] T006 In `main/data/HubRepository.kt` add `reason: String? = null` and `outputId: String? = null`
  to `HubError.Rejected`. In `main/data/HttpClients.kt` make `rejection()` read both from
  `ErrorResponse`, never their text into UI. Make T005 pass; every existing `Rejected(…)` test stays
  green.

### Several routes per room (research R2)

- [ ] T007 [P] Write `test/domain/RoutesByRoomTest.kt`, failing first:
  - a room route is listed under its room
  - a group route is listed under every known member, and an unknown member id is skipped
  - two routes on one room are kept in hub order
  - a `Stopped` route is ignored, while `Failed`/`Unknown` are kept
  - a `Target.Unknown` route covers nothing
  - `Route.isAnnouncement` is true only for `JoinMode.Announcement` (false for `Unknown`)
- [ ] T008 In `main/domain/PlaybackRules.kt` add `internal fun routesByRoom(snapshot: HubSnapshot):
  Map<String, List<Route>>` (built on `liveRoutes` and `describeTarget(...).occupies`) and
  `internal val Route.isAnnouncement`. Leave `occupancy()` unchanged. Make T007 pass.

### Docs sync (Constitution I, research R1)

- [ ] T009 [P] Update `AGENTS.md`:
  - "API": the contract was fetched at **0.1.21**; mention join modes (`joinMode`,
    `defaultJoinMode`) and admission refusals (`reason`, `outputId`).
  - "Domain rules": replace "An output plays **at most one route**" with: since 0.1.21 an output
    may carry several routes (mixed, announcements); Rooms and Now Playing still show one per room,
    see [docs/backlog/rooms-with-several-playbacks.md](docs/backlog/rooms-with-several-playbacks.md).
    The rest of that bullet (group routes occupy every member) stays.

### Hand-off to Now Playing (research R10)

- [ ] T010 [P] Extend `test/ui/nav/AppBackStackTest.kt`, failing first:
  - `replaceTop(NowPlaying("r1", 7, "Bedroom"))` on `[Rooms, StartPlayback(null)]` gives
    `[Rooms, NowPlaying("r1", 7, "Bedroom")]`, and `pop()` then shows Rooms
  - the saver writes `now:r1` for that entry and restores `NowPlaying("r1")` with `null` fence and
    name
  - the existing round-trips stay green
- [ ] T011 In `main/ui/nav/Destinations.kt` change
  `NowPlaying(routeId: String, startedAfterSeq: Long? = null, targetName: String? = null)`, and add
  `fun replaceTop(destination: Destination)` to `AppBackStack`. `encodeDestination` keeps writing
  `now:<id>` (the fence is in memory only). Make T010 pass.
- [ ] T012 Extend `test/ui/nowplaying/NowPlayingViewModelTest.kt`, failing first:
  - created with `startedAfterSeq = session.startedSeq` (captured before a refresh whose snapshot
    lacks the route, with that refresh in flight) → no `exit` and nothing posted, even when that
    snapshot arrives
  - a refresh started after the fence that contains the route → content shown
  - a refresh started after the fence that still lacks it → `Exit.Ended("Bedroom")` and
    "Playback on Bedroom ended" posted (name from `targetName` though never shown)
  - without a fence, behaviour is unchanged
- [ ] T013 In `main/ui/nowplaying/NowPlayingViewModel.kt` accept `startedAfterSeq: Long?` and
  `targetName: String?`. Initialise `fenceSeq = startedAfterSeq` and `lastTargetName = targetName`.
  In `main/AppGraph.kt` (`nowPlayingViewModel(...)`) and `main/ui/nav/AppNavigation.kt` (the
  `NowPlaying` entry) pass `key.startedAfterSeq` / `key.targetName`. Make T012 pass.

**Checkpoint**: everything green. Nothing has changed visibly yet.

---

## Phase 3: User Story 1 - Play a configured source in a room or group (Priority: P1) 🎯 MVP

**Goal**: from Rooms, open Start Playback, pick a turned-on source and a room or group, tap Play.
The hub starts it and Now Playing opens for it.

**Independent Test**: with the hub idle, start a stream in one room, then a different source on a
group. After each, the hub plays it on the chosen target, Now Playing shows it, and Back → Rooms
shows it (quickstart §2 rows 1–3).

### Tests for User Story 1 (write first, see them fail)

- [ ] T014 [P] [US1] Write `test/domain/StartPlaybackBuilderTest.kt` against
  `StartPlaybackBuilder.build(snapshot)` (data-model.md `StartPlaybackContent`):
  - **sources**: turned-off sources are left out, runtime (ephemeral) ones are included; sorted by
    name case-insensitively ("alpha" < "Beta"), id as tie-break; `kind` from `inferSourceKind`
  - **targets**: every room and group the hub lists except `Target.Unknown`. Order: selectable
    groups, selectable rooms, then every unselectable target, each part A→Z case-insensitive with
    id tie-break (FR-007)
  - **status rows** (FR-008; data-model "Rules"; non-announcement routes only):
    - room idle → `Idle`
    - own route in each status (Active/paused → Paused, Active → Playing, live stream → Playing,
      Starting, Stopping, Failed, Unknown) → `Playing(status, [source])`
    - two own routes → both sources in hub order
    - covered only by a group route → `InGroup(group)`
    - own route and group route → `Playing`
    - disabled → `TurnedOff`; unavailable → `NotConnected`; disabled and unavailable → `TurnedOff`
    - disabled room with its own playing route → `TurnedOff`, not selectable (spec edge case
      "turned-off room or group that is still playing")
    - group with its own route → `GroupPlaying`
    - idle group → `GroupMembers(playable members)`
    - group with a turned-off and a not-connected member → `GroupMembers` of the rest, selectable
    - group with no known members → `NoRooms`
    - all members disabled → `TurnedOff`
    - members disabled or unavailable with none playable → `NotConnected`
    - disabled group → `TurnedOff`; disabled group with its own playing route → `TurnedOff`, not
      selectable
    - an announcement route on a room is ignored (room reads `Idle`)
  - `selectable` is false exactly for `TurnedOff`, `NotConnected` and `NoRooms` (FR-009)
- [ ] T015 [P] [US1] Write `test/ui/startplayback/StartPlaybackTextTest.kt` (status part):
  - "Idle", "Playing · Jazz24", "Paused · Morning playlist", "Starting… · X", "Stopping… · X",
    "Couldn't play · X", "Unknown · X", "Playing · Jazz24 + Doorbell"
  - "In Downstairs", "Group · Radio Paradise", "Group · Kitchen + Patio"
  - "Turned off", "Not connected", "No rooms"
  - Play label: "Play" when nothing or only one side is selected, "Play Jazz24 in Bedroom",
    "Starting…"
- [ ] T016 [P] [US1] Extend `test/data/KtorHubRepositoryActionsTest.kt` with contract tests 1, 3
  (for `POST /api/v2/routes`), 5 and 6:
  - `startSource("jazz", Room("bedroom"))` sends exactly `POST /api/v2/routes` with
    `{"inputId":"jazz","targetId":"bedroom","targetType":"SINGLE_OUTPUT"}` and no `joinMode` key; a
    group target sends `OUTPUT_GROUP`
  - 201 and 200 `RouteResponse` → `Ok(Route)`
  - 400/404/422 problem bodies → `Rejected` with `reason`/`outputId` when present
  - IO failure or a 4 s delay (virtual time) → `Unreachable`
  - garbage 2xx body → `Unexpected`
  - `Target.Unknown` throws and sends nothing
- [ ] T017 [P] [US1] Extend `test/ui/MessagesTest.kt` with the `StartFailure` copy (data-model.md)
  and the source column of the research R6 table, via
  `startFailure(StartKind.Source, error, StartNames("Jazz24", "Bedroom"), roomName)` where
  `roomName: (outputId: String) -> String?` is a lookup stub:
  - a `reason` on any status → `RoomFull` / `AlreadyThere("Jazz24", room)`, with `outputId`
    resolved through `roomName` and falling back to `names.target` when missing or unknown
  - 400/422 without reason → `Other`
  - 404 → `NoLongerOnHub` (name decided by the caller, see T019)
  - `Unreachable` → `HubUnreachable`
  - `Unexpected` → `Other`
  - each copy string exactly as in FR-016
- [ ] T018 [P] [US1] Extend `test/ui/session/HubSessionTest.kt`: `awaitFreshSnapshot(timeoutMillis)`
  - captures `startedSeq` and calls `requestRefresh()`
  - with a refresh in flight it does **not** return that refresh's snapshot, but the next one's
  - returns `null` after the timeout when refreshes fail or no screen holds the session
- [ ] T019 [P] [US1] Write `test/ui/session/PlaybackStarterTest.kt` (source path, `FakeRepository` +
  `HubSession` on the test scheduler, `AppMessages`):
  - success → `StartAttempt.Done(route, startedAfterSeq)` with `startedAfterSeq` =
    `session.startedSeq` captured right after the answer, and `requestRefresh()` called
  - an existing route returned → same
  - **404** → waits for a fresh snapshot (≤ 5 s), then:
    - the source is gone or turned off → `NoLongerOnHub(source name)`
    - else the target is gone → `NoLongerOnHub(target name)`
    - else → `Other`
  - **Unreachable** (any: timeout or connect error, research R6/R8; FR-016a names the timeout, and
    a connect error ends the same way because its recovery refresh fails too) → still `Starting`;
    then a fresh snapshot with a live route on exactly that
    target with that input (several → the last in hub order) → `Done(thatRoute)`
  - no match → `HubUnreachable`; no fresh snapshot within 5 s → `HubUnreachable`
  - a second `start` while one runs is ignored
  - **detached** (the screen called `detach()`, i.e. it closed): a failure posts its copy to
    `AppMessages` once; a success posts nothing
- [ ] T020 [P] [US1] Write `test/ui/startplayback/StartPlaybackViewModelTest.kt` (source path):
  - opened with `initialTargetId = null` → nothing selected, `playLabel = Play`, Play disabled
  - opened with an idle room id → that room selected; with an id that is unselectable or unknown on
    the first snapshot → nothing selected
  - picking a source and a target → `PlaySource("Jazz24", "Bedroom")`, enabled only while
    `Connection.Live`
  - an unselectable target ignores selection
  - **stale**: lists and selections kept, Play disabled, re-enabled on the next success
  - **pruning**: a selected source removed or turned off, or a target removed or becoming
    unselectable, is deselected at the next snapshot
  - **Play**: one `startSource` call, `starting = true`, selections locked (select calls ignored)
  - `Done` → `exit = Started(routeId, startedAfterSeq, targetName)`
  - a failure → `starting = false`, selections kept, `message` = FR-016 copy
  - `onClose()` → `exit = Closed` and the starter is detached
  - acquire/release on visible/hidden
  - selections restored from `SavedStateHandle`

### Implementation for User Story 1

- [ ] T021 [US1] Create `main/domain/StartRequest.kt` with the plain types `sealed interface StartWhat
  { Source(id), Link(uri) }` and `data class StartNames(source: String?, target: String)`
  (data-model.md; no logic, so no test of their own). The starter, the view model and, later,
  `StartConsequence` (T035) use them. Then implement `main/domain/StartPlaybackBuilder.kt`: `StartPlaybackContent`,
  `SourceOption`, `TargetOption`, `TargetStatus`, `object StartPlaybackBuilder { fun build(snapshot):
  StartPlaybackContent }`, using `routesByRoom`, `cardStatus`, `inferSourceKind` and the data-model
  rules verbatim. Make T014 pass.
- [ ] T022 [P] [US1] Implement `main/ui/startplayback/StartPlaybackText.kt`:
  `targetStatusText(TargetStatus)` (maps `CardStatus.LiveStream` → "Playing", reusing
  `statusWord` for the rest) and `playLabelText(PlayLabel)`. Make T015 pass.
- [ ] T023 [US1] Add `suspend fun startSource(inputId: String, target: Target): HubResult<Route>` to
  `main/data/HubRepository.kt` and implement it in `main/data/KtorHubRepository.kt` with
  `RoutesApi.createRoute(CreateRouteRequest(inputId, targetId, targetType))`, joinMode left null and
  the response mapped via `toRoute()`, else `Unexpected`. Add a scriptable `startSource` (and a
  `startResults` queue) to `FakeRepository` in `test/ui/rooms/FakeHub.kt`. Make T016 pass.
- [ ] T024 [P] [US1] In `main/ui/Messages.kt` add `sealed interface StartFailure` (data-model.md),
  `enum class StartKind { Source, Link }`, `fun startFailure(kind: StartKind, error: HubError,
  names: StartNames, roomName: (outputId: String) -> String?): StartFailure` and
  `fun startFailureMessage(StartFailure): String`. This is the only
  place these strings exist. Make T017 pass.
- [ ] T025 [US1] Add `suspend fun awaitFreshSnapshot(timeoutMillis: Long = 5000): HubSnapshot?` to
  `main/ui/session/HubSession.kt` (fence on `startedSeq`, `requestRefresh()`, first `Connected`
  state with `refreshSeq > captured`, `withTimeoutOrNull`). Make T018 pass.
- [ ] T026 [US1] Implement `main/ui/session/PlaybackStarter.kt`:
  - `class PlaybackStarter(scope, session, messages)` with `val attempt: StateFlow<StartAttempt?>`
  - `StartAttempt` = `Starting | Done(route, startedAfterSeq) | Failed(StartFailure)`
  - `fun start(what: StartWhat, target: Target, names: StartNames)` (types from T021), `fun
    detach()`, `fun consume()`
  - failures via `startFailure(kind, error, names, roomName)`, with `roomName` looking `outputId`
    up in the session's latest snapshot; 404 names come from the fresh snapshot, falling back to
    `names` (research R7)
  - source requests only for now; the link path follows in US3
  - runs in the app scope (research R9); recovery per research R7/R8 using `awaitFreshSnapshot`

  Create it once in `main/AppGraph.kt`. Make T019 pass.
- [ ] T027 [US1] Implement `main/ui/startplayback/StartPlaybackUiState.kt` (data-model.md "UI
  state": `PlayLabel`, `StartExit`) and `main/ui/startplayback/StartPlaybackViewModel.kt`
  `(initialTargetId: String?, savedState: SavedStateHandle, session: HubSession, starter: PlaybackStarter)`:
  - collects the session, rebuilds via `StartPlaybackBuilder`, prunes selections (research R12)
  - `onSelectSource`, `onSelectTarget`, `onPlay`, `onClose`, `onVisible`/`onHidden`,
    `consumeMessage`, `consumeExit`
  - selections stored in `SavedStateHandle`

  Add `startPlaybackViewModel(targetId, savedState)` to `main/AppGraph.kt`. Make T020 pass.
- [ ] T028 [P] [US1] In `main/ui/rooms/RoomsScreen.kt` make the "Set your hub address" /
  "Can't reach the hub" message block (`Message`) and `StaleBanner` `internal` and reusable,
  without changing Rooms' output.
- [ ] T029 [P] [US1] Implement `main/ui/startplayback/SourceRow.kt` (min-h 54, radius 14, kind tile
  38 r10 via the existing `KindStyle`, name 15 sp 600, kind label 12 sp `textMuted`, radio 20 accent,
  selected `selectedBg` + 1 dp `selectedOutline` ring) and `main/ui/startplayback/TargetTile.kt`
  (min-h 64, radius 14, padding 10/12, name 15 sp 600, status 12 sp `textMuted` one line with
  ellipsis, radio 18, opacity 0.5 when unselectable). Both are `selectable(role = Role.RadioButton)`
  with merged semantics "<name>, <kind>" / "<name>, <status>" and disabled when unselectable
  (contracts/start-playback-ui.md "Accessibility").
- [ ] T030 [US1] Implement `main/ui/startplayback/StartPlaybackScreen.kt` per
  contracts/start-playback-ui.md:
  - header: Close `IconButton` 44 dp, "Close" content description, `Icons.Close` in `textSoft`;
    title "Play something" Sora 20 sp 600
  - scrolling body: "OR PICK A SOURCE" list (empty: "No sources on the hub. Paste a link above."),
    "PLAY IN" two-column grid, each a `selectableGroup()`
  - fixed footer: the Play button, h 56, radius 28, accent/onAccent 16 sp 600, label from
    `playLabelText`, disabled when `!playEnabled`
  - states: no address / loading / stale / unreachable via the reused Rooms blocks
  - snackbar for `message`
  - `LaunchedEffect` on `exit`
  - `onVisible`/`onHidden` under `repeatOnLifecycle(STARTED)`, as Now Playing does

  Leave a slot above the source list for the link field (US3) and above the button for the
  footer lines (US2).
- [ ] T031 [US1] Wire `main/ui/nav/AppNavigation.kt`: replace the `StartPlayback` placeholder entry
  with `StartPlaybackScreen(viewModel { graph.startPlaybackViewModel(key.targetId,
  createSavedStateHandle()) }, …)`. `Closed` → `backStack.pop()`. `Started(routeId, seq, name)` →
  `backStack.replaceTop(Destination.NowPlaying(routeId, seq, name))` (FR-015).
  `./gradlew :androidApp:assembleDebug` is green.

**Checkpoint**: US1 works end to end with configured sources: quickstart §2 rows 1–3 can run on a
device.

---

## Phase 4: User Story 2 - Know what will stop before starting (Priority: P1)

**Goal**: once something and a target are selected, the footer states what starting will do
(stop / play alongside / lower / already playing), plus the "won't play" and mute notes.

**Independent Test**: one source playing in a room, another on a group, one room turned off. Read
every status line, select each target in turn, and compare the consequence line with what the hub
does after Play (quickstart §2 rows 4–6, 10).

### Tests for User Story 2 (write first, see them fail)

- [ ] T032 [P] [US2] Write `test/domain/StartConsequenceTest.kt` against
  `StartConsequence.of(snapshot, what, target)` and `effectiveJoinMode` (data-model.md
  "Consequence"):
  - **Replace**:
    - idle target → `line == null`
    - busy room → `WillStop([Radio Paradise in Bedroom])`
    - room inside a group's playback → named by the group (`… in Downstairs`)
    - group over two rooms playing different things → both, in hub order
    - a group route counted once though it covers several selected rooms
    - same source on exactly the same target → `AlreadyPlaying`
    - same source on a group containing the chosen room → `WillStop` naming it
    - an announcement on the room is left out (only it → `line == null`)
  - **Mix default**: busy → `PlaysAlongside`, announcements left out; idle → `null`
  - **Announcement default**: busy → `WillBeLowered` including announcements; idle → `null`;
    `AlreadyPlaying` still wins
  - **Link** → always Replace, never `AlreadyPlaying`
  - `defaultJoinMode == null` → Replace; `defaultJoinMode == Unknown` → Replace (`WillStop` on a
    busy target); `effectiveJoinMode` never returns `Unknown`
  - a busy route with `joinMode = Unknown` is named in `WillStop` (not treated as an announcement)
  - **Notes**:
    - group with turned-off and not-connected members → `WontPlay(turnedOff, notConnected)`
    - room muted → `TargetMuted("Bedroom")`
    - group muted, or every member muted → `TargetMuted(group)`
    - master mute → `AllRoomsMuted` instead of `TargetMuted`
- [ ] T033 [P] [US2] Extend `test/ui/startplayback/StartPlaybackTextTest.kt` (consequence part):
  - "Radio Paradise will stop in Bedroom"
  - "Jazz24 will stop in Office and Morning playlist in Kitchen"; three items → "A in X, B in Y and C in Z"
  - "Plays alongside Jazz24 in Bedroom"
  - "Jazz24 will be lowered in Bedroom while it plays"
  - "Jazz24 is already playing in Bedroom"
  - "Won't play in Patio (not connected)"; turned off → "(turned off)"; both kinds → two clauses in
    one line
  - "Bedroom is muted"
  - "All rooms are muted"
- [ ] T034 [P] [US2] Extend `test/ui/startplayback/StartPlaybackViewModelTest.kt`:
  - `consequence` is `null` until both a source and a target are selected
  - it follows the next snapshot when the target's state changes (spec edge case)
  - it stays visible while Play is disabled by a stale connection

### Implementation for User Story 2

- [ ] T035 [US2] Implement `main/domain/StartConsequence.kt` (using `StartWhat` from T021):
  `effectiveJoinMode(what, snapshot)` (`null`/`Unknown` → Replace), `AffectedPlayback`, `ConsequenceLine`, `WontPlay`,
  `MuteNote`, `Consequence` and `object StartConsequence { fun of(...) }`, per research R4 and
  data-model.md, using `routesByRoom`, `describeTarget` and `isAnnouncement`. Make T032 pass.
- [ ] T036 [P] [US2] Add `consequenceLineText`, `wontPlayText` and `muteNoteText` to
  `main/ui/startplayback/StartPlaybackText.kt`, joining with the `joinNames` style. Make T033 pass.
- [ ] T037 [US2] In `main/ui/startplayback/StartPlaybackViewModel.kt` compute `consequence` on every
  rebuild and selection change. Make T034 pass.
- [ ] T038 [US2] Render the footer lines in `main/ui/startplayback/StartPlaybackScreen.kt`
  (contracts/start-playback-ui.md "Footer"):
  - `WillStop`: 13 sp `warningText` with the 16 dp warning icon, gap 6
  - every other line and note: 13 sp `textMuted` in the same layout without an icon
  - order: line, `WontPlay`, mute note

**Checkpoint**: US1 + US2 make starting safe. This is the full P1 scope.

---

## Phase 5: User Story 3 - Paste a link and play it (Priority: P2)

**Goal**: paste a SoundCloud, YouTube or stream address, pick a target, Play. The hub resolves the
link (≤ 30 s) and plays it.

**Independent Test**: play a SoundCloud link and a direct stream address in a room, then try an
unreachable address and a malformed one (quickstart §2 rows 7–9, 12, 13).

### Tests for User Story 3 (write first, see them fail)

- [ ] T039 [P] [US3] Write `test/domain/LinkAddressTest.kt` for `checkLink(text)` (research R11):
  - `""` and `"   "` → `Empty`
  - `"soundcloud.com/artist/track"` → `Valid("https://soundcloud.com/artist/track")`
  - `" https://x.y/a "` → trimmed
  - `"http://radio.lan:8000/live"` → kept as is
  - `"jazz"` → `Valid("https://jazz")` (single-label host)
  - `"https://[fe80::1]:8000/x"` → valid
  - `"ftp://x.y"`, `"https://"`, `"https:///path"`, `"hello world"` and `"https://a b.com"` → `Invalid`
- [ ] T040 [P] [US3] Extend `test/data/KtorHubRepositoryActionsTest.kt` with contract tests 2–5
  for `POST /api/v2/play`:
  - the body is exactly `{"uri":…,"targetId":…,"targetType":…}`, with no
    `displayName`/`volume`/`joinMode`
  - 200 `PlaybackResponse` → its nested `Route`; 200 without `route` → `Unexpected`
  - 400/404/422/502/503 problem bodies → `Rejected` with `reason`/`outputId`
  - an answer after 10 s (virtual time) succeeds; after 31 s → `Unreachable`
  - `Target.Unknown` throws
- [ ] T041 [P] [US3] Extend `test/ui/MessagesTest.kt` with the link column of the research R6 table:
  - 400 and 422 without reason → `LinkUnusable`
  - 502 → `LinkUnreachable`
  - 503 → `ServiceDown`
  - 404 → `NoLongerOnHub` (target)
  - `ROUTE_LIMIT_REACHED` → `RoomFull`; `INPUT_ALREADY_ON_OUTPUT` → `Other` (a link has no source
    name, research R6), with `StartNames(null, "Bedroom")`
  - `Unreachable` → `HubUnreachable`
  - each copy exactly as in FR-016
- [ ] T042 [P] [US3] Extend `test/ui/session/PlaybackStarterTest.kt` (link path):
  - success → `Done`
  - **Unreachable** → a fresh snapshot with a `Runtime` input whose `uri` equals the sent link
    (trimmed; scheme and host case-insensitive) and a live route of it on exactly that target →
    `Done(thatRoute)`
  - an input with a different uri → `HubUnreachable`
  - 404 → `NoLongerOnHub(target)` after the fresh snapshot
  - detached failure posted to `AppMessages`
- [ ] T043 [P] [US3] Extend `test/ui/startplayback/StartPlaybackViewModelTest.kt` (link path):
  - typing a non-empty link deselects the source, and picking a source clears the link (FR-005)
  - `Invalid` text while typing → `linkMessageShown == false`, Play disabled
  - `onLinkPasted` / `onLinkFocusLost` / `onLinkDone` with invalid text → `true`
  - becoming valid or empty → `false`
  - a paste is detected as a value change inserting more than one character
  - valid link + target → `PlayLink("Bedroom")`
  - Play calls `playLink` with the normalised uri; `starting` until the answer, even past 3 s
  - link failures keep link and target and show the FR-016 copy
  - the link text is restored from `SavedStateHandle`

### Implementation for User Story 3

- [ ] T044 [US3] Implement `main/domain/LinkAddress.kt` (`LinkCheck`, `checkLink`) with a
  hand-written parser and no JVM APIs (Constitution III). Make T039 pass.
- [ ] T045 [US3] Add `suspend fun playLink(uri: String, target: Target): HubResult<Route>` to
  `main/data/HubRepository.kt`. In `main/data/KtorHubRepository.kt`:
  - create `PlaybackApi(address.baseUrl, linkClient)` where `linkClient = client.config {
    install(HttpTimeout) { requestTimeoutMillis = LINK_TIMEOUT_MILLIS; socketTimeoutMillis =
    LINK_TIMEOUT_MILLIS; connectTimeoutMillis = HUB_TIMEOUT_MILLIS } }`
  - add `internal const val LINK_TIMEOUT_MILLIS = 30_000L` in `main/data/HttpClients.kt`
  - send `PlaybackRequest(uri, targetId, targetType)` and map `.route` via `toRoute()`

  Add `playLink` to `FakeRepository` in `test/ui/rooms/FakeHub.kt`. Make T040 pass.
- [ ] T046 [P] [US3] Extend `startFailure` in `main/ui/Messages.kt` for `StartKind.Link`. Make T041
  pass.
- [ ] T047 [US3] Extend `main/ui/session/PlaybackStarter.kt` with `StartWhat.Link` (calls
  `playLink`, recovery match per research R8). Make T042 pass.
- [ ] T048 [US3] Extend `main/ui/startplayback/StartPlaybackViewModel.kt` with `onLinkChange`,
  `onLinkPasted`, `onLinkFocusLost`, `onLinkDone`, the `linkText` / `linkMessageShown` state,
  FR-005 exclusivity and `StartWhat.Link` for the consequence (always Replace). Make T043 pass.
- [ ] T049 [US3] Implement `main/ui/startplayback/LinkField.kt` (contracts/start-playback-ui.md):
  - label "PASTE A LINK"
  - field h 52, radius 16, `surface`, padding 0/14, `Icons.Link`, 15 sp text, placeholder "SoundCloud,
    YouTube or stream URL" in `textMuted`
  - keyboard `ImeAction.Done` and `KeyboardType.Uri`
  - the message "Enter a web address (https://…)" in 13 sp `warningText`, exposed as the field's
    error semantics, label "Paste a link"

  Place it in the slot from T030.

**Checkpoint**: all three stories work. Quickstart §2 can run in full.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [ ] T050 [P] Accessibility pass on `main/ui/startplayback/*` against FR-019: Close label, radio
  groups, disabled semantics on unselectable tiles, every target ≥ 44 dp. If any new text/background
  pair is introduced, add it to `test/ui/theme/ContrastTest.kt`.
- [ ] T051 [P] Design check against `design/screens/StartPlayback.dc.html` (sizes, radii, spacing,
  colours via tokens). Any departure not already in spec Assumptions or
  contracts/start-playback-ui.md is fixed or stopped on and asked about (Constitution VI).
- [ ] T052 Run quickstart §1 in full: `./gradlew :androidApp:assembleDebug
  :composeApp:testAndroidHostTest :composeApp:allTests :composeApp:check`. All green, with no
  generated files in `git status`.
- [ ] T053 [P] Create `specs/003-start-playback/verification.md` with the quickstart §2 table (rows
  1–13) and empty result columns for the on-device run, in the style of
  `specs/002-now-playing-move-to-room/verification.md`. Rows 5 and 13 are flagged as hub-assumption
  checks.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: T001 first (version commit), then T002.
- **Foundational (Phase 2)**: needs T002.
  - T003 → T004; T005 → T006; T007 → T008 (T008 needs T004 for `joinMode`).
  - T009 is parallel to everything.
  - T010 → T011 → T012 → T013.
- **US1 (Phase 3)**: needs Phase 2.
  - Tests T014–T020 are all parallel.
  - Then T021, T022, T023, T024 and T025 (T022 ∥ T024 ∥ T028 ∥ T029).
  - T026 needs T021 (`StartWhat`/`StartNames`) and T023–T025; T027 needs T021, T022 and T026.
  - T030 needs T027–T029; T031 needs T030.
- **US2 (Phase 4)**: needs US1 (the view model and screen it extends). T032 ∥ T033 ∥ T034 → T035
  → T036 ∥ T037 → T038.
- **US3 (Phase 5)**: needs US1. It is independent of US2 except through the shared view model and
  screen files, so do it after US2 or merge carefully. T039–T043 parallel → T044, T045 → T046 ∥
  T047 → T048 → T049.
- **Polish (Phase 6)**: needs all stories.

### User Story Dependencies

- **US1 (P1)**: Foundational only.
- **US2 (P1)**: US1 (screen and view model). Its domain part (T032, T035) can start right after
  Foundational.
- **US3 (P2)**: US1. Its domain part (T039, T044) and repository part (T040, T045) can start right
  after Foundational.

### Within Each User Story

- Tests first, seen failing → pure domain → data → session/view model → composables → wiring.

### Parallel Opportunities

- Phase 2: T003 ∥ T005 ∥ T007 ∥ T009 ∥ T010.
- US1: T014–T020 together, then T022 ∥ T024 ∥ T028 ∥ T029.
- US2: T032 ∥ T033 ∥ T034.
- US3: T039–T043 together.
- Across stories, once Foundational is done: T032/T035 (US2 domain) and T039/T044 (US3 domain) can
  run alongside US1's UI work.
- Polish: T050 ∥ T051 ∥ T053.

---

## Parallel Example: User Story 1

```bash
# Tests first (all different files):
Task: "Write StartPlaybackBuilderTest in composeApp/src/commonTest/kotlin/sonora/multiroom/mobile/domain/StartPlaybackBuilderTest.kt"
Task: "Write StartPlaybackTextTest (status part) in composeApp/src/commonTest/kotlin/sonora/multiroom/mobile/ui/startplayback/StartPlaybackTextTest.kt"
Task: "Extend KtorHubRepositoryActionsTest for startSource in composeApp/src/commonTest/kotlin/sonora/multiroom/mobile/data/KtorHubRepositoryActionsTest.kt"
Task: "Extend MessagesTest with StartFailure in composeApp/src/commonTest/kotlin/sonora/multiroom/mobile/ui/MessagesTest.kt"
Task: "Write PlaybackStarterTest in composeApp/src/commonTest/kotlin/sonora/multiroom/mobile/ui/session/PlaybackStarterTest.kt"

# Then independent implementations:
Task: "Implement StartPlaybackText in composeApp/src/commonMain/kotlin/sonora/multiroom/mobile/ui/startplayback/StartPlaybackText.kt"
Task: "Implement StartFailure in composeApp/src/commonMain/kotlin/sonora/multiroom/mobile/ui/Messages.kt"
Task: "Implement SourceRow and TargetTile in composeApp/src/commonMain/kotlin/sonora/multiroom/mobile/ui/startplayback/"
```

---

## Implementation Strategy

### MVP First

1. Phase 1 → Phase 2 (contract reconciled, no visible change).
2. US1: configured sources can be started; the app is no longer only a remote for what others started.
3. **Stop and validate**: build, run all tests, and hand over the APK for quickstart §2 rows 1–3.

### Incremental Delivery

1. Setup + Foundational → 0.1.21 mapped, Now Playing ready for new routes.
2. US1 → start configured sources (MVP).
3. US2 → the "will stop" line. Together with US1 this is the full P1 scope and the safe default to
   ship.
4. US3 → links.
5. Polish → accessibility, design fidelity, verification sheet.

---

## Notes

- [P] = different files, no dependency on unfinished tasks.
- Verify each test fails before implementing (Constitution IV).
- Never commit `composeApp/build/` or hand-edit generated code (Constitution I).
- Announcements are never named as stopping and never appear in status lines. They are named only
  when the chosen source's default is itself an announcement (FR-010).
- A timed-out start is never reported as failed before a fresh snapshot is checked (FR-016a).
- Rows 5 and 13 of quickstart §2 check hub assumptions. If either fails on the device, record it in
  `verification.md` and ask before changing behaviour.
