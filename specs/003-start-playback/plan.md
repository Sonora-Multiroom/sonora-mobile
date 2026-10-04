# Implementation Plan: Start Playback

**Branch**: `003-start-playback` | **Date**: 2026-10-04 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/003-start-playback/spec.md`

## Summary

Replace the Start Playback placeholder with a screen that starts a configured source or a pasted
link on a room or group, and says first what will stop:
- **Screen**: a link field, the list of turned-on sources, a "Play in" grid of groups and rooms with
  status lines, and a footer with the consequence line, muted notes and the Play button.
- **Start**: `POST /api/v2/routes` for a source, `POST /api/v2/play` for a link (30 s). On success
  Start Playback is replaced by Now Playing for the returned route.

Technically:
- The contract copy is already at 0.1.21. Join modes are mapped into the domain (`Route.joinMode`,
  `Source.defaultJoinMode`), and admission refusals (`reason`, `outputId`) are kept on
  `HubError.Rejected`.
- A new `routesByRoom()` sees several playbacks per room. Rooms and Now Playing keep their
  one-route view (backlog 004).
- The target list and the consequence line are two pure, test-first domain objects
  (`StartPlaybackBuilder`, `StartConsequence`). Wording is in one text file and error copy stays in
  `ui/Messages.kt`.
- The request runs in an app-scoped `PlaybackStarter`, so it survives Close. Timeouts and 404s
  resolve against a fenced fresh snapshot (FR-016a).
- Now Playing gains a "started after" fence, so it does not announce "ended" before the first
  snapshot that can contain the new playback.
- No new dependencies.

## Technical Context

**Language/Version**: Kotlin 2.4.20 (KMP), JDK 17+, Gradle 9.8.0 (unchanged)

**Primary Dependencies**: unchanged from 002: Compose Multiplatform 1.12.1, material3 1.9.0,
lifecycle 2.11.0 (+ navigation3 / savedstate), navigation3 1.1.2, Ktor 3.6.0,
kotlinx.serialization 1.11.0, OpenAPI Generator 7.14.0 (`kotlin`, `multiplatform`). None added
([R13](research.md#r13-dependencies)).

**Storage**: none new. Screen selections are kept in `SavedStateHandle`.

**Testing**: kotlin-test, kotlinx-coroutines-test (virtual time), Ktor `MockEngine`, `FakeHub`, in
`commonTest`.

**Target Platform**: Android (minSdk 26). iOS targets declared, not built.

**Project Type**: mobile app (KMP: `composeApp` + `androidApp` + `iosApp` stub)

**Performance Goals**:
- A started source shows on Rooms ≤ 3 s after Play (SC-004): one round trip, then an immediate
  refresh.
- Status lines and the warning follow hub changes within one poll (≤ 2.5 s).

**Constraints**:
- One poll loop app-wide, zero requests in the background.
- 3 s timeout for everything except `POST /play` (30 s).
- Never send `joinMode`, `volume` or `displayName`.
- Never `PUT /groups/{id}/volume`.
- Nothing Android-only in `commonMain`.

**Scale/Scope**: one home hub, ~2–20 rooms, a few groups, ~5–30 sources. 1 new screen, 2 new
repository methods, 1 contract reconciliation.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | How |
|---|---|---|
| I. API Contract Fidelity | ✅ | Only documented `/api/v2` calls (`POST /routes`, `POST /play`) through `HubRepository` ([contract](contracts/hub-repository.md)). The **0.1.20 → 0.1.21 change is reconciled first** ([R1](research.md#r1-reconciling-the-contract-0120--0121)): existing calls are unchanged and their suites are re-run, the new fields are mapped, and AGENTS.md is synced (API version, the "one route per output" rule). Admission refusals are keyed on the documented `reason`, not on a guessed status. No new hub gap |
| II. Truthful UI | ✅ | Status lines and warnings come from hub state, decided in `StartPlaybackBuilder`/`StartConsequence` ([R4](research.md#r4-start-playback-content-one-builder-one-consequence-function)). Unknown join modes are treated as replace, so a warning never understates. A timed-out start is confirmed by hub state, not assumed ([R8](research.md#r8-timeout-recovery-fr-016a-and-the-long-link-timeout-fr-014)). Input kind uses the existing single function. No progress or metadata |
| III. Shared-First | ✅ | All code in `commonMain`. The link parser is hand-written, not `java.net.URI` ([R11](research.md#r11-link-normalisation-and-the-inline-message-fr-006)). No platform source set changes |
| IV. Test-First | ✅ | `checkLink`, `routesByRoom`, the builder, the consequence, text, error mapping, repository additions (MockEngine), `PlaybackStarter`, the view model and the Now Playing fence each get tests first ([quickstart](quickstart.md) §1, covering FR-017) |
| V. Resilient LAN | ✅ (one recorded deviation) | Stale state keeps lists and selections and disables Play. Timeouts lead to recovery, then plain copy. Problem details are mapped to the app's own words (`reason`/`outputId` never shown). An unknown route `joinMode` maps to an explicit `JoinMode.Unknown` and never fails a snapshot (`coerceInputValues`, contract test 7). An unknown source `defaultJoinMode` cannot be told apart from `null` and is a recorded deviation (Complexity Tracking). **Timeouts**: every call keeps the short 3 s timeout except `POST /play`, which allows 30 s because the hub fetches and resolves the link before answering (FR-014, [R8](research.md#r8-timeout-recovery-fr-016a-and-the-long-link-timeout-fr-014)). Connect stays at 3 s, so an unreachable hub still fails fast; the screen shows "Starting…" throughout, Close is never blocked (R9), and polling keeps its 3 s timeout |
| VI. Design Fidelity & A11y | ✅ | Layout per `StartPlayback.dc.html` ([UI contract](contracts/start-playback-ui.md)). Departures are listed in spec Assumptions (no preselection, named group in the warning, undrawn states). The scrolling body and fixed footer are a layout consequence of real data, also listed in the UI contract. Radio-group semantics, labels, and ≥ 44 dp targets; dimmed tiles are exempt |
| VII. Minimal Dependencies | ✅ | None added |

**Post-design re-check (after Phase 1)**: still passing.
- Generated types stay in `data/`. `HubError.Rejected` grows two plain strings, and the UI never
  shows them.
- The one-route assumption is not changed in Rooms, Now Playing or the Move sheet; only the new
  screen uses `routesByRoom` (backlog 004 keeps the rest).
- The design's undrawn states reuse existing styles and are listed as departures.
- Every "fresh snapshot" wait captures `session.startedSeq` (002 fence rule): R7, R8 and R10.

## Project Structure

### Documentation (this feature)

```text
specs/003-start-playback/
├── spec.md
├── plan.md              # this file
├── research.md          # Phase 0
├── data-model.md        # Phase 1
├── quickstart.md        # Phase 1
├── contracts/
│   ├── hub-repository.md
│   └── start-playback-ui.md
└── tasks.md             # /speckit-tasks (not created here)
```

### Source Code (changes against 002)

```text
gradle.properties                          # versionName 0.3.0-alpha, versionCode 3 (first commit)
AGENTS.md                                  # API 0.1.21; "one route per output" qualified (R1)

