---

description: "Task list for 006 Rooms a Playback Is Really On, and Plain Refusal Messages"
---

# Tasks: Rooms a Playback Is Really On, and Plain Refusal Messages

**Input**: Design documents from `specs/006-route-outputs-refusals/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md),
[data-model.md](data-model.md), [contracts/ui-behaviour.md](contracts/ui-behaviour.md),
[quickstart.md](quickstart.md)

**Tests**: REQUIRED. They come from Constitution IV (test-first for logic) and spec FR-011/FR-012.
Every test task comes before its implementation task and MUST be seen failing first.

**Organization**: grouped by user story. Spec priorities:
- US1 P1: a room that was turned off is no longer shown as playing
- US2 P2: a refusal says what is turned off
- US3 P2: moving right after turning a room back on just works (one quiet retry)

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on an unfinished task)
- **[Story]**: US1–US3 from spec.md

## Path Conventions

- Shared code: `composeApp/src/commonMain/kotlin/sonora/multiroom/mobile/` (abbreviated `main/` below)
- Shared tests: `composeApp/src/commonTest/kotlin/sonora/multiroom/mobile/` (abbreviated `test/` below)

## General rules for every task

- No new hub call. Never `PUT /groups/{id}/volume`, never a `joinMode`. Only `/api/v2/**`.
- Nothing Android-only in `commonMain`/`commonTest`; `ui/` and `domain/` never import the generated
  package (`verifyLayering`). Colours only through `SonoraTheme` tokens.
- One rule decides which rooms a playback occupies: `occupiedRooms` in
  main/domain/PlaybackRules.kt. No screen re-derives it.
- Error copy lives only in main/ui/Messages.kt, Move sheet note copy only in
  main/ui/nowplaying/DestinationText.kt, exactly as in
  [contracts/ui-behaviour.md](contracts/ui-behaviour.md). No message contains the hub's wording.
- A route without `outputs` (`null`) behaves exactly as in 005: every existing 001–005 test stays
  green without edits to its assertions (spec SC-005). Test helpers may gain an optional
  `outputs: List<String>? = null` parameter.
- The "2026-10-17" fixture below is the contract's: rooms `office` "Office Speaker" (off, 40),
  `kitchen` "Kitchen Speakers" (30), `bedroom` "Bedroom" (20), `bathroom` "Bathroom" (10); group
  `living` "Living Room" = [`office`, `kitchen`]; route `r1` KissFM (`http://…`, not pauseable,
  transferable) on group `living`, ACTIVE. Variant A: `outputs = ["kitchen"]`; B: `outputs = null`;
  C: `office` on, `outputs = ["kitchen", "office"]`.
- Code comments must not cite `FR-`, `SC-`, `US` or research numbers of this feature.

---

## Phase 1: Setup

**Purpose**: version bump and the design update Constitution VI requires before the UI code.

- [ ] T001 Set `sonora.versionName=0.6.0-alpha` and `sonora.versionCode=6` in gradle.properties as
  the first implementation commit (AGENTS.md "Bump the app version first").
- [ ] T002 **Local, needs the user**: update the Claude Design canvas
  (https://claude.ai/artifact/R7e4yABDYUuytc64XxNNKK, Transfer / Move to room sheet) per research
  R9: one "<room> only" sample row whose note reads "Leaves Living Room" in the existing note style
  (textMuted, not the warning colour). Get the user's approval of the change.
- [ ] T003 Copy the updated canvas screen to design/screens/Transfer.dc.html (depends on T002).

**Checkpoint**: T030 (the note's copy and use) does not start before T003 is committed. Every other
task may run while T002 waits for the user.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: the domain field and the refusal-reason names that the stories build on. No behaviour
changes here.

- [ ] T004 Add `val outputs: List<String>? = null` as the last parameter of `Route` in
  main/domain/Models.kt, with KDoc: "The rooms the hub says this route plays on right now, in its
  order; null when the hub did not say (before API 0.1.22)." Do not map it yet. All existing tests
  stay green.
- [ ] T005 [P] In main/data/HubRepository.kt add
  `object RefusalReason { const val ROUTE_LIMIT_REACHED = "ROUTE_LIMIT_REACHED"; const val INPUT_ALREADY_ON_OUTPUT = …; const val OUTPUT_DISABLED = …; const val GROUP_DISABLED = …; const val INPUT_DISABLED = …; const val ALL_MEMBERS_DISABLED = …; const val ROUTE_CHANGING_OUTPUTS = … }`
  next to `HubError`, each with the hub API version that added it (0.1.21 / 0.1.22 / 0.1.25). Make
  `startFailure` in main/ui/Messages.kt use the two existing constants instead of string literals.
  Also correct the `transferRoute` KDoc: since API 0.1.22 the answer is the same route with the same
  id, and before that it was a new route; callers follow the returned id either way.

**Checkpoint**: build and all tests green, nothing user-visible changed.

---

## Phase 3: User Story 1 - A room that was turned off is no longer shown as playing (Priority: P1) 🎯 MVP

**Goal**: every screen shows a playback only in the rooms the hub lists; with no list, as before.

**Independent Test**: fixture A on Rooms, Now Playing and Move to room…: Kitchen Speakers only,
Office Speaker under "Off", nothing names it as playing or stopping. Variant C: both again
(quickstart §2 rows 1–6).

### Tests for User Story 1 (write first, see them fail)

- [ ] T006 [P] [US1] In test/data/KtorHubRepositorySnapshotTest.kt add `routeOutputsAreMapped`:
  a route JSON without `outputs` → `Route.outputs == null`; `"outputs":[]` → `emptyList()`;
  `"outputs":["kitchen","","kitchen","office"]` → `["kitchen", "office"]` ("blanks dropped,
  duplicates dropped, hub order kept", data-model.md).
- [ ] T007 [P] [US1] Create test/domain/OccupiedRoomsTest.kt for
  `occupiedRooms(route, rooms, groups)` with one test per row of the data-model table: group G =
  [`office`, `kitchen`] with `outputs` `["kitchen","office"]` → office, kitchen (group order);
  `["kitchen"]` → kitchen; `null` → office, kitchen; `[]` → office, kitchen; `["ghost","kitchen"]`
  → kitchen; `["ghost"]` → office, kitchen (fallback); `["kitchen","bedroom"]` → kitchen, bedroom;
  room target `bedroom` with `["bedroom"]` and with `null` → bedroom; room target unknown id with
  `null` → empty; unknown target with `["kitchen"]` → kitchen, with `null` → empty. Assert room ids
  in order.
- [ ] T008 [P] [US1] In test/domain/RoutesByRoomTest.kt add: with fixture A, `occupancy` has
  `kitchen` → r1 and no `office` key, and `routesByRoom` lists r1 under `kitchen` only; with B,
  both rooms (as today). Extend the file's `route(...)` helper with `outputs: List<String>? = null`.
- [ ] T009 [P] [US1] In test/domain/RoomsBuilderTest.kt add the contract's Rooms rows for A/B/C:
  card `memberNames` (A `["Kitchen Speakers"]`, B and C `["Office Speaker", "Kitchen Speakers"]`),
  `volume` (A 30, B/C 40), `memberVolumes` (A `{kitchen=30}`), `inUse` (A 1, B 2, C 2) of
  `total` 4, idle rows (A: Bathroom and Bedroom NothingPlaying, Office Speaker TurnedOff; B: no
  Office Speaker row; C: Bathroom, Bedroom). Extend the `route(...)` helper with `outputs`.
- [ ] T010 [P] [US1] In test/domain/NowPlayingBuilderTest.kt add the A/B/C cases: `target.name`
  "Living Room" and `target.memberNames` (A `["Kitchen Speakers"]`, B/C both, group order);
  `volume.main.roomVolumes` (A `{kitchen=30}`, B/C both); `volume.members` keys
  (A `["member:kitchen"]`, B/C `["member:office", "member:kitchen"]`). Extend the `route(...)` helper.
- [ ] T011 [P] [US1] In test/domain/MoveDestinationsTest.kt add the contract's Move rows: A —
  "Kitchen Speakers only" note `MoveDestinationNote.LeavesGroup("Living Room")`, selectable;
  "Office Speaker only" `TurnedOff`, not selectable; no row with label "Office Speaker" or
  "Kitchen Speakers" (no duplicate plain row); B — "Kitchen Speakers only" →
  `OthersStop(["Office Speaker"])`; C — "Office Speaker only" → `OthersStop(["Kitchen Speakers"])`.
  Add: a second route r2 on group [`bedroom`, `kitchen`] with `outputs = ["bedroom"]` makes the
  "Kitchen Speakers" plain row of a third playback read `Idle`, not "will stop". Extend the
  `route(...)` helper. In test/ui/nowplaying/DestinationTextTest.kt add
  `destinationNoteText(LeavesGroup("Living Room")) == "Leaves Living Room"` and
  `LeavesGroup("Living Room").warning == false`. Write these first; they will not compile until
  T019 adds `LeavesGroup`, which is their failing state.
- [ ] T012 [P] [US1] In test/domain/StartConsequenceTest.kt add the contract's extra case: rooms
  `bedroom`, `kitchen` on; group [`bedroom`, `kitchen`]; KissFM on that group with
  `outputs = ["kitchen"]`; starting a source on Bedroom gives `line == null`; with
  `outputs = null` it gives `WillStop([AffectedPlayback("KissFM", "<group name>")])` (as in 005).
- [ ] T013 [P] [US1] In test/domain/SettingsBuilderTest.kt and test/domain/SettingsConfirmTest.kt
  (helpers in test/domain/SettingsFixtures.kt gain `outputs`): with fixture C, the Office Speaker
  room row is `RoomStatus.Playing(["KissFM"])`; with a group playback whose `outputs` leave out an
  *enabled* member, that member's row is not `Playing`, and `turnOffConfirmation(Room)` for it is
  null; `turnOffConfirmation(Group "living")` in A gives
  `TurnOffGroup("Living Room", ["KissFM"], ["Kitchen Speakers"])`, in B and C
  `members = ["Office Speaker", "Kitchen Speakers"]`.
- [ ] T014 [P] [US1] In test/ui/rooms/RoomsViewModelControlsTest.kt add: with fixture A on the
  `FakeHub`, dragging the Living Room card's pill to 60 and releasing sends
  `setRoomVolume("kitchen", 60)` only, never for `office`; with B it sends `office` 60 and
  `kitchen` 45, as `GroupVolume.scale` does today.

### Implementation for User Story 1

- [ ] T015 [US1] In main/data/ApiMapping.kt `RouteResponse.toRoute()` map
  `outputs = outputs?.filter { it.isNotBlank() }?.distinct()`. Makes T006 pass.
- [ ] T016 [US1] In main/domain/PlaybackRules.kt add
  `internal fun occupiedRooms(route: Route, rooms: Map<String, Room>, groups: Map<String, Group>): List<Room>`
  exactly as the data-model pseudocode: the known rooms in `route.outputs`. A group target orders
  them by the group's `memberIds`, then other listed rooms in hub order; other targets keep hub
  order. When the list is null, empty or has no known room, fall back: room target → its room if
  known; group target → known members in group order; unknown → empty. Add
  `internal fun describeRoute(route, rooms, groups, masterMuted): TargetDescription` per the
  data-model field table: title/`isGroup`/`muted` from `describeTarget`, the rest from
  `occupiedRooms`, `volume = null` and `adjustable = false` for an unknown target. Make
  `occupancy` and `routesByRoom` use `occupiedRooms` and update their KDoc ("a route occupies the
  rooms the hub lists, else …"). Makes T007, T008, T012 and the T013 room cases pass.
- [ ] T017 [US1] In main/domain/RoomsBuilder.kt use `describeRoute(route, rooms, groups, master)`
  instead of `describeTarget(route.target, …)`. Makes T009 and T014 pass.
- [ ] T018 [US1] In main/domain/NowPlayingBuilder.kt build `TargetLine` from
  `describeRoute`. In `volumeSection`, take the group's rooms from `occupiedRooms(route, …)`
  instead of `group.memberIds`; pass the route in, and return null when it is empty, as today.
  Makes T010 pass.
- [ ] T019 [US1] In main/domain/MoveDestinations.kt add
  `data class LeavesGroup(val group: String) : MoveDestinationNote` (not a warning, selectable),
  and use `current = occupiedRooms(route, …)`. Plain room rows skip rooms in `current` and every
  member of the current group. A "<member> only" row that is on and connected reads
  `OthersStop(names of current minus the member)`, or `LeavesGroup(currentGroup.name)` when that is
  empty. In main/ui/nowplaying/DestinationText.kt add
  `is MoveDestinationNote.LeavesGroup -> "Leaves ${note.group}"`. Makes T011 pass.
- [ ] T020 [US1] In main/domain/SettingsConfirm.kt make `TurnOffGroup.members` the occupied rooms
  of the group's own live routes. Take the union of `occupiedRooms` over them, keep the group's
  member order, put listed rooms outside the group after them, and fall back to the known members
  when the union is empty. Update the KDoc. Makes the T013 group cases pass. Confirm that
  main/domain/SettingsBuilder.kt, StartConsequence.kt and StartPlaybackBuilder.kt need no edit:
  T012/T013 pass through `routesByRoom`.

**Checkpoint**: US1 works on its own. Build and all tests green, with no 001–005 assertion edited.

---

## Phase 4: User Story 2 - A refusal says what is turned off (Priority: P2)

**Goal**: start and move refusals for something turned off name it, and the lists refresh at once.

**Independent Test**: turn a room off by `curl` while Start Playback is open on it, then Play:
"<room> is turned off", and the room shows as off right after (quickstart §2 rows 8–12).

### Tests for User Story 2 (write first, see them fail)

- [ ] T021 [P] [US2] In test/ui/MessagesTest.kt add the contract's start table, using the file's
  `names`, `linkNames` and `roomName` (extend the map with `"office" to "Office Speaker"`). Build
  the errors as `HubError.Rejected(409, null, reason, outputId)`. Cover: `OUTPUT_DISABLED` with a
  known `outputId` for source and link; with an unknown and with no `outputId` (names the chosen
  target); `GROUP_DISABLED`; `ALL_MEMBERS_DISABLED` ("All rooms in <target> are turned off");
  `INPUT_DISABLED` for a source ("Jazz24 is turned off") and for a link ("Couldn't start
  playback"); an unknown reason `SOMETHING_NEW` for both kinds ("Couldn't start playback"). Assert
  `needsRefresh` is true for every `StartFailure.TurnedOff` and false for every other
  `StartFailure`. Add the contract's move table for `actionErrorMessage(UserAction.Move("Bedroom"),
  "KissFM", error, roomName)`, except the `ROUTE_CHANGING_OUTPUTS` row (T026). Assert that a
  problem's title/detail never appears (the error carries none; the messages are fixed strings).
- [ ] T022 [P] [US2] In test/ui/session/PlaybackStarterTest.kt add: a start answered
  `Rejected(409, null, "OUTPUT_DISABLED", "bedroom")` ends `Failed(TurnedOff(Room("Bedroom")))` and
  triggers one refresh (the session's snapshot calls rise by one), on the screen and after
  `detach()` (then `AppMessages` gets "Bedroom is turned off"). A `ROUTE_LIMIT_REACHED` refusal
  triggers no extra refresh.
- [ ] T023 [P] [US2] In test/ui/nowplaying/NowPlayingViewModelTest.kt add
  `aMoveRefusedBecauseTheRoomIsOffNamesIt`: `transferResult` answers
  `Rejected(409, null, "OUTPUT_DISABLED", "office")` with "office" → "Office Speaker" in the
  session's snapshot; the message is "Office Speaker is turned off"; the sheet closes; exactly one
  transfer is sent; a refresh is requested.

### Implementation for User Story 2

- [ ] T024 [US2] In main/ui/Messages.kt add
  `sealed interface TurnedOff { data class Room(val name: String); data class Group(val name: String); data class Source(val name: String); data class AllRooms(val group: String) }`,
  `fun turnedOff(error: HubError, target: String, source: String?, roomName: (String) -> String?): TurnedOff?`
  per the data-model table ("The reason decides whatever the HTTP status"; `INPUT_DISABLED` with
  no source → null) and `fun turnedOffMessage(t: TurnedOff): String` with the four texts. Add
  `StartFailure.TurnedOff(val what: TurnedOff)` and
  `val needsRefresh: Boolean get() = this is TurnedOff` on `StartFailure`. Check it in
  `startFailure` after the two existing reasons and before the status rules. Map it in
  `startFailureMessage`. Give `actionErrorMessage` a last parameter
  `roomName: (String) -> String? = { null }`; in its `Move` branch return
  `turnedOffMessage(it)` when `turnedOff(error, action.destination, targetName, roomName)` is not
  null, else the current text. Makes T021 pass (except the T026 row).
- [ ] T025 [US2] In main/ui/session/PlaybackStarter.kt make `fail(failure)` call
  `session.requestRefresh()` when `failure.needsRefresh`, before posting or publishing it. In
  main/ui/nowplaying/NowPlayingViewModel.kt pass a room-name lookup from the session's latest
  snapshot to `actionErrorMessage` for `UserAction.Move` (only `reportFailure` changes; the other
  actions keep the default). Makes T022 and T023 pass.

**Checkpoint**: US1 and US2 both work. Build and all tests green.

---

## Phase 5: User Story 3 - Moving right after turning a room back on just works (Priority: P2)

**Goal**: a move refused as `ROUTE_CHANGING_OUTPUTS` is retried once after 1 s, so it succeeds
without a message. A second refusal for the same reason shows "Try again in a moment."

**Independent Test**: the contract's retry table in `NowPlayingViewModelTest` on virtual time.
On the device: turn a group member back on, then move straight away (quickstart §2 row 7).

### Tests for User Story 3 (write first, see them fail)

- [ ] T026 [P] [US3] In test/ui/MessagesTest.kt add the move row
  `ROUTE_CHANGING_OUTPUTS` → "Couldn't move KissFM to Bedroom. Try again in a moment."
- [ ] T027 [P] [US3] In test/ui/nowplaying/NowPlayingViewModelTest.kt add one test per row of the
  contract's retry table. Script `FakeHub.transferResult` with an `ArrayDeque` of answers or a call
  counter. Use `advanceTimeBy` / `runCurrent` as the file does today.
  (1) changing → Ok: no message, the sheet closes, the returned id is followed, and two
  `transferRoute` calls with the same route id and target, the second at ≥ 1000 ms virtual time
  after the first (`Call.time`).
  (2) changing → changing: message "Couldn't move <source> to <destination>. Try again in a moment.",
  two calls, no third after a further `advanceTimeBy(5_000)`.
  (3) changing → `OUTPUT_DISABLED` with `outputId`: "<room> is turned off" (needs T024).
  (4) changing → Unreachable: "… Can't reach the hub."
  (5) changing, then the view model is cleared during the wait (close the scope the way the file's
  other "screen closed" tests do): one call only, no message.
  (6) `OUTPUT_DISABLED` first: one call, no retry.
  Also, during the wait: `NowPlayingAction.Move` is in `inFlight`, the sheet is still open, and a
  refresh whose snapshot lacks the route does not set `exit`.

### Implementation for User Story 3

- [ ] T028 [US3] In main/ui/Messages.kt, in the `Move` branch of `actionErrorMessage`, after the
  turned-off check: `RefusalReason.ROUTE_CHANGING_OUTPUTS` → "Couldn't move $targetName to
  ${action.destination}. Try again in a moment." Makes T026 pass.
- [ ] T029 [US3] In main/ui/nowplaying/NowPlayingViewModel.kt `onConfirmMove`: when the first
  `transferRoute` answer is `Rejected` with reason `RefusalReason.ROUTE_CHANGING_OUTPUTS`,
  `delay(MOVE_RETRY_DELAY)` (`1.seconds` in the companion) and send the same transfer once more,
  with Move still in flight and the sheet left open. Then handle that second answer with the
  existing Ok / Err code; only the first answer may trigger the retry. Keep the single
  `session.requestRefresh()` after the final answer. Makes T027 pass.

**Checkpoint**: all three stories work; build and all tests green.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [ ] T030 Check the Move sheet against design/screens/Transfer.dc.html (depends on T003, T019):
  the "Leaves <group>" note uses the same text style and colour as the other non-warning notes. If
  the design differs, adjust main/ui/nowplaying/MoveSheet.kt.
- [ ] T031 Run the local checks and read each exit code (`cmd > log; echo $?`):
  `./gradlew :androidApp:assembleDebug`, `:composeApp:testAndroidHostTest`, `:composeApp:allTests`,
  `:composeApp:check`. Record the commands, date and test counts in
  specs/006-route-outputs-refusals/verification.md. Create it in 005's format: build under test,
  device, status legend, one row per quickstart §2 check, "Issues found".
- [ ] T032 [P] Add a `## [0.6.0-alpha]` entry to CHANGELOG.md (spec folder and PR link). Under
  Fixed: a room turned off while its group plays is no longer shown as playing, with no volume pill
  and no "stops" note for it. Under Changed: refusals for a room, group or source that is off name
  it; a move made while a room rejoins is retried once; the Move sheet's "<room> only" row can read
  "Leaves <group>".
- [ ] T033 [P] Update AGENTS.md (FR-013). In "Hub gaps", the "No rooms in a playback's answer" item
  says the app uses `RouteResponse.outputs` since 0.6.0-alpha, falling back to the group's members
  without it. The domain rule "what plays on output X" reads the route's `outputs` first. The API
  0.1.22 paragraph lists the reasons the app now maps (`OUTPUT_DISABLED`, `GROUP_DISABLED`,
  `INPUT_DISABLED`, `ALL_MEMBERS_DISABLED`, `ROUTE_CHANGING_OUTPUTS` with one retry) and drops the
  link to the backlog item.
- [ ] T034 [P] Delete docs/backlog/refusal-reasons-and-route-outputs.md and its row in
  docs/backlog/INDEX.md. Remove or update links to it from docs/backlog/rooms-with-several-playbacks.md
  and anywhere else (`grep -rn refusal-reasons-and-route-outputs`).
- [ ] T035 **Local**: run quickstart §2 rows 1–12 on the POCO against the real hub, following
  AGENTS.md "On-device verification". Note the volumes and set them to 1 first, stop test routes
  before restoring the volumes, and turn every output, group and source back on at the end. Record
  the evidence in specs/006-route-outputs-refusals/verification.md.
- [ ] T036 Update docs/testing/full-app-test.md for this feature. Add rows for every quickstart §2
  check, in their sections (R, N, S, ST). Update N6's "<G1> only" note expectation for a group
  with a member off ("Leaves <G>"). Extend the fixture if a row needs a two-room group with a
  member turned off. Bump the header (features covered through 006, app 0.6.0-alpha, hub API
  0.1.25, date).

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (T001–T003)**: T001 first. T002 → T003 is a local step with the user; it blocks only T030.
- **Foundational (T004–T005)**: after T001. T004 blocks US1. T005 blocks US2 and US3.
- **US1 (T006–T020)**: after T004. Write all of T006–T014 first. T015 and T016 come before
  T017–T020. T019's tests (T011) do not compile until T019, which is their failing state.
- **US2 (T021–T025)**: after T005. Independent of US1 (other files, except NowPlayingViewModel.kt,
  which US1 does not touch).
- **US3 (T026–T029)**: after T005. Case (3) of T027 needs T024 (US2). T028 and T024 both edit the
  `Move` branch in Messages.kt, so do them one after the other. T029 and T025 both edit
  NowPlayingViewModel.kt: do US2 before US3.
- **Polish (T030–T036)**: after the stories. T035 needs an installed build. T036 is last.

### Within Each User Story

- Tests first, seen failing. Then the data mapping, then the shared rule, then the builders, then
  the text and the view model.

### Parallel Opportunities

- T005 alongside T004.
- T006–T014: nine different test files.
- T017, T018, T019, T020 (different files), once T016 is in.
- T021, T022, T023; T026, T027.
- US1 and US2 can run in parallel by two people.
- T032, T033, T034 alongside T031.

## Parallel Example: User Story 1

```text
# Tests together:
Task: "outputs mapping in composeApp/src/commonTest/kotlin/sonora/multiroom/mobile/data/KtorHubRepositorySnapshotTest.kt"
Task: "occupiedRooms table in composeApp/src/commonTest/kotlin/sonora/multiroom/mobile/domain/OccupiedRoomsTest.kt"
Task: "Rooms A/B/C in composeApp/src/commonTest/kotlin/sonora/multiroom/mobile/domain/RoomsBuilderTest.kt"
Task: "Move rows in composeApp/src/commonTest/kotlin/sonora/multiroom/mobile/domain/MoveDestinationsTest.kt"

# After T015–T016, builders together:
Task: "describeRoute in composeApp/src/commonMain/kotlin/sonora/multiroom/mobile/domain/RoomsBuilder.kt"
Task: "occupied rooms in composeApp/src/commonMain/kotlin/sonora/multiroom/mobile/domain/NowPlayingBuilder.kt"
Task: "LeavesGroup in composeApp/src/commonMain/kotlin/sonora/multiroom/mobile/domain/MoveDestinations.kt"
Task: "TurnOffGroup.members in composeApp/src/commonMain/kotlin/sonora/multiroom/mobile/domain/SettingsConfirm.kt"
```

---

## Implementation Strategy

### MVP First

1. T001, T004.
2. US1 (T006–T020): the screens stop showing a turned-off room as playing.
3. **Stop and validate**: build, all tests, quickstart §2 rows 1–6.

### Incremental Delivery

1. Setup + Foundational.
2. US1, the truthful screens (MVP).
3. US2, the "… is turned off" messages.
4. US3, the quiet retry.
5. Polish: design check, verification, changelog, AGENTS.md, backlog, and the full app test last.

---

## Notes

- [P] = different files, no dependency on unfinished tasks.
- Verify each test fails before implementing (Constitution IV).
- The app never decides on its own which rooms play: it follows the hub's list, and falls back
  only when the hub gives none.
- If a task needs a decision the spec does not cover, stop and ask.
