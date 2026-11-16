---

description: "Task list for 009 Find and Play Radio Stations"
---

# Tasks: Find and Play Radio Stations

**Input**: Design documents from `specs/009-radio-stations/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md),
[data-model.md](data-model.md), [contracts/hub-repository.md](contracts/hub-repository.md),
[contracts/radio-ui.md](contracts/radio-ui.md), [quickstart.md](quickstart.md)

**Tests**: REQUIRED for every rule that turns hub data into UI state, every view model, the
repository additions and the back stack (Constitution IV, research R14). Every test task comes
before its implementation task and MUST be seen failing first. Layout, scrolling, the keyboard,
TalkBack and real playback are checked on the device (quickstart §2); no Compose UI test setup is
added.

**Organization**: grouped by user story. Spec priorities:
- US1 P1: find a station by name and play it in a room (MVP)
- US2 P2: a playing station looks like a station
- US3 P2: play a recent station again
- US4 P2: narrow the list by country, tag and language
- US5 P3: radio is missing, slow or failing, and the app says so plainly

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on an unfinished task)
- **[Story]**: US1–US5 from spec.md

## Path Conventions

- Shared code: `composeApp/src/commonMain/kotlin/sonora/multiroom/mobile/` (abbreviated `main/` below)
- Shared tests: `composeApp/src/commonTest/kotlin/sonora/multiroom/mobile/` (abbreviated `test/` below)

## General rules for every task

- **Only** `/api/v2/**` and the four read-only radio requests of
  [contracts/hub-repository.md](contracts/hub-repository.md). Never `GET /api/radio/stations/{uuid}`,
  any `/api/tts/*` path, group volume, `joinMode`, `chime` or `displayName` on a start. Radio is
  offered only while `GET /api/v2/extensions` lists `radio` as `ACTIVE`; without it the app is
  exactly as before this feature.
- Everything in `commonMain` / `commonTest`; no `expect`/`actual`, nothing in `androidMain`,
  `iosMain` or `androidApp`. Generated wire types (`RadioStation`, …) stay in `data/`
  (`verifyLayering`). No new dependency, no version change.
- Colours and type only through `SonoraTheme` tokens; no new token. Touch targets ≥ 44 dp; every
  icon-only control has the spoken label of [contracts/radio-ui.md](contracts/radio-ui.md).
- Wording lives in `StartPlaybackText.kt`, `RadioText.kt` and `Messages.kt`; decisions in `domain/`
  functions; screens only draw. A hub error never reaches the screen as a status code, a title or a
  detail (SC-007).
- Code comments must not cite `FR-`, `SC-`, `US` or research numbers of this feature (`MOB-009` is
  fine).
- Extend existing test files and their helpers (`FakeRepository` in test/ui/rooms/FakeHub.kt,
  `Fixtures.kt`, the session fakes) rather than adding parallel fixtures.
- A failed station start is shown in the snackbar like a link failure; the canvas's `startFailed`
  line above Play is **not** built (plan, decision 1).

---

## Phase 1: Setup

- [ ] T001 Set `sonora.versionName=0.9.0-alpha` and `sonora.versionCode=9` in gradle.properties as
  the first implementation commit (AGENTS.md "Bump the app version first").
- [ ] T002 Add `Radio` to the generator's tag filter in composeApp/build.gradle.kts
  (`"tag:Routes|Outputs|Inputs|Groups|Master Mute|Playback|Extensions|Radio"`). Generate once and
  check that `RadioApi.searchRadioStations`, `listRadioCountries`, `listRadioLanguages`,
  `listRadioTags` and the models `RadioStation`, `RadioStationPage`, `RadioCountry`,
  `RadioLanguage`, `RadioTag`, `RadioTagPage` exist; if an operation has another name, the HTTP
  column of contracts/hub-repository.md is binding. Commit no generated code.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: the domain types, the four repository methods, the shared preferences file with the
radio store, and the three small app-scoped objects every story reads.

**CRITICAL**: no user story starts before this phase is complete.

### Tests first

- [ ] T003 [P] Create test/domain/RadioTest.kt with failing tests for the pure rules of
  data-model.md "Station" and "RecentStation": `stationName` (removes every `%`, trims, cuts to 100
  characters, null when empty or blank), `tagName` (also removes `/`), `stationDisplayName`
  ("Unnamed station"), `stationDetail` (known parts of country code, codec, "<n> kbps" joined with
  " · "; null when none), `mergePage` (a `uuid` already listed is dropped, page order kept),
  `canLoadMore` / `limitReached` (`nextOffset != null && nextOffset < 10000` / `>= 10000`),
  `regionCountry` (case-insensitive code match, null when none), `narrow` (name contains the
  trimmed text ignoring case; all when blank), `groupThousands` (6965 → "6,965"), `addRecent`
  (station first, older entry with the same `uuid` removed, cut to ten), `radioOffered` (extension
  `radio` in any letter case with status `Active`; any other status or none → false).
- [ ] T004 [P] Create test/data/KtorHubRepositoryRadioTest.kt with the failing `MockEngine`
  contract tests 1–16 of contracts/hub-repository.md (exact query parameters, percent-encoding of
  "Хіт FM & co", the live sample page, null and `UNKNOWN` fields, a blank `uuid` dropped, unknown
  fields ignored, `nextOffset` null or missing, countries / languages / tags, the three problem
  bodies sent as `application/problem+json`, the plain 404, the HTML and wrong-shape 200 bodies,
  the 5.5 s / 6.5 s timeouts in virtual time and that a snapshot still times out at 3.5 s, network
  failures, and `playLink` with a station address posting only `uri`, `targetId`, `targetType`).
  First check that `application/problem+json` is decoded into `ErrorResponse` (research R9 "To
  confirm"); if not, make `rejection()` read the body as text and decode it with `HubJson`.
- [ ] T005 [P] In test/ui/MessagesTest.kt add failing tests for `radioFailure(error)` (every row of
  research R9: `Unreachable` → `HubUnreachable`; 404 whose type does not start with
  `urn:multiroom:radio:` → `Gone`; 502, 503, 500, 400, 404 with a radio type, an unknown radio
  type and `Unexpected` → `Catalogue`).
- [ ] T006 [P] Create test/data/RadioStoreTest.kt and test/data/InMemoryRadioStore.kt (the
  in-memory implementation for view-model tests, shaped like InMemoryHubAddressStore.kt). Failing
  tests: JSON round trips of recent stations, filters and pending entries; an unreadable value is
  absent (empty list, no filters), an unknown `order` reads as "Most voted"; `addRecent` keeps ten;
  `removeRecent(uuid)` removes only that entry and touches no pending entry; `encodeStation` /
  `decodeStation` round trip and `decodeStation` of garbage is null.
- [ ] T007 [P] Create test/ui/session/RadioAvailabilityTest.kt with failing tests (research R3):
  starts `Unknown`; `refresh()` → `Offered` for an active `radio` extension, `NotOffered`
  otherwise; a failed request leaves the state as it was; an answer for a repository that is no
  longer the session's is dropped; a changed hub address returns it to `Unknown`; `markGone()` →
  `NotOffered`.
- [ ] T008 [P] Create test/ui/session/StationPickerTest.kt with failing tests: `pick` publishes the
  station and `consumePicked` clears it; `reportGone` / `consumeGone` likewise; one pending value
  only.
- [ ] T009 [P] In test/ui/nav/AppBackStackTest.kt add failing tests: `Destination.Radio` encodes as
  `radio` and decodes back; the bottom bar is hidden on it.

### Implementation

- [ ] T010 Create main/domain/Radio.kt (after T003): `Station`, `StationQuery`, `StationOrder`
  (`MostVoted`, `Trending`, `NameAZ`), `StationPage`, `Country`, `Language`, `Tag`, `TagPage`,
  `RadioFilters` (with `toQuery(name)`) and the pure functions and constants of T003
  (`RADIO_PAGE_SIZE = 50`, the 10,000 limit), `radioOffered(inventory)`. Quote data-model.md for
  each field's rule (e.g. `uuid` blank ⇒ dropped; `codec` blank or `UNKNOWN` in any letter case ⇒
  null; `bitrate` null or ≤ 0 ⇒ null). Make T003 pass.
- [ ] T011 [P] In main/data/HttpClients.kt add `RADIO_TIMEOUT_MILLIS = 6000` and the client
  configuration for it (request and socket 6 s, connect the usual 3 s), the pattern of the 30 s
  playback client.
- [ ] T012 [P] In main/data/ApiMapping.kt map `RadioStation` → `Station`, `RadioStationPage` →
  `StationPage`, `RadioCountry`, `RadioLanguage`, `RadioTag`, `RadioTagPage` per the "Mapping to
  domain types" table (blank entries dropped; `Language.code` not mapped; counts `Long`).
- [ ] T013 Add `searchStations`, `radioCountries`, `radioLanguages` and `radioTags` to
  main/data/HubRepository.kt and implement them in main/data/KtorHubRepository.kt on `RadioApi`
  with the 6 s client (needs T002, T011, T012). Order mapping: `MostVoted` → `votes`, `Trending` →
  `clickCount`, `NameAZ` → `name`. Send `limit=50`, `offset`, `order` and the optional values only
  when not null; never `direction`, `codec` or `bitrateMin`. Make T004 pass.
- [ ] T014 Implement the four methods in `FakeRepository` (test/ui/rooms/FakeHub.kt) with scripted
  answers and call recording, so view-model tests can script pages, delays and failures, and fix
  any other `HubRepository` implementer the compiler reports.
- [ ] T015 In main/ui/Messages.kt add `RadioFailure` (`Catalogue`, `HubUnreachable`, `Gone`) and
  `radioFailure(error)` (after T005). The catalogue wording lives in `RadioText.kt` (T033).
- [ ] T016 Create main/data/RadioStore.kt (after T006): the `RadioStore` interface of data-model.md
  and `DataStoreRadioStore` with the keys `radio_recent`, `radio_filters`, `radio_pending` holding
  JSON through private `@Serializable` classes (`ignoreUnknownKeys`; an unreadable value counts as
  absent), plus `encodeStation` / `decodeStation`. Define `PendingStation` in domain/Radio.kt.
- [ ] T017 Change main/data/HubAddressStore.kt so `DataStoreHubAddressStore` takes the shared
  `DataStore<Preferences>` instead of a path, and update test/data/InMemoryHubAddressStore.kt or
  its callers only if the compiler requires it. In main/AppGraph.kt create the one DataStore from
  `dataStorePath` and hand it to both stores; `AppGraph(dataStorePath)` stays the production entry
  and `SonoraApplication` is not edited.
- [ ] T018 [P] Create main/ui/session/RadioAvailability.kt (after T007): `state: StateFlow<RadioOffer>`
  (`Unknown`, `Offered`, `NotOffered`), `suspend refresh()`, `markGone()`; `Unknown` again when the
  hub address changes. Wire it in AppGraph.
- [ ] T019 [P] Create main/ui/session/StationPicker.kt (after T008): `picked`, `gone`, `pick`,
  `reportGone`, `consumePicked`, `consumeGone`. Wire it in AppGraph.
- [ ] T020 Add `Destination.Radio` (data object, saved as `radio`, not a tab, bottom bar hidden) in
  main/ui/nav/Destinations.kt (after T009). The entry itself is added in T040.

**Checkpoint**: `./gradlew :composeApp:testAndroidHostTest` is green; the app behaves exactly as
before this feature.

---

## Phase 3: User Story 1 - Find a station by name and play it in a room (Priority: P1) 🎯 MVP

**Goal**: from Start Playback open the radio screen, type part of a name, choose a station, come
back with it selected, choose a room and Play.

**Independent Test**: with the radio extension active and an idle room, open "Play something",
open the radio screen, type "mango", choose MANGORADIO, pick the room and press Play: the station
is audible and Now Playing shows it (quickstart §2 rows 1, 3–6).

### Tests for User Story 1 (write first, see them fail)

- [ ] T021 [P] [US1] In test/domain/StartConsequenceTest.kt add failing tests: a station on a busy
  target says what will stop; a station replaces (`effectiveJoinMode(Station)` = `Replace`);
  `alreadyThere` is true when a live route addressed to the target plays an input whose `uri` is
  the same station (`sameStation`, whichever input id), false for another station or another
  target (FR-017, whichever client started it).
- [ ] T022 [P] [US1] In test/domain/StartPlaybackBuilderTest.kt add failing tests for
  `radioRows(offered, recent, selected)`: null when not offered; the first three recent stations;
  a selected station that is one of them marks that row and `found` is null; a selected station
  that is not among them is `found` and takes the place of the "Find a radio station" row; a
  station is never listed twice.
- [ ] T023 [P] [US1] In test/ui/MessagesTest.kt add failing tests for `startFailure` with
  `StartKind.Station`: 422, 502, 503, 400 and any other answer → `StationFailed(name)` worded
  "Couldn't start <name>"; a `reason` refusal, 404 and unreachable keep their existing meaning.
- [ ] T024 [P] [US1] In test/ui/session/PlaybackStarterTest.kt add failing tests: a station start
  calls `playLink` with the station's address and sends no `displayName`; the answer is returned
  like a link's; a lost answer is recovered by a runtime input with the same station address; a
  failed start reports `StationFailed` and starts no tracking.
- [ ] T025 [P] [US1] In test/ui/startplayback/StartPlaybackViewModelTest.kt add failing tests: the
  radio section only while `RadioAvailability` is `Offered` (never for `Unknown` or `NotOffered`);
  a picked station becomes `selectedStation` and is consumed; choosing a source or typing a link
  deselects the station and choosing a station clears the source and the link text; the Play label
  is `PlaySource(stationDisplayName, target)`; a failed station start keeps the station and the
  target selected and sets the snackbar `message` "Couldn't start <station>"; the selected station
  survives saved-state restore (`station` key, JSON); `openRadio` is one-shot; availability is
  refreshed each time the screen becomes visible.
- [ ] T026 [P] [US1] In test/ui/startplayback/StartPlaybackTextTest.kt add failing tests for
  `playLabelParts` (`("Play ", "MANGORADIO", " in Bedroom")`; the name is empty for "Play", "Play
  link in <target>" and "Starting…", whose whole text is in `before`; source names use the same
  parts) and for the station row's second line ("Radio · DE · MP3 · 128 kbps", or "Radio" alone).
- [ ] T027 [P] [US1] Create test/ui/radio/RadioViewModelTest.kt with failing tests for the name
  search: the list loads without typing (first page, no filters, `MostVoted`); a typed name loads
  after 500 ms (virtual time) and `%` is not sent; only the latest text decides the list (an
  earlier slow answer never replaces it); a blank name counts as empty; a trailing space loads
  nothing again; paging requests the next page within 10 rows of the end, drops duplicate `uuid`s,
  follows a page of only duplicates with the next request at once, stops at `nextOffset == null`
  and at 10,000; choosing a station calls `StationPicker.pick` and sets `exit = Chosen`; nothing is
  emitted after the view model is cleared; the typed name is kept in `SavedStateHandle`.
- [ ] T028 [P] [US1] Create test/ui/radio/RadioTextTest.kt with failing tests for the wording and
  spoken labels of the screen and a station row ("Radio stations", "Station name", "Clear the
  name", "Unnamed station", "<name>, <second line>", "Loading stations", "Loading more stations…").

### Implementation for User Story 1

- [ ] T029 [US1] In main/domain/StartRequest.kt add `StartWhat.Station(station)` and
  `effectiveJoinMode` → `Replace`; in main/domain/StartConsequence.kt handle a station (T021). In
  main/domain/SourceKind.kt add only `isStationAddress` and `sameStation` here (the kind rules are
  US2); fix every `when` over `StartWhat` the compiler reports.
- [ ] T030 [US1] In main/domain/StartPlaybackBuilder.kt add `radioRows` (T022).
- [ ] T031 [US1] In main/ui/Messages.kt add `StartKind.Station`, `StartFailure.StationFailed(name)`
  and the `startFailure` mapping (T023).
- [ ] T032 [US1] In main/ui/session/PlaybackStarter.kt start a `StartWhat.Station` through
  `HubRepository.playLink(station.uri, target)` (never through `checkLink`), carry the station's
  display name in `StartNames.source`, and recover a lost answer by address (T024).
- [ ] T033 [P] [US1] Create main/ui/radio/RadioText.kt (all radio wording and spoken labels, T028)
  and main/ui/radio/RadioUiState.kt (`RadioUiState`, `StationList` with `phase`, `nextOffset`,
  `limitReached`, `problem`, derived `empty`; `RadioExit`; `FilterSheet` as a type only, filled in
  US4).
- [ ] T034 [P] [US1] Add `Search`, `ChevronDown` and `Sort` to main/ui/theme/Icons.kt (vector
  drawn in code like the existing icons).
- [ ] T035 [US1] Create main/ui/radio/RadioViewModel.kt (T027): `collectLatest` over the query with
  the 500 ms wait for a name and an immediate load for a filter; paging and `mergePage`; the
  session's connection; `StationPicker.pick`; the name in `SavedStateHandle`; all requests in
  `viewModelScope`. Filters are read from `RadioStore` (none stored ⇒ no filter, `MostVoted`);
  the first-visit rule and the sheets come in US4, the failures in US5.
- [ ] T036 [P] [US1] Create main/ui/radio/StationRow.kt: the row of contracts/radio-ui.md "A
  station row" (54 dp, tile 38 dp in the stream colours, one-line name ending in "…", second line
  in `textMuted`, spoken as one node), reused by Start Playback with its "Radio ·" prefix.
- [ ] T037 [US1] Create main/ui/radio/RadioScreen.kt: header with Back, the 52 dp search field
  (at most 100 characters, a typed `%` dropped, clear button, Search action only hides the
  keyboard), the list heading "STATIONS", six placeholders while loading the first page,
  "Loading more stations…", the session's stale banner, the session held with acquire / release.
  Chips, the order control, "Recent" and the failure states are added by US4, US3 and US5.
- [ ] T038 [US1] In main/ui/startplayback/ add `selectedStation`, `radio` and `openRadio` to
  `StartPlaybackUiState`; in `StartPlaybackViewModel` the radio rows, the exclusive selection, the
  picker and availability collection, the `station` saved-state key, the failure message (T025).
- [ ] T039 [US1] Create main/ui/startplayback/RadioRows.kt (section heading "Or a radio station",
  the "Find a radio station" row, the selected row with "Change" and a `MarqueeText` name, the
  selected-style rows) and update StartPlaybackScreen.kt and StartPlaybackText.kt: the section
  between the link field and "Or pick a source", `playLabelParts` and the three-part Play button
  (only the name shrinks and ends in "…") for stations and source names (T026). "Will stop",
  "already playing" and the starting state work as for any source.
- [ ] T040 [US1] In main/ui/nav/AppNavigation.kt add the `Destination.Radio` entry (no bottom bar,
  Back pops and changes nothing, a chosen station pops) and `onOpenRadio` from Start Playback,
  also when Start Playback has a fixed target ("Change source…"); in AppGraph add
  `radioViewModel(savedState)` and pass `RadioAvailability`, `StationPicker` and `RadioStore` to
  `startPlaybackViewModel`. Pass the phone's region as `region: () -> String` from
  `androidx.compose.ui.text.intl.Locale.current.region` (used by US4).

**Checkpoint**: US1 works on its own: build, all tests, quickstart §2 rows 1, 3–7.

---

## Phase 4: User Story 2 - A playing station looks like a station (Priority: P2)

**Goal**: a station that plays, started by any client, shows kind "Radio", "Live stream", Stop
without Pause and no technical address.

**Independent Test**: start a station from another client with `curl`; Rooms shows "Radio · Live
stream", Now Playing "Radio" with "Live streams can't be paused", and the source lists kind
"Radio" with the stream tile (quickstart §2 row 25). Scenario 4 ("already playing") is delivered
by T021 and T029.

### Tests for User Story 2 (write first, see them fail)

- [ ] T041 [P] [US2] In test/domain/SourceKindTest.kt add failing tests: a `radiobrowser://` address
  is `SourceKind.Radio` for an EPHEMERAL and a STATIC input, in any letter case, checked before the
  run-time-means-Link rule; `isLiveStream` is true for a non-pauseable station address and false
  for a pauseable one; `http(s)` and line-in rules unchanged; `kindLabel(Radio)` = "Radio".
- [ ] T042 [P] [US2] In test/domain/AddressDetailTest.kt and test/domain/SourceDetailTest.kt add
  failing tests: `addressDetail` and `sourceDetail` for `Radio` are null, so the Now Playing
  subtitle is "Radio" alone.
- [ ] T043 [P] [US2] In test/domain/SettingsBuilderTest.kt add a failing test that
  `RuntimeSourceRow.kind` is `Radio` for a station input and `Link` for other run-time sources; in
  test/data/KtorHubRepositorySnapshotTest.kt (with Fixtures.kt) add contract test 17 (an input
  with `uri: "radiobrowser://…"` maps to kind `Radio`, EPHEMERAL and STATIC).
- [ ] T044 [P] [US2] In test/domain/RoomsBuilderTest.kt, test/domain/NowPlayingBuilderTest.kt and
  test/ui/rooms/StatusTextTest.kt add failing tests that a non-pauseable station route has the
  status "Radio · Live stream", Stop without Pause, the line "Live streams can't be paused" and the
  title of the station's name.

### Implementation for User Story 2

- [ ] T045 [US2] In main/domain/Models.kt add `SourceKind.Radio`; in main/domain/SourceKind.kt
  make `inferSourceKind` return it first for a station address, extend `isLiveStream`, `kindLabel`,
  `addressDetail` and `sourceDetail`; fix every exhaustive `when` over `SourceKind` the compiler
  reports.
- [ ] T046 [P] [US2] In main/ui/rooms/KindStyle.kt map `Radio` to the stream colours and icon.
- [ ] T047 [US2] Add `kind` to `RuntimeSourceRow` in main/domain/SettingsBuilder.kt and make
  `RuntimeSourceSettingRow` in main/ui/settings/SettingRows.kt draw that kind's tile.
- [ ] T048 [US2] Run `./gradlew :composeApp:testAndroidHostTest` and confirm the Rooms status, Now
  Playing and source lists need no further change (they derive from these functions); fix any
  place that still shows a station as "Link" or draws its address.

**Checkpoint**: US1 and US2 both work; a station started by `curl` reads as a station.

---

## Phase 5: User Story 3 - Play a recent station again (Priority: P2)

**Goal**: stations started from this phone and listened to for 30 seconds are kept (ten), shown
under "Recent" on the radio screen and the first three in Start Playback.

**Independent Test**: start two stations from the phone, let each play more than 30 s, stop
them: both are under "Recent", the later first; a station stopped after a few seconds is not
(quickstart §2 rows 8–13).

### Tests for User Story 3 (write first, see them fail)

- [ ] T049 [P] [US3] In test/domain/RadioTest.kt add failing tests for `judgeRecent(pending,
  snapshot, nowMillis)`, every row of research R8: no live route with that id → `Drop`; input
  missing or another address → `Drop`; status `Failed` → `Drop`; `Active` and ≥ 30,000 ms since the
  start → `Recent`; `Starting` or younger than 30 s → `Wait`; a transfer keeps the route id and
  the start time.
- [ ] T050 [P] [US3] Create test/ui/session/RecentStationTrackerTest.kt with failing tests, fake
  session and virtual time: `track` stores a pending entry (also in the store); a refresh older
  than 30 s adds the station first and drops the entry; a stop within 30 s adds nothing; a failed
  start never calls `track`; a pending entry made in this process is judged only by a refresh that
  started after the start's answer (the fence) and a restored one by any refresh; a pending entry
  restored from the store after the process was ended is judged on the first refresh; two stations
  in two rooms are judged by their own routes; a removed station can qualify again; the list stays
  at ten with each `uuid` once; the tracker never polls.
- [ ] T051 [P] [US3] In test/ui/session/PlaybackStarterTest.kt add failing tests: a successful
  station start (also a recovered one, also when Start Playback was closed) calls
  `tracker.track(station, routeId, fence)`; a source or a link start does not.
- [ ] T052 [P] [US3] In test/ui/startplayback/StartPlaybackViewModelTest.kt add failing tests: up to
  three recent rows, most recent first, none when radio is not offered whatever the phone
  remembers; selecting a recent row selects the station without opening the radio screen; a
  selection that is one of the three marks that row once; removing a station from "Recent" frees a
  place for the next one and leaves a selection or playback unaffected.
- [ ] T053 [P] [US3] In test/ui/radio/RadioViewModelTest.kt add failing tests: "Recent" (all ten,
  unfiltered) while the cleaned name is empty, whatever filters are chosen, hidden while a name is
  typed and when the list is empty; removal is immediate, without confirmation; a removed station
  disappears from both screens.

### Implementation for User Story 3

- [ ] T054 [US3] In main/domain/Radio.kt add `PendingStation` use, `judgeRecent`,
  `RECENT_AFTER_MILLIS = 30_000`, `RECENT_LIMIT = 10`, `RECENT_IN_START_PLAYBACK = 3` (T049).
- [ ] T055 [US3] Create main/ui/session/RecentStationTracker.kt (T050): `track(station, routeId,
  fence)`, judging on every successful session refresh, pending entries kept in `RadioStore`,
  read once at start-up. Create it in AppGraph beside `PlaybackStarter`.
- [ ] T056 [US3] In main/ui/session/PlaybackStarter.kt call `tracker.track` when a station start
  succeeds (T051).
- [ ] T057 [US3] In `RadioViewModel` expose `recent` and `removeRecent(uuid)`; in
  main/ui/radio/RadioScreen.kt draw the "RECENT" heading and up to ten station rows, each with a
  44 dp ✕ spoken "Remove <name> from Recent", above the list (T053).
- [ ] T058 [US3] In `StartPlaybackViewModel` and main/ui/startplayback/RadioRows.kt list the three
  recent rows directly under "Find a radio station" (transparent, a radio dot at the end, one
  radio group with the sources, no remove action) and mark a selected one (T052).

**Checkpoint**: US1–US3 work; quickstart §2 rows 8–13.

---

## Phase 6: User Story 4 - Narrow the list by country, tag and language (Priority: P2)

**Goal**: chips for country, tag and language, an order control, sheets with search, first-visit
country, and filters kept on the phone.

**Independent Test**: choose the country Germany and the tag "jazz": German jazz stations, most
voted first; removing the tag shows all German stations again (quickstart §2 rows 14–21).

### Tests for User Story 4 (write first, see them fail)

- [ ] T059 [P] [US4] In test/ui/radio/RadioViewModelTest.kt add failing tests: first visit (no
  stored filters) loads the countries, matches the phone's region ignoring case, stores that
  country or none, then loads stations; if the country list fails nothing is stored and the
  catalogue message with Retry shows; afterwards the stored filters and order open the screen and
  the typed name is not kept; each change is stored; choosing a value, removing a chip and a change
  of order reload the first page at once; several filters and a name are sent together; "Clear
  filters" removes country, tag and language, keeps order and name, and is offered only when one is
  chosen; a stored value the catalogue no longer lists is still applied and shown.
- [ ] T060 [P] [US4] In test/ui/radio/RadioViewModelTest.kt add failing sheet tests: Country and
  Language lists load once when the sheet first opens, are kept while the screen lives, and the
  field narrows them on the phone; the Tag sheet pages 50 at a time, asks the hub for `name` 0.5 s
  after the last change with `%` and `/` removed, shows a chosen tag as the first row and leaves it
  out of its own place; the Order sheet has three rows ("Most voted", "Trending", "Name A–Z");
  opening with a value chosen reports that row as the one to show; choosing closes the sheet,
  applies and stores; Cancel, scrim and Back change nothing; a sheet that cannot load shows the
  catalogue message inside itself and leaves the list untouched.
- [ ] T061 [P] [US4] In test/ui/radio/RadioTextTest.kt add failing tests: the chips' spoken
  removals ("Remove the country Ukraine", "Remove the tag jazz", "Remove the language german"),
  "Order: Most voted. Change the order", the list heading ("STATIONS IN <COUNTRY>" / "STATIONS"),
  the sheet titles, field labels ("Search countries", "Search tags", "Search languages") and the
  Order rows' descriptions.

### Implementation for User Story 4

- [ ] T062 [US4] In `RadioViewModel` and RadioUiState.kt add the filters, the first-visit rule,
  `Clear filters`, the four sheet kinds with `FilterRow(name, count, detail, selected)` (a country
  row also carries its code), the tag paging and the 500 ms tag search; persist through
  `RadioStore.saveFilters` (T059, T060). The region comes from the `region` function of T040.
- [ ] T063 [US4] Create main/ui/radio/FilterSheet.kt: a `ModalBottomSheet` in the style of the Move
  to room sheet (handle, radius 28, scrim), Sora 22 sp title, the 48 dp field (not for Order, at
  most 100 characters, `%` and `/` dropped in the tag field), rows of at least 52 dp with the name
  on one line ending in "…" and the count at the right never shrinking (thousands grouped, chosen
  row in `selected` with the count in `accent`, role radio button), the chosen row scrolled into
  view on opening, "Loading more tags…", the Order sheet without field or counts, "Cancel" at the
  bottom.
- [ ] T064 [US4] In RadioScreen.kt add the chips row (Country, Tag, Language: a `surface` pill with
  a chevron when not chosen; when chosen `selected` with the value at most 120 dp wide ending in
  "…", an 40 × 44 dp ✕ and the value opening the sheet; the row scrolls sideways if needed), the
  list heading "STATIONS IN <COUNTRY>" and the 44 dp order control with the sort icon at its
  right, always visible with the list, and the "No stations match" state with "Clear filters".

**Checkpoint**: US1–US4 work; quickstart §2 rows 14–21.

---

## Phase 7: User Story 5 - Radio is missing, slow or failing, and the app says so plainly (Priority: P3)

**Goal**: no radio where the hub has none, plain words and a way forward when the catalogue is
slow or down, and the existing connection state when the hub is unreachable.

**Independent Test**: on a hub without the extension Start Playback has no radio row and works as
before; with the hub's internet cut the radio screen shows "The station catalogue isn't
answering" with Retry (quickstart §2 rows 26–31).

### Tests for User Story 5 (write first, see them fail)

- [ ] T065 [P] [US5] In test/ui/radio/RadioViewModelTest.kt add failing tests: a `Catalogue`
  failure keeps the listed stations and sets `problem`; Retry repeats the failed request (first
  page or next page); `HubUnreachable` asks the session for a refresh, shows no catalogue message
  while the session is unreachable and repeats the request by itself when the hub answers again
  (if the session is live all the same, it is shown as `Catalogue`); `Gone` calls
  `RadioAvailability.markGone()` and `StationPicker.reportGone()` and sets `exit = Gone`;
  `limitReached` at 10,000 with the note; "No stations match" for an empty loaded list; no state
  change and no message after the view model is cleared mid-request.
- [ ] T066 [P] [US5] In test/ui/startplayback/StartPlaybackViewModelTest.kt add failing tests:
  `Unknown` and `NotOffered` show nothing of radio and no error (a hub without radio is exactly as
  before, recent stations not listed); availability is asked again after a successful session
  refresh while still `Unknown`; a picker `gone` shows "Radio is no longer available on this hub"
  once, drops the radio rows and deselects a selected station; a start whose screen was closed
  reports to Rooms through `AppMessages` as today.

### Implementation for User Story 5

- [ ] T067 [US5] In `RadioViewModel` implement the three failure paths of T065 and the retry of a
  failed first or next page; in `StartPlaybackViewModel` the `Unknown` re-ask and the `gone`
  handling of T066; in `RadioAvailability` the refresh on each visible opening.
- [ ] T068 [US5] In RadioScreen.kt and FilterSheet.kt draw the catalogue card (a `surface` card
  above the list, role alert, info icon, "The station catalogue isn't answering" in `warningText`,
  "Retry" in `accent`; rows already listed stay under it, inside the sheet for a sheet), the
  placeholder and "Loading more…" announcements (status), "No stations match" (Sora 17 sp, "Try
  another name or fewer filters.") and the limit note "Showing the first 10,000 stations. Narrow
  the search to see others.". The radio screen closes on `exit = Gone` and on `Chosen`.

**Checkpoint**: all five stories work; quickstart §2 all rows except those needing the user.

---

## Phase 8: Polish & Cross-Cutting Concerns

- [ ] T069 [P] In test/ui/theme/ContrastTest.kt add the new pairs of contracts/radio-ui.md
  "Accessibility": `textSoft` on `surface`, `accent` on `selectedBg`, `warningText` on `surface`,
  `textMuted` on `surface`; fix any token use that fails.
- [ ] T070 Run `./gradlew :androidApp:assembleDebug`, `:composeApp:testAndroidHostTest`,
  `:composeApp:allTests` and `:composeApp:check` with `ANDROID_HOME="$LOCALAPPDATA/Android/Sdk"`,
  reading each Gradle exit code (`… > log; echo $?`); all green, and `verifyLayering` finds no
  generated radio type under `ui/` or `domain/`. Walk quickstart §1.
- [ ] T071 Create specs/009-radio-stations/verification.md in the format of 008's: build under test
  (CI build number and commit), the commands of T070 with dates and counts, and an empty table of
  quickstart §2 rows 1–31 to fill on the device.
- [ ] T072 [P] Add a `## [0.9.0-alpha] - Unreleased` entry to CHANGELOG.md above 0.8.1-alpha
  (user-facing: find and play radio stations, recent stations, filters, a station shown as
  "Radio · Live stream"), linking `specs/009-radio-stations` and "pull request to follow".
- [ ] T073 [P] Update AGENTS.md: the layout (`ui/radio/`, the three session objects), "Endpoints
  per screen" (radio requests; the extensions request also when Start Playback opens), "Hub gaps"
  (no failure reason for a failed station start, HLS unknown), "Agreed details" (station kind
  "Radio", the three-part Play label), and the API paragraph (radio used since 0.9.0-alpha).
- [ ] T074 [P] Add the entry "0.1.34: radio is used since 0.9.0-alpha" to docs/hub-api-history.md.
- [ ] T075 [P] Delete docs/backlog/radio-stations.md and its row in docs/backlog/INDEX.md; keep
  docs/backlog/start-failure-above-play.md and its row.
- [ ] T076 On a local session with the installed build, run quickstart §2 as AGENTS.md "On-device
  verification" describes (POCO, volumes noted and set to 1, Do Not Disturb, Sonora in front before
  every input, test routes stopped before volumes are restored; say before row 3 that the run
  replaces what plays in `A` and `B`; rows marked **User** are done by the user or marked `n/a`).
  Record each row's evidence in verification.md; issues under "Issues found", hub-side ones as
  observations.
- [ ] T077 Update docs/testing/full-app-test.md for this feature: a new section for radio with rows
  for quickstart §2, the fixture extended (the `radio` extension `ACTIVE`, a known-good station),
  rows that read a source's kind or the Start Playback layout updated, the header to MOB-009 /
  0.9.0-alpha / API 0.1.34 ("Keeping this script current" in the script). Last task.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (T001–T002)**: first. T002 before T004 and T013.
- **Foundational (T003–T020)**: after Setup; blocks every story. Tests T003–T009 first (all [P]),
  then T010 → T011/T012 → T013 → T014; T015 after T005; T016–T017 after T006 (T017 before T018);
  T018, T019 after T017; T020 after T009.
- **US1 (T021–T040)**: after Foundational. The MVP.
- **US2 (T041–T048)**: after Foundational; independent of US1 except T029's `sameStation` and T048's
  full run. Plan order may put US2 first (it needs no radio request).
- **US3 (T049–T058)**: after US1 (needs `PlaybackStarter`'s station start, the radio screen and
  Start Playback's rows).
- **US4 (T059–T064)**: after US1; shares `RadioViewModel`, `RadioScreen.kt` and `FilterSheet.kt`
  with US3 and US5, so run them one after another or merge carefully.
- **US5 (T065–T068)**: after US1 and US4 (its sheet card uses `FilterSheet.kt`).
- **Polish (T069–T077)**: after the stories. T076 needs an installed build; T077 is last.

### Within Each User Story

- Tests first, seen failing, then the implementation.
- Domain before data before view model before screen before navigation.

### Parallel Opportunities

- T003–T009 (different test files) at the start of Foundational; then T011, T012, T018, T019.
- T021–T028 (different test files) at the start of US1; then T033, T034, T036.
- T041–T044, T049–T053, T059–T061, T065–T066 are the test batches of their stories.
- T072–T075 alongside T069.
- T025, T038, T052, T058, T066 share StartPlaybackViewModel files and T027, T035, T053, T059,
  T060, T065 share RadioViewModel files: do those one after the other.

## Parallel Example: Foundational tests

```text
Task: "Pure rules in composeApp/src/commonTest/kotlin/sonora/multiroom/mobile/domain/RadioTest.kt"
Task: "Contract tests in composeApp/src/commonTest/kotlin/sonora/multiroom/mobile/data/KtorHubRepositoryRadioTest.kt"
Task: "radioFailure in composeApp/src/commonTest/kotlin/sonora/multiroom/mobile/ui/MessagesTest.kt"
Task: "RadioStore in composeApp/src/commonTest/kotlin/sonora/multiroom/mobile/data/RadioStoreTest.kt"
Task: "RadioAvailability in composeApp/src/commonTest/kotlin/sonora/multiroom/mobile/ui/session/RadioAvailabilityTest.kt"
```

---

## Implementation Strategy

### MVP First

1. T001–T020 (Setup and Foundational).
2. US1 (T021–T040): search by name, choose, Play.
3. **Stop and validate**: build, all tests, quickstart §2 rows 1, 3–7.

### Incremental Delivery

1. Setup + Foundational.
2. US1 (MVP), then US2 (a station reads as a station; cheap and also helps stations started by
   other clients).
3. US3 (Recent), US4 (filters), US5 (failure states).
4. Polish: verification, changelog, AGENTS.md, API history, backlog, the device run, and the full
   app test last.

---

## Notes

- [P] = different files, no dependency on unfinished tasks.
- Verify each test fails before implementing (Constitution IV).
- If a task needs a decision the spec does not cover, stop and ask.
