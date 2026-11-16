# Implementation Plan: Find and Play Radio Stations

**Branch**: `009-radio-stations` | **Date**: 2026-11-16 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/009-radio-stations/spec.md`

## Summary

Start Playback gains a section "Or a radio station": a row that opens a new radio screen, and the
three stations listened to most recently from this phone. The radio screen searches the hub's
radio extension by name, narrows by country, tag and language, orders the list, pages it, and
hands a chosen station back to Start Playback, where it is a source like any other. A station
that plays is shown as kind "Radio" and as a live stream on every screen, whichever client
started it. All of it exists only while the hub reports the `radio` extension as active.

Technically:
- **Contract**: `Radio` is added to the generator's tag filter; four read-only repository methods
  on a 6 s client ([R1](research.md#r1-the-generated-client),
  [R2](research.md#r2-repository-methods-and-the-radio-timeout),
  [contract](contracts/hub-repository.md)).
- **Availability**: an app-scoped `RadioAvailability` asks `GET /api/v2/extensions` when Start
  Playback opens ([R3](research.md#r3-knowing-whether-radio-is-offered-fr-001-fr-002-fr-033)).
- **Kind**: `SourceKind.Radio`, decided by the `radiobrowser://` scheme in `domain/SourceKind.kt`,
  which also makes a station a live stream
  ([R4](research.md#r4-a-station-as-a-kind-of-source-fr-025-to-fr-028)).
- **Start**: `StartWhat.Station` through the existing `POST /api/v2/play`; "already playing" and
  timeout recovery match by the station's address
  ([R5](research.md#r5-starting-a-station-fr-015-to-fr-020)).
- **Radio screen**: `Destination.Radio` with its own view model: debounce, "latest wins", paging
  with duplicates dropped, the 10,000 limit, filter sheets
  ([R6](research.md#r6-the-radio-screens-list-fr-003-fr-004-fr-005-fr-012),
  [R7](research.md#r7-filters-and-their-sheets-fr-006-to-fr-011),
  [UI contract](contracts/radio-ui.md)).
- **Recent**: an app-scoped `RecentStationTracker` judges each started station against the
  session's snapshots and adds it after 30 seconds of play
  ([R8](research.md#r8-recent-stations-and-the-30-second-rule-fr-021-to-fr-024a)).
- **Storage**: recent stations, filters and pending entries as JSON in the existing preferences
  file ([R10](research.md#r10-what-is-kept-on-the-phone)).
- **No** new dependency, no platform code, no hub change.

## Technical Context

**Language/Version**: Kotlin 2.4.21 (KMP), JDK 17+, AGP 9.5.0 (unchanged)

**Primary Dependencies**: unchanged: Compose Multiplatform 1.12.2, material3 1.9.0, Navigation 3
1.2.0, Lifecycle ViewModel 2.11.0, Ktor 3.6.0, kotlinx.serialization 1.12.0, coroutines 1.11.0,
DataStore 1.3.0, OpenAPI Generator 7.14.0 (`kotlin`, `multiplatform`). None added
([R13](research.md#r13-dependencies-and-versions)).

**Storage**: the existing DataStore Preferences file gains three keys (`radio_recent`,
`radio_filters`, `radio_pending`) holding JSON. The selected station and the typed name live in
their view models' `SavedStateHandle`.

**Testing**: kotlin-test and kotlinx-coroutines-test (virtual time) in `commonTest`, Ktor
`MockEngine` for the repository, the existing `FakeRepository` and fake session for view models
and session objects ([R14](research.md#r14-testing)). Layout, scrolling, TalkBack and real
playback on the device (quickstart §2).

**Target Platform**: Android (minSdk 26). iOS targets declared, not built.

**Project Type**: mobile app (KMP: `composeApp` + `androidApp` + `iosApp` stub)

**Performance Goals**: matching stations within 2 s of the last keystroke (SC-003: 0.5 s wait plus
one request of 0.3–1.4 s); the catalogue's failure known within ~6 s (SC-008); the radio screen
never blank (SC-004); no extra request on the 2.5 s poll.

**Constraints**: only `/api/v2/**` and the radio extension's read-only requests; one poll loop
app-wide (the radio screen runs none); radio requests wait 6 s, everything else keeps its
timeout; never a `joinMode`, `chime` or `displayName` on a start; nothing Android-only in
`commonMain`; no request to any host but the hub (no station images).

**Scale/Scope**: one home hub; a catalogue of tens of thousands of stations served 50 at a time,
at most 10,000 per search; 225 countries, 585 languages, more than ten thousand tags. 1 new
screen with 4 sheets, 4 new repository methods, 3 new session objects, 1 new store. About 14 new
production files and 20 edited ones; 8 new and 12 extended test files.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | How |
|---|---|---|
| I. API Contract Fidelity | ✅ | Only `/api/v2/**` plus the read-only `/api/radio/**` requests that constitution 1.6.0 allows; `/api/tts/*` stays out. The client is generated from `api/openapi.json` 0.1.34 with `Radio` added to the tag filter, never hand-written ([R1](research.md#r1-the-generated-client)). Screens and domain use `Station`, `Country` and the like; wire types stay in `data/` and `verifyLayering` enforces it. Radio is offered only while `GET /api/v2/extensions` lists `radio` as `ACTIVE`, and the app works fully without it ([R3](research.md#r3-knowing-whether-radio-is-offered-fr-001-fr-002-fr-033)). No guessed field: what the hub does not say (why a start failed, whether HLS is refused) is not shown and is recorded as a hub gap ([R15](research.md#r15-left-to-the-hub-or-left-out)). No hub change |
| II. Truthful UI | ✅ | A station is a live stream by the one existing function, extended for the station scheme; kind "Radio" by the one kind function ([R4](research.md#r4-a-station-as-a-kind-of-source-fr-025-to-fr-028)). No votes, images, artwork or progress. "Recent" is judged by what the hub reports, not by a timer ([R8](research.md#r8-recent-stations-and-the-30-second-rule-fr-021-to-fr-024a)). A failed start names no cause the hub did not give. "Already playing" is decided before Play for a station too |
| III. Shared-First | ✅ | Everything in `commonMain` / `commonTest`. The phone's region comes from Compose's common `Locale`, so no `expect`/`actual`; `androidMain`, `iosMain` and `androidApp` are untouched |
| IV. Test-First | ✅ | Every rule is a pure function or a view model with tests written first ([R14](research.md#r14-testing)): kind and live-stream inference, name cleaning, paging and duplicates, the recent-station verdict, availability, error mapping. The repository additions are tested against `MockEngine` with the contract's shapes, RFC 7807 bodies and network failures ([contract tests](contracts/hub-repository.md#contract-tests-mockengine)). The build and all three test tasks gate the change |
| V. Resilient LAN Networking | ✅ (one recorded deviation) | A missing extension, a slow or broken catalogue and an unreachable hub are three distinct, plain states with a way forward ([R9](research.md#r9-radio-errors-in-plain-words-fr-029-to-fr-034)); none crashes or hangs. Unknown JSON fields are ignored; an unknown radio problem type is the catalogue message. Problem details never reach the screen. The radio screen polls nothing of its own. **Timeouts**: radio requests wait 6 s, because the hub itself waits up to 5 s for the catalogue and then answers 503 (Complexity Tracking) |
| VI. Design Fidelity & Accessibility | ✅ | The canvas was drawn first (commit `15c65e7`: `Radio.dc.html`, `RadioFilterSheet.dc.html`, `StartPlaybackRadio.dc.html`) and the [UI contract](contracts/radio-ui.md) follows it; colours and type from tokens, no new token. Targets ≥ 44 dp, every icon-only control has a spoken label, new colour pairs go into `ContrastTest`. Agreed details kept: "will stop" notice, live-stream wording, marquee rules (MOB-007). One recorded departure: the canvas's `startFailed` state is not built, a failed station start uses the snackbar as the spec says (decision 1 below) |
| VII. Minimal Dependencies | ✅ | None added, no version changed ([R13](research.md#r13-dependencies-and-versions)) |
| Full app test gate | ✅ planned | The final task updates `docs/testing/full-app-test.md`: a new section for radio with rows for quickstart §2, the fixture extended (the `radio` extension `ACTIVE`, a known-good station), rows that read a source's kind or the Start Playback layout updated, header to MOB-009 / 0.9.0-alpha / API 0.1.34 |

**Post-design re-check (after Phase 1)**: unchanged, all ✅ with the one recorded deviation.
- Wire types stay in `data/`; the domain gains plain types and pure functions
  ([data-model.md](data-model.md)).
- `HubRepository` grows by four read-only methods; `playLink` is reused for a station, so no
  second way to start exists.
- The extensions request moves from "only while Settings' Extensions tab is open" to "also when
  Start Playback opens"; AGENTS.md "Endpoints per screen" is updated with the feature.

**Decisions made in this plan that the spec does not spell out** (for review before
`/speckit-tasks`):

1. **Where "Couldn't start <station>" is shown: settled by the owner on 2026-11-16.** In the
   snackbar, as the spec says and as link failures are shown. The canvas's `startFailed` state (a
   line above Play) is not built; showing every start failure that way is the backlog item
   [start-failure-above-play.md](../../docs/backlog/start-failure-above-play.md)
   ([R5](research.md#r5-starting-a-station-fr-015-to-fr-020)).
2. **The list heading**: "Stations in <country>" as on the canvas, and "Stations" when no country
   is chosen (the canvas shows only the first).
3. **"Clear filters"** removes country, tag and language, keeps the order and the typed name, and
   is offered only when one of the three is chosen.
4. **A chosen tag in the Tag sheet** is shown as the first row, since its own place may be
   thousands of rows down ([R7](research.md#r7-filters-and-their-sheets-fr-006-to-fr-011)).
5. **The search field drops a typed `%`** (and the tag field `%` and `/`) as the canvas does,
   instead of keeping it on screen and leaving it out of the request.
6. **A selected recent row in Start Playback does nothing when tapped again**; the "Find a radio
   station" row above it opens the radio screen. Only a selected station that is not among the
   three reopens the radio screen from its own row ("Change"), as the canvas draws it.
7. **The Play button's three-part layout applies to source names too**, so a long source name no
   longer hides the target.
8. **The 30 seconds are counted on the phone's clock**, so that a pending station survives the
   app's process being ended, and **from the first moment the hub reports the playback as
   playing** (the start's answer when it is already `Active`, otherwise the first refresh that
   shows it so). Time spent `Starting` does not count (FR-021a, settled after the design review).

Settled after `/speckit-analyze` on 2026-11-16 and written into the spec:

- Decisions 1 and 3 are departures from the canvas and are now listed in the spec's Assumptions
  (Constitution VI). Decision 6 is now User Story 1, scenario 8.
- **A station shown in the radio section is not listed again under "Or pick a source"** while it
  plays (FR-022a); a playing station the section does not show stays in the source list
  ([R12](research.md#r12-start-playback-with-radio-fr-002-fr-016-fr-016a-fr-022a)).
- **A selected station is deselected when radio stops being offered**, for whatever reason
  (FR-022b).
- **Settled after the design review** (owner-judge, 2026-11-16, commit `3617d83`, verdict
  CONCERNS): a station started through "Or pick a source" does not enter "Recent" (spec
  Assumptions); the 30 seconds start at the first `Active` (decision 8); a failed radio request
  is told apart from an unreachable hub by a fresh session refresh, not by the session's last
  known state ([R9](research.md#r9-radio-errors-in-plain-words-fr-029-to-fr-034)); tag paging
  stops at offset 10,000 like station paging; a selected station counts only while radio is
  offered; an error answer to the extensions request means "not offered"
  ([R3](research.md#r3-knowing-whether-radio-is-offered-fr-001-fr-002-fr-033)).
- **Settings' run-time source row shows a station by its stream tile only**; it names no kind in
  words, as before (User Story 2, scenario 3).

## Project Structure

### Documentation (this feature)

```text
specs/009-radio-stations/
├── plan.md              # This file
├── research.md          # Phase 0
├── data-model.md        # Phase 1
├── quickstart.md        # Phase 1
├── contracts/
│   ├── hub-repository.md   # the four radio requests, mapping, errors, contract tests
│   └── radio-ui.md         # Start Playback with radio, the radio screen, sheets, a playing station
├── checklists/requirements.md
└── tasks.md             # Phase 2 (/speckit-tasks)
```

### Source Code (repository root)

```text
gradle.properties                                   # 0.9.0-alpha / 9 (first commit)
composeApp/build.gradle.kts                         # "Radio" added to the generator's tag filter
composeApp/src/commonMain/kotlin/sonora/multiroom/mobile/
├── AppGraph.kt                                     # one DataStore for both stores; RadioStore, RadioAvailability,
│                                                   #   StationPicker, RecentStationTracker, region, radioViewModel()
├── domain/
│   ├── Radio.kt                                    # new: Station, StationQuery, StationOrder, pages, Country,
│   │                                               #   Language, Tag, RadioFilters, PendingStation and the pure rules
│   ├── Models.kt                                   # SourceKind.Radio
│   ├── SourceKind.kt                               # isStationAddress, sameStation; kind, live stream, label, detail
│   ├── StartRequest.kt                             # StartWhat.Station
│   ├── StartConsequence.kt                         # already playing by station address; a station replaces
│   ├── StartPlaybackBuilder.kt                     # radioRows
│   └── SettingsBuilder.kt                          # RuntimeSourceRow.kind
├── data/
│   ├── HubRepository.kt                            # + searchStations, radioCountries, radioLanguages, radioTags
│   ├── KtorHubRepository.kt                        # RadioApi on the 6 s client
│   ├── HttpClients.kt                              # RADIO_TIMEOUT_MILLIS
│   ├── ApiMapping.kt                               # RadioStation → Station and the list types
│   ├── HubAddressStore.kt                          # takes the shared DataStore
│   └── RadioStore.kt                               # new: RadioStore, DataStoreRadioStore, station JSON
└── ui/
    ├── Messages.kt                                 # StartKind.Station, StationFailed, RadioFailure, radioFailure
    ├── session/
    │   ├── RadioAvailability.kt                    # new
    │   ├── StationPicker.kt                        # new
    │   ├── RecentStationTracker.kt                 # new
    │   └── PlaybackStarter.kt                      # station starts; tracks on success
    ├── nav/
    │   ├── Destinations.kt                         # Destination.Radio, "radio"
    │   └── AppNavigation.kt                        # the Radio entry; onOpenRadio from Start Playback
    ├── radio/                                      # new package
    │   ├── RadioViewModel.kt
    │   ├── RadioUiState.kt
    │   ├── RadioScreen.kt                          # header, field, chips, Recent, list and its states
    │   ├── StationRow.kt                           # the row shared with Start Playback
    │   ├── FilterSheet.kt                          # Country, Tag, Language, Order
    │   └── RadioText.kt                            # all radio wording and spoken labels
    ├── startplayback/
    │   ├── StartPlaybackViewModel.kt               # radio rows, selected station, saved state
    │   ├── StartPlaybackUiState.kt
    │   ├── StartPlaybackScreen.kt                  # the section; the three-part Play label
    │   ├── RadioRows.kt                            # new: the "Find a radio station" row and the selected row
    │   └── StartPlaybackText.kt                    # playLabelParts
    ├── rooms/KindStyle.kt                          # Radio → stream colours and icon
    ├── settings/SettingRows.kt                     # the runtime row draws its kind's tile
    └── theme/Icons.kt                              # Search, ChevronDown, Sort
composeApp/src/commonTest/kotlin/sonora/multiroom/mobile/
├── domain/RadioTest.kt                             # new
├── domain/SourceKindTest.kt, AddressDetailTest.kt, SourceDetailTest.kt, StartConsequenceTest.kt,
│   StartPlaybackBuilderTest.kt, SettingsBuilderTest.kt
├── data/KtorHubRepositoryRadioTest.kt              # new
├── data/RadioStoreTest.kt, InMemoryRadioStore.kt   # new
├── data/KtorHubRepositorySnapshotTest.kt, Fixtures.kt
├── ui/MessagesTest.kt
├── ui/session/RadioAvailabilityTest.kt, RecentStationTrackerTest.kt, StationPickerTest.kt   # new
├── ui/session/PlaybackStarterTest.kt
├── ui/radio/RadioViewModelTest.kt, RadioTextTest.kt                                        # new
├── ui/startplayback/StartPlaybackViewModelTest.kt, StartPlaybackTextTest.kt
├── ui/nav/AppBackStackTest.kt
├── ui/rooms/FakeHub.kt                             # FakeRepository: the four radio methods
└── ui/theme/ContrastTest.kt
AGENTS.md                                           # layout (ui/radio, session objects), endpoints per screen,
                                                    #   hub gaps (no failure reason, HLS), agreed details
docs/hub-api-history.md                             # 0.1.34: radio is used since 0.9.0-alpha
docs/backlog/radio-stations.md (delete on ship) + INDEX.md row
CHANGELOG.md                                        # [0.9.0-alpha]
docs/testing/full-app-test.md                       # radio section, fixture, header
```

**Structure Decision**: the existing KMP layout. The radio screen gets its own package
`ui/radio/`; the station row lives there and is reused by Start Playback. Domain types and rules
for radio sit in one new file, `domain/Radio.kt`, while the rule for "is this source a station"
stays in `domain/SourceKind.kt` with the other kind rules (FR-028). The three app-scoped objects
join `ui/session/`, where the app already keeps state that outlives a screen.

## Implementation notes for tasks

1. **First commit**: `sonora.versionName=0.9.0-alpha`, `sonora.versionCode=9`.
2. **Suggested order**, each step test-first:
   1. User Story 2 alone (kind "Radio", live stream, no address detail, the runtime row's tile):
      it needs no radio request and already improves stations started from other clients.
   2. The generator filter, the repository methods and their contract tests.
   3. `RadioAvailability`, `StationPicker`, `Destination.Radio`, the radio screen with search by
      name, and Start Playback's row, selection and start (User Story 1), with the failure
      states of User Story 5 that the same code paths produce.
   4. `RadioStore`, `RecentStationTracker`, "Recent" on both screens (User Story 3).
   5. Filters, sheets, order, first visit (User Story 4).
3. `DataStoreHubAddressStore`'s constructor changes; `AppGraph(dataStorePath)` stays the
   production entry, so `SonoraApplication` is not edited.
4. Finish with [quickstart](quickstart.md) §1 all green, update AGENTS.md, the API history, the
   changelog and the backlog, then hand over for §2 on the device and record the results in
   `verification.md`. The last task is "Update docs/testing/full-app-test.md for this feature".

## Complexity Tracking

| Deviation | Why needed | Simpler alternative rejected because |
|---|---|---|
| Constitution V, "short timeouts": the four radio requests use a 6 s request and socket timeout (connect stays 3 s) | The hub waits up to 5 s for the catalogue and then answers 503 with a radio problem type. With the ordinary 3 s the app would give up first and report a slow catalogue as an unreachable hub (FR-031, SC-008) | Keeping 3 s: the user would see "Can't reach the hub" while the hub is fine, and the Retry that fits the real cause would never be offered. 6 s is the smallest value above the hub's own bound; the screen shows its loading state meanwhile and Back is never blocked |
