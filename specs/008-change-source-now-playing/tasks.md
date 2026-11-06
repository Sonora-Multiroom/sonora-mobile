---

description: "Task list for 008 Change What Plays from Now Playing"
---

# Tasks: Change What Plays from Now Playing

**Input**: Design documents from `specs/008-change-source-now-playing/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md),
[data-model.md](data-model.md), [contracts/ui-behaviour.md](contracts/ui-behaviour.md),
[quickstart.md](quickstart.md)

**Tests**: REQUIRED for logic that turns hub data into UI state, view-model behaviour and the back
stack (Constitution IV, research R9). Every test task comes before its implementation task and
MUST be seen failing first. Layout, TalkBack and the real replacement on the hub are checked on the
device (quickstart §2); no Compose UI test setup is added.

**Organization**: grouped by user story. Spec priorities:
- US1 P1: switch the station in the room I'm listening to
- US2 P1: change what a group plays
- US3 P2: picking what already plays does nothing
- US4 P3: change a paused playback

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on an unfinished task)
- **[Story]**: US1–US4 from spec.md

## Path Conventions

- Shared code: `composeApp/src/commonMain/kotlin/sonora/multiroom/mobile/` (abbreviated `main/` below)
- Shared tests: `composeApp/src/commonTest/kotlin/sonora/multiroom/mobile/` (abbreviated `test/` below)

## General rules for every task

- No hub call, no repository or generated-client change, no new dependency. The start is Start
  Playback's existing `POST /api/v2/routes` / `POST /api/v2/play` through `PlaybackStarter`; the
  app never stops the old playback itself (FR-006).
- Everything in `commonMain` / `commonTest`; no `expect`/`actual`. Colours and type only through
  `SonoraTheme` tokens.
- Wording lives in `StartPlaybackText.kt` / `NowPlayingText.kt`, decisions in `domain/`
  functions; screens only draw.
- Start Playback opened from Rooms ("Play in room", "Play something") MUST behave as before except
  for the "already playing" gate (US3).
- Code comments must not cite `FR-`, `SC-`, `US` or research numbers of this feature (`MOB-008` is
  fine).
- Extend the existing test files and their helpers (`Setup`, `live`, `refresh`, the fixtures in
  test/domain/) rather than adding parallel fixtures.

---

## Phase 1: Setup

- [ ] T001 Set `sonora.versionName=0.8.0-alpha` and `sonora.versionCode=8` in gradle.properties as
  the first implementation commit (AGENTS.md "Bump the app version first").

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: the navigation key and back-stack rule every story depends on (research R1, R2). No
screen uses them yet, so nothing user-visible changes.

- [ ] T002 In test/ui/nav/AppBackStackTest.kt add failing tests (data-model.md "Navigation"):
  - `encodeDestination(StartPlayback(null, Target.Room("k"), "Kitchen"))` == `"change:room:k"`;
    `Target.Group("g")` → `"change:group:g"`; decoding gives back the same `fixedTarget` with
    `fixedName = null`; an id containing `:` (`"a:b"`) round-trips; `"change:"`,
    `"change:other:x"` decode to null; `"play"` and `"play:k"` decode as before
    (`StartPlayback(null)`, `StartPlayback("k")`, no fixed target).
  - `replaceTopTwo(d)`: `[Rooms, NowPlaying(r1), StartPlayback(…)]` → `[Rooms, d]`;
    on `[Rooms, StartPlayback(…)]` it behaves as `replaceTop` and keeps Rooms.
  - `showStarted(NowPlaying(r2))`: fixed Start Playback over `NowPlaying(r1)` → `[Rooms,
    NowPlaying(r2)]`, then `pop()` leaves `[Rooms]`; non-fixed Start Playback over Rooms →
    `[Rooms, NowPlaying(r2)]`; non-fixed Start Playback over `NowPlaying(r1)` → only the top is
    replaced; fixed Start Playback directly over Rooms → `[Rooms, NowPlaying(r2)]`; top not a
    Start Playback → stack unchanged.
  - Close without a start: `pop()` on `[Rooms, NowPlaying(r1), StartPlayback(null, Target.Room("k"))]`
    leaves `[Rooms, NowPlaying(r1)]`, the same entry, so its view model is kept.
- [ ] T003 In main/ui/nav/Destinations.kt make T002 pass (depends on T002):
  `data class StartPlayback(val targetId: String?, val fixedTarget: Target? = null, val fixedName: String? = null)`
  (KDoc: `fixedTarget` is the addressed `Target.Room` or `Target.Group` of the Now Playing it was
  opened from; `fixedName` is not saved); `encodeDestination` writes `change:room:<id>` /
  `change:group:<id>` when `fixedTarget` is set (a `Target.Unknown` is never passed; encode it as
  plain `play`); `decodeDestination` reads them with `removePrefix`, before the existing `play:`
  branch; `fun replaceTopTwo(destination)` (never removes the root) and
  `fun showStarted(started: Destination.NowPlaying)` with the rules of T002.

**Checkpoint**: build and all tests green, nothing user-visible changed.

---

## Phase 3: User Story 1 - Switch the station in the room I'm listening to (Priority: P1) 🎯 MVP

**Goal**: "Change source…" on Now Playing opens Start Playback titled "Change source" with the
room as a fixed card; a start replaces the playback and Back then leads to Rooms; Close returns to
the same Now Playing; no false "ended" notice.

**Independent Test**: quickstart §2 rows 1–5, 8, 11, 15–17.

### Tests for User Story 1 (write first, see them fail)

- [ ] T004 [P] [US1] Create test/domain/FixedTargetTest.kt with failing tests for
  `FixedTarget.of(snapshot, target, knownName)` on a **room** (data-model.md "FixedTargetCard",
  table in order): idle → status `Idle`, `unusable` null; own playback → `Playing(…)`; paused →
  `Playing(Paused, …)`; playing via a group → `InGroup(group)`; `enabled = false` → `TurnedOff`;
  `available = false` → `NotConnected`; not in `snapshot.rooms` → status `Removed`, `unusable =
  Removed`. Name: from the snapshot when present, else `knownName`, else the id.
- [ ] T005 [P] [US1] In test/domain/NowPlayingBuilderTest.kt add failing tests:
  `changeSourceVisible` true for a room playback that is playing, paused or failed, and for a group
  playback; false for a route whose target is `Target.Unknown`.
- [ ] T006 [P] [US1] In test/ui/nowplaying/NowPlayingTextTest.kt add failing tests for
  `actionRowLayout(content)` (data-model.md: "`count` 1 (Stop) + `pauseVisible` +
  `changeSourceVisible` + `moveVisible`", "`compact` `count == 4`"): live stream movable → 3, not
  compact; pauseable playing movable → 4, compact; paused (not movable) → 3; failed (no pause, no
  move) → 2; unknown target, not pauseable → 1.
- [ ] T007 [P] [US1] In test/ui/nowplaying/NowPlayingViewModelTest.kt add failing tests (research
  R6): after `onVisible()` then `onHidden()`, a snapshot without the route posts no message to
  `AppMessages` and sets no `exit`; `onVisible()` again while another holder keeps the session
  loop running (acquire a second holder before `onHidden()`) triggers a fetch without waiting for
  the poll interval, and that snapshot without the route exits with `Exit.Ended` and posts
  "Playback on <target> ended" exactly once; the existing "ended elsewhere while visible" test
  still passes unchanged.
- [ ] T008 [P] [US1] In test/ui/startplayback/StartPlaybackTextTest.kt add failing tests for
  `fixedTargetStatusText(card)` on rooms (research R5: as the picker tile — `Playing · KissFM`,
  `Paused · …`, `In Downstairs`, `Idle`, `Turned off`, `Not connected` — and `Removed`) and
  `fixedTargetNoteText(unusable, name)`: `TurnedOff` → "Kitchen is turned off", `NotConnected`
  (room) → "Kitchen is not connected", `Removed` → "Kitchen no longer exists".
- [ ] T009 [US1] In test/ui/startplayback/StartPlaybackViewModelTest.kt add failing tests for
  fixed mode, constructing the view model with `fixedTarget = Target.Room(…)`, `fixedName`:
  - `changeSource` is true before the first snapshot; after it `fixed` is set, `selectedTarget`
    is the fixed target, no source and no link selected, Play disabled until a source is picked.
  - `onSelectTarget(another room)` changes nothing.
  - picking a source on a busy room gives `ConsequenceLine.WillStop` and Play enabled; the
    `PlayLabel` and `StartNames` use the card's name.
  - the room turned off on a refresh → `fixed.unusable == TurnedOff`, `consequence` null, Play
    disabled; turned on again → Play enabled again with the same source still selected.
  - the room removed → card kept with the last known name, Play disabled.
  - restored: a new view model on the same `SavedStateHandle` with `fixedName = null` and a
    snapshot without the room still names it (the saved `fixedName` key).
  - a successful start yields `StartExit.Started` as today.
  - Opened from Rooms (`initialTargetId`, no fixed target), every existing test still passes.

### Implementation for User Story 1

- [ ] T010 [US1] Create main/domain/FixedTarget.kt (depends on T004): `data class
  FixedTargetCard(val target: Target, val name: String, val status: TargetStatus, val unusable:
  Unusable?)`, `enum class Unusable { TurnedOff, AllRoomsTurnedOff, NotConnected, NoRooms,
  Removed }`, and `object FixedTarget { fun of(snapshot: HubSnapshot, target: Target, knownName:
  String?): FixedTargetCard }` deciding `unusable` exactly in the order of data-model.md's table.
  Get `status` from `StartPlaybackBuilder`: factor its per-room and per-group status computation
  out of `build` into internal functions both use, so the picker and the card cannot drift. Add
  `data object Removed : TargetStatus` in main/domain/StartPlaybackBuilder.kt (not selectable;
  never in `StartPlaybackContent.targets`). Implement the **room rows only**: group rows (and the
  group `status`, which needs T024) are written test-first in US2 (T021, T025). Until then `of`
  throws `NotImplementedError` for a `Target.Group`; US1 and US2 ship in the same pull request, so
  no build with a reachable group button is released.
- [ ] T011 [US1] In main/domain/NowPlayingBuilder.kt add `changeSourceVisible: Boolean` to
  `NowPlayingContent.Playback`, set to `route.target !is Target.Unknown` beside `moveVisible`
  (depends on T005).
- [ ] T012 [US1] Add `actionRowLayout(content)` returning `ActionRowLayout(count: Int, compact:
  Boolean)` to main/ui/nowplaying/NowPlayingText.kt (depends on T006, T011).
- [ ] T013 [US1] In main/ui/nowplaying/NowPlayingViewModel.kt add a private `visible` flag, set in
  `onVisible()` and cleared in `onHidden()` beside the existing `session.acquire()` /
  `release()`; `onGone` returns early while not visible; `onVisible()` also calls
  `session.requestRefresh()` (depends on T007). KDoc: a covered Now Playing (Start Playback on
  top) must not announce its playback's end; the refresh it asks for when visible again decides,
  since the skipped snapshots are not replayed and the loop may not restart.
- [ ] T014 [US1] Add `fixedTargetStatusText(card: FixedTargetCard)` and
  `fixedTargetNoteText(unusable: Unusable, name: String)` to
  main/ui/startplayback/StartPlaybackText.kt (depends on T008, T010). Implement every row of
  research R5 for **rooms** (status texts and the room notes, including `Removed`). The group
  wording ("Group · …"; notes "All rooms in Downstairs are turned off", "No room in Downstairs is
  connected", "Downstairs has no rooms") is written after its tests, in T025.
- [ ] T015 [US1] Fixed mode in main/ui/startplayback/StartPlaybackViewModel.kt and
  main/ui/startplayback/StartPlaybackUiState.kt (depends on T009, T010; research R3):
  - constructor gains `fixedTarget: Target?`, `fixedName: String?` after `initialTargetId`;
    update `startPlaybackViewModel(targetId, fixedTarget, fixedName, savedState)` in
    main/AppGraph.kt.
  - UI state gains `fixed: FixedTargetCard? = null` and `changeSource: Boolean = false`
    (initialised from `fixedTarget != null`).
  - New saved key `FIXED_NAME_KEY = "fixedName"`, seeded from `fixedName` when absent; after each
    snapshot store `fixed.name`.
  - In fixed mode: `selectedTarget` is always the fixed target (also on init, ignoring the saved
    `target` key); `rebuild` neither prunes it nor applies the room preselection; `onSelectTarget`
    returns at once; `rebuild` computes `fixed = FixedTarget.of(snapshot, fixedTarget, savedName)`.
  - `derive`: in fixed mode the target name is `fixed.name` and the target is ready only when
    `fixed != null && fixed.unusable == null`; no consequence while unusable. `onPlay` uses the
    same name for `StartNames` and passes the fixed target to `starter.start`.
- [ ] T016 [US1] In main/ui/startplayback/StartPlaybackScreen.kt (depends on T014, T015;
  contracts/ui-behaviour.md "Start Playback opened from Change source"):
  - `Header` title "Change source" when `state.changeSource`, else "Play something".
  - In `Lists`, when `state.changeSource`, keep the "Play in" legend and draw one `FixedTargetCard`
    composable instead of the tile grid (nothing while `fixed` is still null): full width,
    `heightIn(min = 64.dp)`, `SonoraTheme.shapes.tile` (radius 14), `colors.surface`, padding
    horizontal 14 / vertical 10, name `type.body15` SemiBold `colors.text`, status line
    `type.label12` `colors.textMuted` `maxLines = 1` with "…"; no radio, not clickable, alpha 0.5
    when `unusable != null`; semantics: one node, `contentDescription = "<name>, <status>"`.
    Put it in a new file main/ui/startplayback/FixedTargetCard.kt next to `TargetTile.kt`.
  - In `Footer`, when `fixed?.unusable != null`, show `fixedTargetNoteText(...)` in
    `type.body13` `colors.textMuted` above Play.
- [ ] T017 [US1] Add `val Swap = icon("swap", paths = arrayOf("M4 8h14M15 5l3 3-3 3M20 16H6M9 13l-3 3 3 3"))`
  to main/ui/theme/Icons.kt (stroked, like `Arrow`).
- [ ] T018 [US1] Now Playing's action row in main/ui/nowplaying/NowPlayingScreen.kt (depends on
  T011, T012, T017; research R7, contracts/ui-behaviour.md "Now Playing: the action row"):
  - `NowPlayingActions` gains `onChangeSource: () -> Unit = {}`; `NowPlayingScreen` gains a
    parameter `onChangeSource: (target: Target, name: String) -> Unit`, called with
    `content.target.target` and `content.target.name` (the addressed target, never its rooms).
  - `RoundAction` for Change source after Stop and Pause/Resume, before Move to room…, shown when
    `content.changeSourceVisible`: `SonoraIcons.Swap`, caption "Change source…", `label = "Change
    source"`, `colors.surface` / `colors.text`, enabled when `state.controlsEnabled` and neither
    `Stop` nor `Move` is in `state.inFlight`.
  - Not compact: the row keeps 76 dp buttons, 28 dp icons, one-line captions, and its spacing
    becomes 24 dp (was 40). Compact (`actionRowLayout(content).compact`): `Row(Modifier.fillMaxWidth(),
    horizontalArrangement = spacedBy(4.dp))`, each action in `Modifier.weight(1f)`, 64 dp circle,
    24 dp icon, caption `textAlign = Center`, `softWrap = true`, `maxLines = 2`. Give `RoundAction`
    `size`/`iconSize`/`wrapCaption` parameters rather than a second composable.
