# Implementation Plan: Now Playing and "Move to room…"

**Branch**: `002-now-playing-move-to-room` | **Date**: 2026-10-03 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/002-now-playing-move-to-room/spec.md`

## Summary

Replace the Now Playing placeholder with a full screen for one playback, and add the "Move
playback" bottom sheet:
- **Screen**: source, kind and address, status, target, Stop for every route, Pause/Resume when
  pauseable, the live-stream line, a group pill with per-member pills, and room/group mute.
- **Sheet**: lists rooms, "<Room> only" options and groups, each with a note that says what moving
  there will stop. The move uses `POST /routes/{id}/transfer`, and the screen follows the new route
  id the hub returns.

Technically:
- The poll loop and connection state move out of `RoomsViewModel` into one app-wide `HubSession`
  shared by both screens.
- The volume drag/throttle logic is extracted into a reusable controller keyed by room.
- Now Playing content and move destinations are pure, test-first functions.
- Three repository methods are added (room mute, group mute, transfer).
- Two libraries from the existing JetBrains lifecycle family are added: the Nav3 view-model
  decorator, so each Now Playing entry gets its own view model, and `lifecycle-viewmodel-savedstate`,
  so the followed route id survives process death.

## Technical Context

**Language/Version**: Kotlin 2.4.20 (KMP), JDK 17+, Gradle 9.8.0 (unchanged from 001)

**Primary Dependencies**: as 001 ([research R1](../001-rooms-screen-foundation/research.md)). New:
`org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-navigation3` and `lifecycle-viewmodel-savedstate`,
both 2.11.0 ([research R2](research.md#r2-a-view-model-per-now-playing-entry)).
Uses the existing `compose-material3` 1.9.0 for `ModalBottomSheet` ([R11](research.md#r11-bottom-sheet)).

**Storage**: none new (hub address only, from 001).

**Testing**: kotlin-test, kotlinx-coroutines-test (virtual time), Ktor `MockEngine`, in
`commonTest`.

**Target Platform**: Android (minSdk 26). iOS targets declared, not built.

**Project Type**: mobile app (KMP: `composeApp` + `androidApp` + `iosApp` stub)

**Performance Goals**: a hub change is visible on Now Playing in ≤ 3 s, and the app returns to
Rooms ≤ 3 s after the playback ends elsewhere (SC-004). A moved playback is shown on its new target
≤ 3 s after confirm (SC-002). Volume drags stay at ≤ 4 requests/s per pill, as in 001.

**Constraints**: one poll loop app-wide, never overlapping. Zero requests in the background. Never
`PUT /groups/{id}/volume`. Nothing Android-only in `commonMain`. Transfer only when `transferable`
and Playing.

**Scale/Scope**: one home hub, ~2–20 rooms, a few groups and routes. 1 new screen + 1 sheet.
Refactor of the Rooms view model onto a shared session.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | How |
|---|---|---|
| I. API Contract Fidelity | ✅ | Only `/api/v2` calls: `PUT outputs/{id}/mute`, `PUT groups/{id}/mute`, `POST routes/{id}/transfer`, all in `openapi.json` 0.1.20, through `HubRepository` ([contract](contracts/hub-repository.md)). The new route id comes from the documented transfer response, not a guess. Group volume stays absent. No new hub gap. The unknown "move while paused" behaviour is handled by hiding the action (FR-012), not by invention |
| II. Truthful UI | ✅ | No progress, position or metadata. The decorative panel depends on kind only. Pause only when `pauseable`, Move only when `transferable` (+ Playing). Live decided by the existing `isLiveStream`. Address detail derived in one function (`addressDetail`, R7). Destination notes decided in one builder + one formatter (R8, including the turned-off-member and no-playable-member group rules). Mute shows the hub's state with no optimistic flip (R9). The local value wins while dragging via the shared controller (R5) |
| III. Shared-First | ✅ | All new code in `commonMain`. Both new dependencies are JetBrains multiplatform artifacts. No platform source set changes |
| IV. Test-First | ✅ | Builders (`NowPlayingBuilder`, `MoveDestinations`, `addressDetail`, `joinNames`, note text), `HubSession`, `VolumeDragController`, view models and repository additions get their tests first ([quickstart](quickstart.md) §1). The 001 suites migrate without dropping scenarios |
| V. Resilient LAN | ✅ | Same timeouts and loop, now in one place. Stale state disables every Now Playing control and the sheet's confirm. Transfer errors (400/404/422, IO) map to plain copy. Unknown enum values → Unknown (001 mapping reused) |
| VI. Design Fidelity & A11y | ✅ (gate) | Layout per `NowPlaying.dc.html` / `Transfer.dc.html` ([UI contract](contracts/now-playing-ui.md)). Departures already called out in the spec (no preselection, non-stream panel colours). The **Groups section must be added to the canvas first** (FR-026, R12): a gate task before the sheet UI. Labels, ≥ 44 dp and a radio-group sheet per FR-027. Dimmed disabled/unselectable text is exempt from 4.5:1 (constitution 1.2.1) |
| VII. Minimal Dependencies | ✅ | Two new artifacts from the lifecycle family already in use (same version 2.11.0), justified in R2 (per-entry view-model scope; `SavedStateHandle` for R3), added to `libs.versions.toml`. `ModalBottomSheet` comes from the existing material3 |

**Post-design re-check (after Phase 1)**: still passing.
- The data model keeps generated types in `data/`.
- The repository contract adds only spec'd v2 calls and omits group volume.
- The UI contract keeps the pill, the "Move to room…" label and "will stop" notes.
- The two Move-sheet rules in R8 (an unavailable "<Room> only" member is unselectable, and a group
  with no known members reads "No rooms") were confirmed by the user on 2026-10-03 and are now in
  spec FR-020a–FR-022.
- The states the design does not draw (Pause/Resume button, non-Playing chips, "All rooms are
  muted", loading and stale states, the "Move" label with nothing selected) are called out in the
  spec Assumptions (Principle VI).
- Every "refresh started after X" check captures `session.startedSeq`, never the state's
  `refreshSeq` (R1), so a refresh in flight at X cannot confirm a move or a volume.

## Project Structure

### Documentation (this feature)

```text
specs/002-now-playing-move-to-room/
├── spec.md
├── plan.md              # this file
├── research.md          # Phase 0
├── data-model.md        # Phase 1
├── quickstart.md        # Phase 1
├── contracts/
│   ├── hub-repository.md
│   └── now-playing-ui.md
└── tasks.md             # /speckit-tasks (not created here)
```

### Source Code (changes against 001)

```text
gradle.properties                          # versionName 0.2.0-alpha, versionCode 2 (first commit)
gradle/libs.versions.toml                  # + lifecycle-viewmodel-navigation3, lifecycle-viewmodel-savedstate (version.ref lifecycle)
composeApp/build.gradle.kts                # + both in commonMain
design/screens/Transfer.dc.html            # + Groups section (gate, synced from canvas, R12)

