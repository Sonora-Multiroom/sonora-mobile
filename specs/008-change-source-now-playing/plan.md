# Implementation Plan: Change What Plays from Now Playing

**Branch**: `008-change-source-now-playing` | **Date**: 2026-11-06 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/008-change-source-now-playing/spec.md`

## Summary

Now Playing gains a round "Change source…" button that opens Start Playback with the playback's
addressed room or group as a fixed target (no picker, title "Change source"). The old playback
keeps playing while the user browses; Play sends the usual start, which the hub turns into a
replacement, and Now Playing for the new playback then replaces both Start Playback and the old
Now Playing. Wherever Start Playback was opened from, picking the source that already plays on the
selected target now disables Play instead of provoking a hub refusal.

Technically:
- **Navigation**: `Destination.StartPlayback` gets `fixedTarget` / `fixedName`, saved as
  `change:room:<id>` / `change:group:<id>`; `AppBackStack.showStarted` replaces the top two
  entries when a fixed Start Playback sits on a Now Playing
  ([R1](research.md#r1-how-start-playback-learns-its-fixed-target),
  [R2](research.md#r2-back-stack-after-a-start-from-now-playing-fr-008)).
- **Start Playback**: the same view model and screen in a fixed mode; one pure
  `FixedTarget.of(snapshot, target, knownName)` decides the card and whether the target can be
  used ([R3](research.md#r3-start-playbacks-view-model-in-fixed-mode-fr-003-fr-004-fr-005-fr-011),
  [R5](research.md#r5-the-fixed-target-cards-wording-fr-012)).
- **"Already playing"**: `playEnabled` also requires the consequence line not to be
  `AlreadyPlaying` ([R4](research.md#r4-already-playing-disables-play-everywhere-fr-007-user-story-3)).
- **Now Playing**: the button, a fourth-column compact layout, and "gone" decided only while the
  screen is visible, so a covered Now Playing does not post a false "ended"
  ([R6](research.md#r6-the-old-now-playing-must-not-announce-ended-while-covered-fr-013),
  [R7](research.md#r7-now-playings-action-row-fr-001-fr-002-fr-012)).
- **No** hub call, repository, generated-client or dependency change.

## Technical Context

**Language/Version**: Kotlin 2.4.20 (KMP), JDK 17+, Gradle 9.8.0, AGP 9.4.1 (unchanged)

**Primary Dependencies**: unchanged (Compose Multiplatform 1.12.1, Navigation 3, Lifecycle
ViewModel, Ktor, kotlinx.serialization, coroutines, DataStore, OpenAPI Generator)

**Storage**: none new. The fixed target's last known name lives in the Start Playback view
model's `SavedStateHandle`; the target itself in the saved back stack.

**Testing**: kotlin-test in `commonTest` (domain functions, view models with the existing fake
session/starter, `AppBackStack`). Layout, TalkBack and the real replacement are checked on the
device (quickstart §2).

**Target Platform**: Android (minSdk 26). iOS targets declared, not built.

**Project Type**: mobile app (KMP: `composeApp` + `androidApp` + `iosApp` stub)

**Performance Goals**: no gap in playback from the tap on the action to Play (SC-005; nothing is
sent); Now Playing of the new playback within one refresh of the start's answer, as today.

**Constraints**: one poll loop app-wide (the covered Now Playing must not poll or act); the
four-button row fits a 360 dp phone; nothing Android-only in `commonMain`.

**Scale/Scope**: 2 new production files (`domain/FixedTarget.kt`,
`ui/startplayback/FixedTargetCard.kt`), 13 edited production files (`Destinations.kt`,
`AppNavigation.kt`, `AppGraph.kt`, `NowPlayingBuilder.kt`, `StartPlaybackBuilder.kt`,
`StartPlaybackViewModel.kt`, `StartPlaybackUiState.kt`, `StartPlaybackScreen.kt`,
`StartPlaybackText.kt`, `NowPlayingScreen.kt`, `NowPlayingText.kt` (`actionRowLayout`),
`NowPlayingViewModel.kt`, `Icons.kt`), 1 new and 8 extended test files, `gradle.properties`,
CHANGELOG.md, AGENTS.md, the backlog and the full app test.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | How |
|---|---|---|
| I. API Contract Fidelity | ✅ | No new call or field: the start is Start Playback's existing `POST /api/v2/routes` / `POST /api/v2/play`, the target comes from `RouteResponse.targetId/targetType` already mapped. Screens keep using domain types only. No hub change; the "hub stops first on a failed start" order stays a hub-side observation |
| II. Truthful UI | ✅ | Every outcome is confirmed by hub state: the new Now Playing follows the returned route, the "will stop" line follows each refresh. Play is no longer offered for a start the hub would refuse (FR-007). A paused group playback reads "Paused", not "Playing" (R5). The target note says plainly why a start cannot be sent (FR-005). Pause and Move rules unchanged |
| III. Shared-First | ✅ | All in `commonMain` / `commonTest`; no `expect`/`actual` |
| IV. Test-First | ✅ | `FixedTarget.of`, `GroupPlaying.status`, `changeSourceVisible`, the `AlreadyPlaying` gate, fixed-mode view model, `showStarted`, destination encoding, "gone while hidden" and `actionRowLayout` get failing tests first ([R9](research.md#r9-testing)). Build and all tests gate the change |
| V. Resilient LAN Networking | ✅ | Stale state disables the new button and Play as it does the other controls; recovery unchanged. A covered Now Playing no longer reacts to snapshots (R6), so an outage while Start Playback is open cannot pop it |
| VI. Design Fidelity & Accessibility | ✅ | The canvas was updated first (commit `adbe26e`, `NowPlaying*.dc.html`, `StartPlaybackFixed.dc.html`); colours and type from tokens. Buttons 64/76 dp, content description "Change source", disabled controls announced disabled; "Move to room…" label and the "will stop" notice kept |
| VII. Minimal Dependencies | ✅ | None added |
| Full app test gate | ✅ planned | The final task updates `docs/testing/full-app-test.md`: rows for quickstart §2, row S6 ("already playing") changed from "Play opens the same playback" to Play disabled, header to 008 / 0.8.0-alpha / API 0.1.33 |

No violations, so Complexity Tracking stays empty.

**Post-design re-check (after Phase 1)**: unchanged, all ✅. The design adds one navigation field,
one domain function and view-state fields ([data-model.md](data-model.md)); the UI contract reuses
existing styles and wording ([contracts/ui-behaviour.md](contracts/ui-behaviour.md)).

**Decisions made in this plan that the spec does not spell out** (flag for review before
`/speckit-tasks`): the status-line wording of the fixed card and the FR-005 notes (R5); the
three-button gap of 24 dp taken from the updated canvas (R7). The two earlier open points, the
silent end under Start Playback and the unknown target type, are now FR-013 and FR-002 in the spec
(commit `7c6a657`) and are planned as R6 and R7.

## Project Structure

### Documentation (this feature)

```text
specs/008-change-source-now-playing/
├── plan.md              # This file
├── research.md          # Phase 0
├── data-model.md        # Phase 1
├── quickstart.md        # Phase 1
├── contracts/
│   └── ui-behaviour.md  # action row, fixed Start Playback, "already playing", accessibility
├── checklists/requirements.md
└── tasks.md             # Phase 2 (/speckit-tasks)
```

### Source Code (repository root)

```text
gradle.properties                                   # 0.8.0-alpha / 8
composeApp/src/commonMain/kotlin/sonora/multiroom/mobile/
├── AppGraph.kt                                     # startPlaybackViewModel(targetId, fixedTarget, fixedName, …)
├── domain/
│   ├── FixedTarget.kt                              # new: FixedTargetCard, Unusable, FixedTarget.of
│   ├── NowPlayingBuilder.kt                        # changeSourceVisible (hidden for an unknown target type)
│   └── StartPlaybackBuilder.kt                     # GroupPlaying.status, Removed; status per target shared
└── ui/
    ├── nav/
    │   ├── Destinations.kt                         # StartPlayback.fixedTarget/fixedName, change: encoding,
    │   │                                           #   replaceTopTwo, showStarted
    │   └── AppNavigation.kt                        # onChangeSource → push; onStarted → showStarted
    ├── nowplaying/
    │   ├── NowPlayingScreen.kt                     # the button, compact four-column row
    │   ├── NowPlayingText.kt                       # actionRowLayout
    │   └── NowPlayingViewModel.kt                  # visible flag; onGone only while visible;
    │                                               #   onVisible requests a refresh
    ├── startplayback/
    │   ├── StartPlaybackViewModel.kt               # fixed mode, AlreadyPlaying gate
    │   ├── StartPlaybackUiState.kt                 # fixed, changeSource
    │   ├── StartPlaybackScreen.kt                  # title, fixed card, note above Play
    │   ├── FixedTargetCard.kt                      # new: the fixed target card, beside TargetTile.kt
    │   └── StartPlaybackText.kt                    # fixedTargetStatusText, fixedTargetNoteText
    └── theme/Icons.kt                              # Swap
composeApp/src/commonTest/kotlin/sonora/multiroom/mobile/
├── domain/FixedTargetTest.kt                       # new
├── domain/StartPlaybackBuilderTest.kt
├── ui/nav/AppBackStackTest.kt
├── domain/StartConsequenceTest.kt, NowPlayingBuilderTest.kt
├── ui/nowplaying/NowPlayingViewModelTest.kt, NowPlayingTextTest.kt
└── ui/startplayback/StartPlaybackViewModelTest.kt, StartPlaybackTextTest.kt
AGENTS.md                                           # "Agreed details": the "Change source…" label and fixed target
docs/backlog/change-source-from-now-playing.md (delete on ship) + INDEX row
docs/testing/full-app-test.md
```

**Structure Decision**: the existing KMP layout. `FixedTarget.kt` sits in `domain/` beside
`StartPlaybackBuilder.kt`, whose per-target status it shares; wording stays in
`StartPlaybackText.kt` with the rest of Start Playback's copy.

## Complexity Tracking

None.