composeApp/src/commonMain/kotlin/sonora/multiroom/mobile/
├── AppGraph.kt                            # + PlaybackStarter (app scope); startPlaybackViewModel()
├── domain/
│   ├── Models.kt                          # + JoinMode (incl. Unknown); Route.joinMode; Source.defaultJoinMode
│   ├── PlaybackRules.kt                   # + routesByRoom(), Route.isAnnouncement
│   ├── LinkAddress.kt                     # LinkCheck, checkLink()
│   ├── StartRequest.kt                    # StartWhat, StartNames (plain types, US1)
│   ├── StartPlaybackBuilder.kt            # StartPlaybackContent, SourceOption, TargetOption, TargetStatus
│   └── StartConsequence.kt                # effectiveJoinMode, Consequence & friends (US2)
├── data/
│   ├── HubRepository.kt                   # + startSource, playLink; Rejected(+reason, +outputId)
│   ├── KtorHubRepository.kt               # + the two calls; link client (30 s)
│   ├── HttpClients.kt                     # rejection() reads reason/outputId; LINK_TIMEOUT_MILLIS
│   └── ApiMapping.kt                      # joinMode / defaultJoinMode mapping
└── ui/
    ├── Messages.kt                        # + StartFailure, startFailure(), startFailureMessage()
    ├── session/
    │   ├── HubSession.kt                  # + awaitFreshSnapshot(timeoutMillis) (fence on startedSeq, R7/R8)
    │   └── PlaybackStarter.kt             # app-scoped start + recovery (R7–R9)
    ├── nav/
    │   ├── Destinations.kt                # NowPlaying(+startedAfterSeq, +targetName); replaceTop()
    │   └── AppNavigation.kt               # StartPlayback entry; exit handling
    ├── nowplaying/NowPlayingViewModel.kt  # fence from startedAfterSeq; seeded target name (R10)
    ├── rooms/RoomsScreen.kt               # expose "Set your hub address" / stale / unreachable blocks for reuse
    ├── placeholder/                       # unchanged (still used by Sources)
    └── startplayback/
        ├── StartPlaybackViewModel.kt
        ├── StartPlaybackUiState.kt
        ├── StartPlaybackScreen.kt         # header, scrolling body, fixed footer
        ├── LinkField.kt
        ├── SourceRow.kt                   # reuses KindStyle
        ├── TargetTile.kt
        └── StartPlaybackText.kt           # status line, consequence, notes, Play label

