---

description: "Task list for 005 Confirm Before Turning Off a Playing Source"
---

# Tasks: Confirm Before Turning Off a Playing Source

**Input**: Design documents from `specs/005-source-off-confirm/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md),
[data-model.md](data-model.md), [contracts/settings-ui.md](contracts/settings-ui.md),
[quickstart.md](quickstart.md)

**Tests**: REQUIRED. They come from Constitution IV (test-first for logic) and spec FR-007/FR-009.
Every test task comes before its implementation task and MUST be seen failing first.

**Organization**: grouped by user story. Spec priorities:
- US1 P1: turning off a source that plays asks first
- US2 P1: turning off an idle source stays one tap, with no false message
- US3 P2: seeing which sources will ask ("Playing in <where>")

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on an unfinished task)
- **[Story]**: US1–US3 from spec.md

## Path Conventions

- Shared code: `composeApp/src/commonMain/kotlin/sonora/multiroom/mobile/` (abbreviated `main/` below)
- Shared tests: `composeApp/src/commonTest/kotlin/sonora/multiroom/mobile/` (abbreviated `test/` below)

## General rules for every task

- No hub contract or repository change: the only call is the existing
  `HubRepository.setSourceEnabled` (research R1). The app never stops a playback itself.
- Nothing Android-only in `commonMain`/`commonTest`; `ui/` and `domain/` never import the generated
  package (`verifyLayering`). Colours only through `SonoraTheme` tokens.
- Copy lives only in `main/ui/settings/SettingsText.kt`, exactly as in
  [contracts/settings-ui.md](contracts/settings-ui.md).
- Do not edit `specs/004-settings-sources/` (spec FR-008).
- Every 001–004 test stays green, except the "keeps playing" assertions that T013–T015 replace.
- Code comments must not cite `FR-`, `SC-`, `US` or research numbers of this feature.

---

## Phase 1: Setup

**Purpose**: version bump and the design update that Constitution VI requires before code.

- [X] T001 Set `sonora.versionName=0.5.0-alpha` and `sonora.versionCode=5` in gradle.properties as
  the first implementation commit (AGENTS.md "Bump the app version first").
- [X] T002 **Local, needs the user**: update the Claude Design canvas
  (https://claude.ai/artifact/R7e4yABDYUuytc64XxNNKK, Settings screen, Sources tab) per research R7:
  one configured source shown playing, its second line reading "Playing in Living Room" in accent
  `#F2A541` in place of its kind line; and the source's turn-off dialog ("Turn off <source>?",
  body "<source> is playing in <where>. Turning it off stops playback there.", "Keep playing" /
  "Turn off") in the existing room/group dialog's style. Get the user's approval of the change.
- [X] T003 Copy the updated canvas screen to design/screens/Settings.dc.html (depends on T002).
  Note in research R4 whether the canvas keeps the kind line's text style (`label12`) or uses the
  rooms' `body13` for the playing line; T020 follows it.

**Checkpoint**: no story task (T006 onwards) starts before T003 is committed: the spec requires the
design update before implementation (FR-010, Constitution VI). T004–T005 (pure domain rule, no UI)
may run while T002 waits for the user.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: the one rule for "where does a source play" (research R2), used by all three stories.

