# Implementation Plan: Rooms a Playback Is Really On, and Plain Refusal Messages

**Branch**: `006-route-outputs-refusals` | **Date**: 2026-10-18 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/006-route-outputs-refusals/spec.md`

## Summary

Since API 0.1.22, the hub lists the rooms each playback really plays on. The app still counts
every member of a group, so a room that was turned off still shows as playing on Rooms, Now
Playing and the Move sheet. The hub's refusals for things that are turned off, and the brief "rooms
are changing" refusal of a move (API 0.1.25), all read "Couldn't start playback" / "Couldn't move
…". This feature:
- shows each playback only in the rooms the hub lists, on every screen, falling back to today's
  rule when the list is missing;
- says what is turned off when the hub refuses for that reason, and refreshes;
- retries a move refused as "rooms changing" once, after about a second.

Technically:
- **Contract and repository**: no new call. The domain `Route` gains `outputs: List<String>?`,
  mapped from `RouteResponse.outputs` ([R1](research.md#r1-contract-and-the-hub-behaviour),
  [R2](research.md#r2-routeoutputs-in-the-domain)).
- **Domain**: one rule, `occupiedRooms(route, …)` in `PlaybackRules.kt`, decides which rooms a
  playback occupies ([R3](research.md#r3-one-occupancy-rule)). `occupancy`, `routesByRoom` and a
  new `describeRoute` use it. The Rooms, Now Playing, Move, Start Playback and Settings builders
  read it through them ([R4](research.md#r4-describing-a-route-not-just-a-target)). The Move sheet
  gets one new note, "Nothing stops" ([R5](research.md#r5-move-sheet-rows)). Group volume follows
  with no new code ([R6](research.md#r6-group-volume)).
- **Copy**: `Messages.kt` maps the four "turned off" reasons for starts and moves, plus the
  "try again" text ([R7](research.md#r7-refusal-messages)). `PlaybackStarter` refreshes after a
  turned-off refusal.
- **View model**: `NowPlayingViewModel.onConfirmMove` retries `ROUTE_CHANGING_OUTPUTS` once after
  1 s ([R8](research.md#r8-one-quiet-retry-of-a-move)).
- **Design**: one copy addition, recorded in the spec's clarifications, which is what Constitution VI
  asks of a departure. The canvas is updated alongside the code, and the sheet is checked against
  it before the pull request ([R9](research.md#r9-design-documentation-and-version)).

## Technical Context

**Language/Version**: Kotlin 2.4.20 (KMP), JDK 17+, Gradle 9.8.0, AGP 9.4.1 (unchanged)

**Primary Dependencies**: unchanged from 005 (Compose Multiplatform, Ktor 3.6.0,
kotlinx.serialization, coroutines 1.11.0, DataStore, kotlinx-datetime, OpenAPI Generator). No
additions.

**Storage**: none new.

**Testing**: kotlin-test, kotlinx-coroutines-test (virtual time for the retry), `FakeHub`, and
Ktor `MockEngine` for the `outputs` mapping, all in `commonTest`.

**Target Platform**: Android (minSdk 26). The iOS targets are declared but not built.

**Project Type**: mobile app (KMP: `composeApp` + `androidApp` + `iosApp` stub)

**Performance Goals**: none new. The occupancy rule runs on the snapshot already in memory, once
per route per build, over a handful of rooms. The retry adds about 1 s only in the rare refused
case.

**Constraints**:
- One poll loop app-wide.
- Never `PUT /groups/{id}/volume`, and never a `joinMode`.
- Nothing Android-only in `commonMain`.
- Hub wording never reaches the screen.
- Routes without `outputs` must behave exactly as in 005 (SC-005).

**Scale/Scope**: ~4–10 rooms, a few groups. The work touches ~12 production files in `commonMain`
(no new file except possibly a test), about 14 test suites, one design screen, AGENTS.md, the
backlog and the full app test.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | How |
|---|---|---|
| I. API Contract Fidelity | ✅ | `outputs` and `reason` / `outputId` are in the 0.1.25 contract, which is already reconciled (005). They are read through `HubRepository` → `toRoute()` / `rejection()`, so screens see only domain types. The reasons' schema description lags the hub; recorded in the hub's backlog, not invented ([R1](research.md#r1-contract-and-the-hub-behaviour)). The "No rooms in a playback's answer" hub gap is closed, and AGENTS.md is updated on shipping (FR-013) |
| II. Truthful UI | ✅ | This is the point of the feature: no room is shown playing, offered a pill or named as stopping unless the hub lists it. One rule decides it for every screen ([R3](research.md#r3-one-occupancy-rule)). Pause/Move visibility rules are untouched. Outcomes stay confirmed by the next refresh, which a turned-off refusal now requests at once |
| III. Shared-First | ✅ | All changes are in `commonMain` / `commonTest`. No platform source-set changes. `delay` is from kotlinx.coroutines |
| IV. Test-First | ✅ | The occupancy rule, every builder's 2026-10-17 results, the copy tables and the retry get failing tests first ([quickstart](quickstart.md) §1, FR-011, FR-012). The `outputs` mapping is tested against `MockEngine` |
| V. Resilient LAN Networking | ✅ | Unknown reasons keep today's messages. A missing or unknown-only list falls back. An unreachable hub during the retry takes the existing path. The retry happens once, with no loop, so it cannot hang |
| VI. Design Fidelity & Accessibility | ✅ | Cards, lists and pills keep their look (FR-004). The new note "Nothing stops" is recorded in the spec (2026-10-18), which meets the principle for a departure; the canvas and `TransferMemberOff.dc.html` are updated alongside the code, and the sheet is checked against them before the pull request. It uses the existing note style and colour (textMuted, contrast already checked) |
| VII. Minimal Dependencies | ✅ | None added |
| Full app test gate | ✅ planned | The final task updates `docs/testing/full-app-test.md`: rows for quickstart §2, N6's notes, and the header bumped to 006 / 0.6.0-alpha |

No violations, so Complexity Tracking stays empty.

**Post-design re-check (after Phase 1)**: unchanged, all ✅. The data model adds one domain field,
two internal functions, one note case and one failure case. The UI contract reuses existing
components and tokens.

## Project Structure

### Documentation (this feature)

```text
specs/006-route-outputs-refusals/
├── plan.md              # This file
├── research.md          # Phase 0
├── data-model.md        # Phase 1
├── quickstart.md        # Phase 1
├── contracts/
│   └── ui-behaviour.md  # what each screen shows; copy tables; retry table
├── checklists/requirements.md
└── tasks.md             # Phase 2 (/speckit-tasks)
```

### Source Code (repository root)

```text
gradle.properties                                   # 0.6.0-alpha / 6
design/screens/TransferMemberOff.dc.html            # new: Move sheet with a member off, "Nothing stops"
composeApp/src/commonMain/kotlin/sonora/multiroom/mobile/
├── data/
│   ├── ApiMapping.kt                               # toRoute(): outputs
│   └── HubRepository.kt                            # RefusalReason constants; transferRoute doc (same id since 0.1.22)
├── domain/
│   ├── Models.kt                                   # Route.outputs
│   ├── PlaybackRules.kt                            # + occupiedRooms, + describeRoute; occupancy/routesByRoom use them
│   ├── RoomsBuilder.kt                             # describeRoute
│   ├── NowPlayingBuilder.kt                        # describeRoute; volumeSection from occupied rooms
│   ├── MoveDestinations.kt                         # occupied rooms; + NothingStops; no duplicate rows
│   └── SettingsConfirm.kt                          # TurnOffGroup.members from occupied rooms
├── ui/
│   ├── Messages.kt                                 # TurnedOff, StartFailure.TurnedOff + needsRefresh, Move reasons
│   ├── nowplaying/DestinationText.kt               # "Nothing stops"
│   ├── nowplaying/NowPlayingViewModel.kt           # one retry; roomName lookup for messages
│   └── session/PlaybackStarter.kt                  # refresh on needsRefresh
composeApp/src/commonTest/kotlin/sonora/multiroom/mobile/
├── data/KtorHubRepositorySnapshotTest.kt
├── domain/OccupiedRoomsTest.kt (new), RoutesByRoomTest.kt, RoomsBuilderTest.kt,
│   NowPlayingBuilderTest.kt, MoveDestinationsTest.kt, StartConsequenceTest.kt,
│   SettingsBuilderTest.kt, SettingsConfirmTest.kt
└── ui/MessagesTest.kt, nowplaying/DestinationTextTest.kt, nowplaying/NowPlayingViewModelTest.kt,
    session/PlaybackStarterTest.kt, rooms/RoomsViewModelControlsTest.kt
AGENTS.md, docs/backlog/ (delete item + INDEX row), docs/testing/full-app-test.md
```

`StartConsequence.kt`, `StartPlaybackBuilder.kt` and `SettingsBuilder.kt` need no code change:
they read `routesByRoom` / `occupancy`, which become list-based. Their tests prove it.

**Structure Decision**: the existing KMP layout. The feature changes the shared rules in place and
adds no production file.

## Complexity Tracking

None.