composeApp/src/commonMain/kotlin/sonora/multiroom/mobile/
├── AppGraph.kt                            # + HubSession, AppMessages (app scope); view-model factories take them
├── domain/
│   ├── SourceKind.kt                      # + addressDetail(), kindLabel()
│   ├── Names.kt                           # joinNames
│   ├── RoomsBuilder.kt                    # shared helpers extracted (status/target/occupancy), behaviour unchanged
│   ├── NowPlayingBuilder.kt               # NowPlayingContent, TargetLine, VolumeSection, PillModel, MuteModel
│   └── MoveDestinations.kt                # MoveSheetContent, MoveDestination, MoveDestinationNote
├── data/
│   ├── HubRepository.kt                   # + setRoomMute, setGroupMute, transferRoute
│   └── KtorHubRepository.kt               # + the three calls (generated OutputsApi/GroupsApi/RoutesApi)
└── ui/
    ├── Messages.kt                        # + UserAction.Mute, UserAction.Move
    ├── session/
    │   ├── HubSession.kt                  # poll loop + connection + snapshot, startedSeq, acquire/release (R1)
    │   ├── Connection.kt                  # moved from ui/rooms/RoomsUiState.kt
    │   ├── AppMessages.kt                 # cross-screen one-shot messages (R4)
    │   └── VolumeDragController.kt        # extracted from RoomsViewModel, per-room pending (R5)
    ├── nav/AppNavigation.kt               # view-model decorator, NowPlaying entry, exit handling
    ├── nav/Destinations.kt                # selectTab() keeps the Rooms root (R2)
    ├── rooms/
    │   ├── RoomsViewModel.kt              # onto HubSession + VolumeDragController; collects AppMessages
    │   └── RoomsScreen.kt                 # acquire/release instead of start/stopPolling
    └── nowplaying/
        ├── NowPlayingViewModel.kt         # follows routeId, actions, sheet state, exit
        ├── NowPlayingUiState.kt
        ├── NowPlayingScreen.kt            # layout per contracts/now-playing-ui.md
        ├── KindPanel.kt                   # decorative panel + live badge
        ├── VolumeSection.kt               # main pill + mute + member grid (reuses VolumePill)
        ├── MoveSheet.kt                   # ModalBottomSheet content
        └── DestinationText.kt             # destinationNoteText()

composeApp/src/commonTest/kotlin/sonora/multiroom/mobile/
├── domain/   AddressDetailTest, NamesTest, NowPlayingBuilderTest, MoveDestinationsTest (+ 001 suites)
├── data/     KtorHubRepositoryActionsTest (extended), Fixtures (transfer/mute payloads)
├── ui/session/  HubSessionTest, VolumeDragControllerTest
├── ui/nowplaying/  NowPlayingViewModelTest, DestinationTextTest, FakeHub additions
└── ui/rooms/  existing suites, adapted to HubSession
```

**Structure Decision**: the existing KMP wizard layout from 001. New code goes under
`composeApp/src/commonMain` in the `domain/`, `data/` and `ui/` packages. `ui/session/` holds state
shared across screens.

## Implementation notes for tasks

1. **First commit**: version bump to `0.2.0-alpha` / `2` (AGENTS.md workflow).
2. **Refactor before features**, with green tests at each step:
   - (a) `RoomsBuilder` helper extraction, which changes no behaviour.
   - (b) `HubSession` + `AppMessages`, with Rooms moved onto them. The 001 polling tests move to
     `HubSessionTest`.
   - (c) `VolumeDragController` extracted, with `RoomsViewModelControlsTest` green against it.
3. **Test-first order**: `joinNames` → `addressDetail` → `NowPlayingBuilder` → `MoveDestinations`
   + `destinationNoteText` → repository additions (MockEngine) → `NowPlayingViewModel`. Watch
   each one fail first.
4. **UI**:
   - Build the Now Playing screen (US1, US2) per the [UI contract](contracts/now-playing-ui.md).
   - The **sheet UI waits for the design gate** (R12). If the canvas and `Transfer.dc.html` still
     lack the Groups section, stop and ask.
   - The sheet's domain logic and view-model behaviour do not wait for the gate.
5. Finish with [quickstart](quickstart.md) §1 all green, then hand over for §2 on the device.

## Open points for the user (not blocking the plan)

- None. R12 (Groups section on the canvas) was done on 2026-10-03.

## Complexity Tracking

None. The two new dependencies are justified in research R2 (Constitution VII), and the shared-session
refactor is justified in R1.