- [X] T004 Write failing tests for `sourcePlaces(sourceId, snapshot)` in
  test/domain/SettingsConfirmTest.kt (new section "Where a source plays"): a route addressed to a
  room → `["Bedroom"]`; to a group → `["Downstairs"]` (the group's name, not its members); two
  routes in different places → both, in the hub's route order; two routes in the same place →
  named once; Paused, Starting and Failed routes count; a Stopped route does not; routes of other
  sources are ignored; no routes → empty. Reuse the file's `base()`, `route(...)` and `withLink()`
  helpers.
- [X] T005 Implement `internal fun sourcePlaces(sourceId: String, snapshot: HubSnapshot): List<String>`
  in main/domain/SettingsConfirm.kt: `liveRoutes(snapshot)` filtered by `inputId == sourceId`,
  mapped to `describeTarget(it.target, rooms, groups, masterMuted = false).title`, `distinct()`.
  Rewrite `removeConfirmation` to use it, with unchanged results (its existing tests stay green).

**Checkpoint**: T004 passes; every existing `removeConfirmation` test is unchanged and green.

---

## Phase 3: User Story 1 - Turning off a source that plays asks first (Priority: P1) 🎯 MVP

**Goal**: flipping off a configured source in use opens the turn-off dialog; nothing is sent until
"Turn off".

**Independent Test**: with a source playing in a room and on a group, flip its switch off: the
dialog names both; "Keep playing" sends nothing; "Turn off" sends `enabled=false` once (quickstart
§2 rows 2–5).

### Tests for User Story 1 (write first, see them fail)

- [X] T006 [P] [US1] In test/domain/SettingsConfirmTest.kt, replace
  `turnOffConfirmation(ItemKey(ItemKind.Source, "radio"), s)` returning null (the "any source
  switch" case) with: a source playing in Living Room →
  `Confirmation.TurnOffSource("Radio Paradise", listOf("Living Room"))`; playing on Downstairs →
  `where = ["Downstairs"]`; in two places → both, once each; only paused → asks; idle → null; only
  a Stopped route → null; the source is off (`enabled = false`) with a live route → null; a source
  id the hub does not list → null. Room and group cases stay as they are.
- [X] T007 [P] [US1] In test/ui/settings/SettingsTextTest.kt add the dialog rows of the
  [copy table](contracts/settings-ui.md#copy-table-additions-for-settingstexttest):
  `confirmTitle(TurnOffSource("Radio Paradise", ["Kitchen"]))` = "Turn off Radio Paradise?";
  `confirmBody` = "Radio Paradise is playing in Kitchen. Turning it off stops playback there.";
  with `["Kitchen", "Downstairs"]` the body reads "… playing in Kitchen and Downstairs. …";
  `confirmLabel` = "Turn off".
- [X] T008 [P] [US1] In test/ui/settings/SettingsViewModelTest.kt replace
  `aSourceTurnedOffWhileInUseDoesNotAskAndSaysItKeepsPlaying` with
  `turningOffAPlayingSourceAsksFirstAndKeepsTheSwitchOn`: `onToggle(Source "radio", false)` with a
  live route opens `ConfirmState` holding `TurnOffSource`, makes no `setSourceEnabled` call and the
  switch still shows on; `onConfirmCancel()` closes it with no call; flipping again then
  `onConfirm()` makes exactly one `setSourceEnabled(radio, false)` call. Add
  `anOpenSourceDialogStaysATurnOffDialogAcrossRefreshes`: with the dialog open, a refresh that adds
  a second place updates `where`, the confirmation stays `TurnOffSource` (never `Remove`), and a
  refresh where the route is gone keeps the last text.

### Implementation for User Story 1

- [X] T009 [US1] In main/domain/SettingsConfirm.kt add
  `data class TurnOffSource(val source: String, val where: List<String>) : Confirmation` and the
  `ItemKind.Source` branch of `turnOffConfirmation`: the source is listed, `enabled` is true and
  `sourcePlaces` is not empty → `TurnOffSource(name, places)`, else null. Update the KDoc that says
  a source never asks. Makes T006 pass.
- [X] T010 [P] [US1] In main/ui/settings/SettingsText.kt add the `TurnOffSource` cases to
  `confirmTitle` ("Turn off <source>?") and `confirmBody` ("<source> is playing in
  ${joinNames(where)}. Turning it off stops playback there."); `confirmLabel` returns "Turn off" for
  it (only `Remove` returns `REMOVE_LABEL`). Makes T007 pass.
- [X] T011 [US1] In main/ui/settings/SettingsViewModel.kt make `confirmationFor` choose the rule by
  the open confirmation's type (research R5): `Remove` → `removeConfirmation`, otherwise
  `turnOffConfirmation(open.key, snapshot)`; its signature becomes
  `private fun confirmationFor(open: ConfirmState, snapshot: HubSnapshot): Confirmation?`. `onToggle` needs no new branch: it already asks `turnOffConfirmation`.
  Makes T008 pass.

**Checkpoint**: US1 works on its own; all tests green.

---

## Phase 4: User Story 2 - Idle sources stay one tap, and no false message (Priority: P1)

**Goal**: an idle source turns off at once with no message; the "keeps playing" message and its
plumbing are gone (research R6).

**Independent Test**: flip off an idle source: no dialog, no message; flip off a playing one and
confirm: no "keeps playing" message (quickstart §2 rows 3, 6, 7).

### Tests for User Story 2 (write first, see them fail)

What fails here: T012 guards behaviour that already holds and passes before T016 (a regression
test, written now so T016 cannot break it); T013 and T014 remove assertions about the code T016
deletes. The failing test for T016 is T015: it calls `setEnabled` without the `keepsPlaying`
argument, so the test source set does not compile until T016. Write T015 last, after T012–T014
have been run, so the compile break hides no other result.

- [X] T012 [P] [US2] Regression tests in test/ui/settings/SettingsViewModelTest.kt (pass before and
  after T016): `turningOffAnIdleSourceSendsAtOnceWithoutAMessage`: no live route →
  `onToggle(..., false)` opens no dialog, makes one `setSourceEnabled` call, and after success
  `state.message` is null and `AppMessages` received nothing; and `turningASourceOnNeverAsks`.
- [X] T013 [P] [US2] In test/ui/settings/SettingsTextTest.kt split
  `theKeepsPlayingMessageAndTheEmptyText`: drop the `keepsPlayingMessage` assertion, keep the
  `CONFIGURED_EMPTY` one (rename the test `theConfiguredEmptyText`).
- [X] T014 [P] [US2] In test/domain/SettingsConfirmTest.kt delete the `keepsPlaying` section
  (`keepsPlayingWhenALiveRouteUsesTheSource` and its idle/stopped counterpart); `sourcePlaces`
  (T004) covers the same cases.
- [X] T015 [US2] Last, after T012–T014 were run: in test/ui/session/SettingsActionsTest.kt replace
  `aSourceStillInUseSaysItKeepsPlayingOnSuccessOnly` with
  `turningASourceOffReportsNothingOnSuccessAndTheFailureOtherwise`: success → no message; a 500 →
  "Couldn't turn Radio 2 off". Change every `setEnabled(key, name, value, keepsPlaying = …)` and
  positional four-argument `setEnabled(…)` call in this file to the three-argument form. Run the
  tests: compilation fails, which is the failing test for T016.

### Implementation for User Story 2

- [X] T016 [US2] Remove the "keeps playing" plumbing: delete `keepsPlaying()` from
  main/domain/SettingsConfirm.kt, `keepsPlayingMessage` from main/ui/settings/SettingsText.kt, the
  `keepsPlaying` parameter and its `report(...)` line from `SettingsActions.setEnabled` in
  main/ui/session/SettingsActions.kt (update its KDoc), and the `keepsPlaying = …` arguments and
  import in main/ui/settings/SettingsViewModel.kt (`onToggle`, `onConfirm`). Makes T015 compile and
  pass; T012 stays green.

**Checkpoint**: US1 + US2 = the full fix of the user-visible fault; build and all tests green.

---

## Phase 5: User Story 3 - Seeing which sources will ask (Priority: P2)

**Goal**: configured source rows read "Playing in <where>" in amber while their switch would ask.

**Independent Test**: one source playing in Kitchen, another idle: the first reads "Playing in
Kitchen" in amber, the second its kind line (quickstart §2 row 1).

### Tests for User Story 3 (write first, see them fail)

- [X] T017 [P] [US3] In test/domain/SettingsBuilderTest.kt: `ConfiguredSourceRow` gains
  `playingIn` (update the four existing `ConfiguredSourceRow(...)` expectations with
  `playingIn = emptyList()`); add cases: playing in a room → `["Living Room"]`; on a group →
  `["Downstairs"]`; two places → both once; only paused → non-empty; idle or Stopped-only → empty;
  the source off with a live route → empty ("Empty when nothing plays it or the source is off",
  data-model.md). Two snapshots in turn (playing, then the route stopped) give a non-empty then
  an empty `playingIn`. Runtime rows unchanged.
- [X] T018 [P] [US3] In test/ui/settings/SettingsTextTest.kt extend `configuredLines`:
  `playingIn = ["Kitchen"]` → "Playing in Kitchen"; `["Kitchen", "Downstairs"]` → "Playing in
  Kitchen and Downstairs"; empty → the existing kind lines unchanged. Add a test for the colour
  predicate (`configuredLinePlaying(row)` true only when `playingIn` is not empty).

### Implementation for User Story 3

- [X] T019 [US3] In main/domain/SettingsBuilder.kt add `val playingIn: List<String>` to
  `ConfiguredSourceRow` and fill it with `if (it.enabled) sourcePlaces(it.id, snapshot) else
  emptyList()`. Makes T017 pass.
- [X] T020 [US3] In main/ui/settings/SettingsText.kt make `configuredLine(row)` return
  "Playing in ${joinNames(row.playingIn)}" when `playingIn` is not empty, else the current kind
  line, and add `fun configuredLinePlaying(row): Boolean`. In main/ui/settings/SettingRows.kt
  (`ConfiguredSourceSettingRow`) colour the line `colors.accent` when `configuredLinePlaying(row)`,
  else `colors.textMuted`; keep one line with an ellipsis; the playing line uses `type.body13`, the idle
  kind line keeps `type.label12` (design canvas v19, research R4). Makes T018 pass (depends on T003, T019).

**Checkpoint**: all three stories work; build and all tests green.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [X] T021 Run the local checks and read each exit code (`cmd > log; echo $?`):
  `./gradlew :androidApp:assembleDebug`, `:composeApp:testAndroidHostTest`, `:composeApp:allTests`,
  `:composeApp:check`. Record the commands, date and test counts in
  specs/005-source-off-confirm/verification.md (create it in 004's format: build under test,
  device, status legend, one row per quickstart §2 check, "Issues found").
- [X] T022 [P] Add a `## [0.5.0-alpha]` entry to CHANGELOG.md (spec folder and PR link): Changed —
  turning off a configured source that plays now asks first and names where it stops; playing
  sources read "Playing in …"; Removed — the "keeps playing" message.
- [X] T023 **Local**: run quickstart §2 rows 1–8 on the POCO against the real hub, following
  AGENTS.md "On-device verification" (volumes noted and set to 1 first; test routes stopped before
  volumes are restored; the source turned back on at the end). Record evidence in
  specs/005-source-off-confirm/verification.md.
- [ ] T024 [P] When the feature ships: delete docs/backlog/source-off-stops-playback.md and its row
  in docs/backlog/INDEX.md (AGENTS.md "Backlog").

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (T001–T003)**: T001 first. T002 → T003 is a local step with the user and blocks every
  story task (T006 onwards).
- **Foundational (T004–T005)**: after T001; blocks every story (all use `sourcePlaces`).
- **US1 (T006–T011)**: after T003 and T005.
- **US2 (T012–T016)**: after US1 (T011), because `onConfirm` and `onToggle` are touched by both and
  US1's tests use the dialog flow; T016 also removes what US1 leaves unused.
- **US3 (T017–T020)**: after T003 and T005; independent of US1/US2 except T020 shares SettingsText.kt with
  T010 and T016 (do them one after another).
- **Polish (T021–T024)**: after the stories; T023 needs an installed build.

### Within Each User Story

- Tests first, seen failing; then the domain change, then text, then view model / UI.

### Parallel Opportunities

- T006, T007, T008 (three different test files).
- T012–T014 (three different test files); T015 after them.
- T017, T018.
- T010 alongside T009 (different files) once their tests are written.
- T022 and T024 alongside T021.

## Parallel Example: User Story 1

```text
# Tests together:
Task: "TurnOffSource cases in composeApp/src/commonTest/kotlin/sonora/multiroom/mobile/domain/SettingsConfirmTest.kt"
Task: "Dialog copy rows in composeApp/src/commonTest/kotlin/sonora/multiroom/mobile/ui/settings/SettingsTextTest.kt"
Task: "Dialog flow in composeApp/src/commonTest/kotlin/sonora/multiroom/mobile/ui/settings/SettingsViewModelTest.kt"

# Then:
Task: "TurnOffSource in composeApp/src/commonMain/kotlin/sonora/multiroom/mobile/domain/SettingsConfirm.kt"
Task: "Dialog copy in composeApp/src/commonMain/kotlin/sonora/multiroom/mobile/ui/settings/SettingsText.kt"
```

---

## Implementation Strategy

### MVP First

1. T001, then T004–T005 (`sourcePlaces`).
2. US1 (dialog) and US2 (no false message) together are the fix of the user-visible fault.
3. **Stop and validate**: build, all tests, quickstart §2 rows 2–8.

### Incremental Delivery

1. Setup + Foundational.
2. US1 + US2 → the fault is fixed (MVP).
3. US3 → "Playing in …" on source rows, once the design copy (T003) is in.
4. Polish → changelog, verification, backlog clean-up.

---

## Notes

- [P] = different files, no dependency on unfinished tasks.
- Verify each test fails before implementing (Constitution IV).
- The app never decides that a playback stopped: after a confirmed turn-off, Rooms and Now Playing
  follow the hub at their next refresh.
- If a task needs a decision the spec does not cover, stop and ask.