composeApp/src/commonTest/kotlin/sonora/multiroom/mobile/
├── domain/       LinkAddressTest, RoutesByRoomTest, StartPlaybackBuilderTest, StartConsequenceTest
├── data/         KtorHubRepositoryActionsTest, KtorHubRepositorySnapshotTest (extended), Fixtures
├── ui/           MessagesTest (extended)
├── ui/session/   PlaybackStarterTest, HubSessionTest (extended: awaitFreshSnapshot)
├── ui/nav/       AppBackStackTest (extended)
├── ui/nowplaying/NowPlayingViewModelTest (extended)
├── ui/rooms/     FakeHub.kt (FakeRepository: + startSource, playLink, startResults queue)
└── ui/startplayback/ StartPlaybackViewModelTest, StartPlaybackTextTest
```

**Structure Decision**: the existing KMP wizard layout. New code goes in the `domain/`, `data/` and
`ui/` packages under `composeApp/src/commonMain`, with the new screen in `ui/startplayback/`.

## Implementation notes for tasks

1. **First commit**: version bump to `0.3.0-alpha` / `3` (AGENTS.md workflow).
2. **Reconcile the contract (Constitution I) before other work**: regenerate the client, build, and
   run every existing suite green on 0.1.21 with no behaviour change. Then add the join-mode and
   `reason`/`outputId` mapping test-first, and sync AGENTS.md.
3. **Test-first order**: `checkLink` → `routesByRoom` → `StartPlaybackBuilder` →
   `StartConsequence` → text (`StartPlaybackText`, `startFailureMessage`) → repository additions
   (MockEngine) → `PlaybackStarter` → `StartPlaybackViewModel` → the Now Playing fence and
   `replaceTop`. Watch each one fail first.
4. **UI**: build the screen per the [UI contract](contracts/start-playback-ui.md), wire the
   navigation (replace the placeholder entry) and the success hand-off to Now Playing.
5. Finish with [quickstart](quickstart.md) §1 all green, then hand over for §2 on the device and
   record the results in `verification.md`. Rows 5 and 13 check two hub assumptions: a start on a
   group member stops the whole group playback, and a link's runtime input keeps the sent address.

## Open points for the user (not blocking the plan)

- Rows 5 and 13 of quickstart §2 confirm two hub behaviours on the device. If either turns out
  differently, the consequence line (row 5) or the timeout match (row 13) is adjusted in a
  follow-up, not guessed now.
- When a source already plays as an announcement on or overlapping the chosen target, the spec
  leaves open whether the hub returns that playback or refuses (spec Edge Cases). The app handles
  both answers. It cannot be checked on the device until a configured source declares
  `DUCK_OTHERS` as its default; record it in `verification.md` when one does.

## Complexity Tracking

No new dependencies. The app-scoped starter (R9) and the second HTTP client for `/play` (R8) are
each the smallest way to meet a spec requirement.

| Deviation | Why needed | Simpler alternative rejected because |
|---|---|---|
| Constitution V, "unknown enum values map to an explicit Unknown": an unrecognised `InputResponse.defaultJoinMode` becomes `null` ("none declared"), not `JoinMode.Unknown` ([R3](research.md#r3-join-modes-in-the-domain)) | `HubJson` uses `coerceInputValues`, so the generated client decodes an unrecognised value of this nullable field to `null` before the mapping sees it. Route `joinMode` is not affected (its `null` maps to `Unknown`) | A custom serializer or a wrapper around the generated model for this one field. Its only effect would be identical behaviour: both `null` and `Unknown` resolve to Replace in `effectiveJoinMode`, so the user is never told less than may stop |
| Constitution V, "short timeouts": `POST /api/v2/play` uses a 30 s request/socket timeout | The hub fetches and resolves the link (SoundCloud, YouTube) before it answers; 3 s would turn most successful link starts into false failures (FR-014) | Keeping 3 s and relying on timeout recovery (FR-016a) for every link: recovery waits at most 5 s more and depends on the runtime input's `uri` matching (R8 risk), so most links would be reported as "Couldn't reach the hub" while they play. Connect stays at 3 s, so an unreachable hub still fails fast |