- [ ] T019 [US1] Wire it in main/ui/nav/AppNavigation.kt (depends on T003, T015, T018): Now Playing's
  `onChangeSource = { target, name -> backStack.push(Destination.StartPlayback(null, target, name)) }`;
  Start Playback's view model gets `key.fixedTarget`, `key.fixedName`; `onStarted` calls
  `backStack.showStarted(Destination.NowPlaying(started.routeId, started.startedAfterSeq, started.targetName))`
  instead of the inline `replaceTop`. System Back keeps detaching the starter for any Start
  Playback (unchanged check).

**Checkpoint**: build and all tests green; on the device quickstart §2 rows 1–5, 8, 11, 15–17 pass.

---

## Phase 4: User Story 2 - Change what a group plays (Priority: P1)

**Goal**: from a group's Now Playing the fixed target is the group (never one of its rooms), the
card says "Group · Playing …", and the start plays on the whole group.

**Independent Test**: quickstart §2 rows 9, 10, 12.

### Tests for User Story 2 (write first, see them fail)

- [ ] T020 [P] [US2] In test/domain/StartPlaybackBuilderTest.kt add failing tests:
  `TargetStatus.GroupPlaying(status, sources)` carries `Playing` for a playing group route,
  `Paused` for a paused one, `Playing` for a live stream (not `LiveStream`); the picker tile text
  stays "Group · Radio Paradise" (`targetStatusText`).
- [ ] T021 [P] [US2] In test/domain/FixedTargetTest.kt add failing group tests, using the member-off
  fixture in test/domain/MemberOffFixture.kt where it fits: playing → `GroupPlaying(Playing, …)`;
  paused; idle with playable members → `GroupMembers`; `enabled = false` → `TurnedOff`; no known
  members → `NoRooms`; every member off → `AllRoomsTurnedOff`; members on but none available →
  `NotConnected`; one member off, others fine → `unusable` null; not in `snapshot.groups` →
  `Removed`.
- [ ] T022 [P] [US2] In test/ui/startplayback/StartPlaybackTextTest.kt add failing tests:
  `fixedTargetStatusText` for groups ("Group · Playing Radio Paradise", "Group · Paused Morning
  playlist", "Group · Kitchen + Living room", "Group · Turned off", "Group · Not connected",
  "Group · No rooms", "Group · Removed") and `fixedTargetNoteText` for `TurnedOff` ("Downstairs is
  turned off"), `AllRoomsTurnedOff` ("All rooms in Downstairs are turned off"), `NotConnected`
  on a group ("No room in Downstairs is connected"), `NoRooms` ("Downstairs has no rooms"),
  `Removed` ("Downstairs no longer exists").
- [ ] T023 [US2] In test/ui/startplayback/StartPlaybackViewModelTest.kt add failing tests with
  `fixedTarget = Target.Group(…)`: a group playback playing on fewer rooms (a member off) keeps the
  group as `selectedTarget`; picking another source gives `WillStop` naming the group playback and
  `wontPlay` naming the member that is off; `onPlay` passes `Target.Group` to the starter.

### Implementation for User Story 2

- [ ] T024 [US2] In main/domain/StartPlaybackBuilder.kt change `GroupPlaying(sources)` to
  `GroupPlaying(status: CardStatus, sources: List<String>)`, `status` = `cardStatus(...)` of the
  group's first non-announcement route, with `CardStatus.LiveStream` mapped to `Playing`
  (depends on T020). Update every `GroupPlaying` use (`targetStatusText` ignores the status).
- [ ] T025 [US2] Make T021–T023 pass (depends on T021–T024, T010, T014): implement the group rows
  of `FixedTarget.of` (data-model.md table, in order, with `status` from the T024
  `GroupPlaying.status`) in main/domain/FixedTarget.kt, replacing the `NotImplementedError`, and
  the group branches of `fixedTargetStatusText` / `fixedTargetNoteText` in
  main/ui/startplayback/StartPlaybackText.kt. Confirm T021 and T022 failed before this task.

**Checkpoint**: build and all tests green; quickstart §2 rows 9, 10, 12 pass.

---

## Phase 5: User Story 3 - Picking what already plays does nothing (Priority: P2)

**Goal**: wherever Start Playback was opened from, selecting the source already playing on the
selected (addressed) target shows "… is already playing in …" and disables Play.

**Independent Test**: quickstart §2 rows 6, 7.

### Tests for User Story 3 (write first, see them fail)

- [ ] T026 [US3] In test/ui/startplayback/StartPlaybackViewModelTest.kt add failing tests
  (research R4): opened from Rooms, a source playing on the selected room → `consequence.line is
  AlreadyPlaying`, `playEnabled` false, `onPlay()` sends nothing to the starter; selecting another
  source, another target, or typing a valid link → Play enabled again; a source playing on a group
  containing the selected room (not on the room) → `WillStop`, Play enabled; the same "already
  playing" case in fixed mode (room and group) → Play disabled.

### Implementation for User Story 3

- [ ] T027 [US3] In `derive` in main/ui/startplayback/StartPlaybackViewModel.kt add
  `&& consequence?.line !is ConsequenceLine.AlreadyPlaying` to `playEnabled` (depends on T026).
  `StartConsequence` stays unchanged. Check in main/ui/startplayback/StartPlaybackScreen.kt that a
  disabled Play keeps `clickable(enabled = false)` (announced disabled) and the dimmed accent
  (`accent` at 0.38), with the line directly above it.

**Checkpoint**: build and all tests green; quickstart §2 rows 6, 7 pass.

---

## Phase 6: User Story 4 - Change a paused playback (Priority: P3)

**Goal**: a paused playback offers Change source…, and its "will stop" line reads as for a playing
one.

**Independent Test**: quickstart §2 rows 13, 14.

- [ ] T028 [US4] In test/domain/StartConsequenceTest.kt add a characterization test (it documents
  existing behaviour, so it is expected to pass at once, the one exception to "seen failing"; if it
  fails, fix `StartConsequence` minimally): a paused route on the target gives `WillStop` with the
  same `AffectedPlayback` as a playing one. In test/ui/nowplaying/NowPlayingTextTest.kt confirm
  the paused row of T006 (Stop · Resume · Change source…, count 3) covers this story.

**Checkpoint**: all tests green; quickstart §2 rows 13, 14 pass (row 13 also checks the spec's
assumption that the hub replaces a paused playback; record the answer).

---

## Phase 7: Polish & Cross-Cutting Concerns

- [ ] T029 Run the local checks and read each exit code (`cmd > log; echo $?`):
  `./gradlew :androidApp:assembleDebug`, `:composeApp:testAndroidHostTest`, `:composeApp:allTests`,
  `:composeApp:check`. Record the commands, date and test counts in
  specs/008-change-source-now-playing/verification.md, created in 007's format: build under test,
  device, status legend, one row per quickstart §2 check, "Issues found".
- [ ] T030 [P] Add a `## [0.8.0-alpha] - Unreleased` entry to CHANGELOG.md above 0.7.0-alpha (spec
  folder link, "pull request to follow"). Added: "Change source…" on Now Playing opens Start
  Playback for that room or group and replaces what plays there. Changed: Play is disabled when
  the chosen source already plays on the chosen room or group.
- [ ] T031 [P] Update AGENTS.md: under Design "Agreed details", after the "Move to room…" line,
  add "The change-source action is labelled **"Change source…"**; it opens Start Playback for the
  playback's own room or group, with no target picker." Under "Domain rules", note that Start
  Playback never sends a start for a source already playing on the chosen target.
- [ ] T032 [P] Delete docs/backlog/change-source-from-now-playing.md and its row in
  docs/backlog/INDEX.md; update links to it (`grep -rn change-source-from-now-playing`), except in
  specs/008-change-source-now-playing/. If the file is still untracked in the working tree, ask
  the user before deleting it.
- [ ] T033 **Local**: run quickstart §2 rows 1–18 on the POCO against the real hub, following
  AGENTS.md "On-device verification". Before the first start, note the volumes and set them to 1,
  and tell the user the run replaces what plays in `A`, `B`, `G`. At the end stop the test routes,
  then restore the volumes, rooms turned off, screen timeout and Do Not Disturb. Record the
  evidence in specs/008-change-source-now-playing/verification.md, including whether the
  four-column row wraps "Change source…" cleanly, whether the hub replaced the paused
  playback (row 13), and, from row 12, that the covered Now Playing really stopped announcing
  (the view model relies on the screen leaving composition while Start Playback is on top). If the hub did not replace the paused playback, stop and ask the user before
  changing anything (the spec assumes it does).
- [ ] T034 Update docs/testing/full-app-test.md for this feature: add rows for quickstart §2 in
  their sections (N for the action row and Change source, S for the fixed Start Playback and the
  "already playing" gate, X for row 16); rewrite S6 from "Play opens the same playback" to "Play
  disabled, nothing sent"; extend the fixture with a group `G` and a pauseable source if a row
  needs them; bump the header (covers `0.8.0-alpha`, hub API 0.1.33, date).

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (T001)**: first.
- **Foundational (T002–T003)**: after T001; blocks US1 (T019) and everything that navigates.
- **US1 (T004–T019)**: after T003. The MVP.
- **US2 (T020–T025)**: needs T010 and T014 (US1's room-only `FixedTarget` and texts); T024 also
  changes the picker's model, so run it after T010 to avoid conflicts in StartPlaybackBuilder.kt.
  T025 comes after T021–T024 and holds all group logic. Ship US1 and US2 together.
- **US3 (T026–T027)**: independent of US1/US2 for the Rooms case; its fixed-mode tests need T015.
- **US4 (T028)**: after T012 (US1's action row).
- **Polish (T029–T034)**: after the stories. T033 needs an installed build. T034 is last.

### Within Each User Story

- Tests first, seen failing, then the implementation.
- Domain before view model before screen before navigation.

### Parallel Opportunities

- T004, T005, T006, T007, T008 (different test files) at the start of US1; then T010, T011, T013,
  T017 (different production files).
- T020, T021, T022 at the start of US2.
- T030, T031, T032 alongside T029.
- T009, T023, T026 share StartPlaybackViewModelTest.kt: write them one after the other.

## Parallel Example: User Story 1 tests

```text
Task: "FixedTarget room tests in composeApp/src/commonTest/kotlin/sonora/multiroom/mobile/domain/FixedTargetTest.kt"
Task: "changeSourceVisible tests in composeApp/src/commonTest/kotlin/sonora/multiroom/mobile/domain/NowPlayingBuilderTest.kt"
Task: "actionRowLayout tests in composeApp/src/commonTest/kotlin/sonora/multiroom/mobile/ui/nowplaying/NowPlayingTextTest.kt"
Task: "gone-while-hidden tests in composeApp/src/commonTest/kotlin/sonora/multiroom/mobile/ui/nowplaying/NowPlayingViewModelTest.kt"
Task: "fixed card texts in composeApp/src/commonTest/kotlin/sonora/multiroom/mobile/ui/startplayback/StartPlaybackTextTest.kt"
```

---

## Implementation Strategy

### MVP First

1. T001–T003.
2. US1 (T004–T019): Change source for a single room, quiet end, back stack.
3. **Stop and validate**: build, all tests, quickstart §2 rows 1–5, 8.

### Incremental Delivery

1. Setup + Foundational.
2. US1, rooms (MVP).
3. US2, groups.
4. US3, the "already playing" gate (also changes Start Playback from Rooms).
5. US4, paused playbacks.
6. Polish: verification, changelog, AGENTS.md, backlog, and the full app test last.

---

## Notes

- [P] = different files, no dependency on unfinished tasks.
- Verify each test fails before implementing (Constitution IV).
- If a task needs a decision the spec does not cover, stop and ask.
