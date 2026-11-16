# Research: Find and Play Radio Stations

**Feature**: [spec.md](spec.md) | **Date**: 2026-11-16

Facts below were read from the code on branch `009-radio-stations` (app 0.8.1-alpha), from
`api/openapi.json` (API 0.1.34), from the canvas copies `design/screens/Radio.dc.html`,
`RadioFilterSheet.dc.html` and `StartPlaybackRadio.dc.html`, and from read-only requests to the
home hub on 2026-11-16 (`radio` 0.2.0, `ACTIVE`). The backlog item
[radio-stations.md](../../docs/backlog/radio-stations.md) holds the earlier live checks; they are
not repeated here.

## R1. The generated client

**Decision**: add `Radio` to the generator's tag filter in `composeApp/build.gradle.kts`
(`"tag:Routes|Outputs|Inputs|Groups|Master Mute|Playback|Extensions|Radio"`). Nothing is
hand-written for the wire format.

**Checked on 2026-11-16** by generating with that filter (OpenAPI Generator 7.14.0) and reverting:

- `RadioApi` appears with `searchRadioStations(name, tag, countryCode, language, codec, bitrateMin,
  order, direction, limit, offset)`, `listRadioCountries()`, `listRadioLanguages()`,
  `listRadioTags(name, limit, offset)` and `getRadioStation(uuid)`. Every optional parameter is
  nullable and a null one is not sent, so the hub never sees an empty or doubled parameter.
- `order` is the nested enum `RadioApi.OrderSearchRadioStations` (`name`, `votes`, `clickCount`,
  `bitrate`). Its default argument is `votes`, so a call without it still sends `order=votes`,
  which the hub accepts.
- Models `RadioStation`, `RadioStationPage`, `RadioCountry`, `RadioLanguage`, `RadioTag`,
  `RadioTagPage`, `RadioProblem`. `uuid` is a `String` (not a UUID type). `name`, `favicon`,
  `homepage`, `countryCode`, `codec`, `bitrate`, `nextOffset` and `RadioLanguage.code` are
  nullable with a null default. Station counts are `Long`.
- `languages`, `tags`, `hls`, `lastCheckOk`, `votes`, `clickCount`, `uuid` and `uri` are
  `@Required`: a station without one of them fails the whole page. The contract marks them
  required and the live hub sends them, so this is accepted; such a page maps to
  `HubError.Unexpected` and the screen shows the catalogue message (R9).
- `getRadioStation` is generated and never called (FR-035); `RadioProblem` is generated and not
  used (R9).

**Rationale**: Constitution I (generated at build time, never hand-edited). The filter is the only
build change, and the existing `verifyLayering` task already keeps the new wire types out of `ui/`
and `domain/`.

**Alternatives considered**: a hand-written Ktor call for the four requests (rejected: against
Constitution I, and the generator copes).

## R2. Repository methods and the radio timeout

**Decision**: four methods on the existing `HubRepository`
([contract](contracts/hub-repository.md)): `searchStations(query, offset)`, `radioCountries()`,
`radioLanguages()`, `radioTags(name, offset)`. They return `HubResult` like every other call. The
`RadioApi` instance uses its own client configuration: request and socket timeout
`RADIO_TIMEOUT_MILLIS = 6000`, connect timeout the usual 3 s (the same pattern as `PlaybackApi`'s
30 s client in `KtorHubRepository`).

Page size is `RADIO_PAGE_SIZE = 50` for stations and tags. No `direction`, `codec` or `bitrateMin`
is ever sent.

**Rationale**:
- `HubRepository` is "the only boundary between the app and the hub"; `HubSession.repository`
  already follows the hub address, so a radio answer from the previous hub is discarded with the
  check the app uses elsewhere (`session.repository !== repo`).
- The hub bounds a radio request at 5 s and then answers 503 itself. With the app's ordinary 3 s
  timeout a slow catalogue would be reported as "hub unreachable" (FR-031). 6 s is the smallest
  value safely above the hub's bound.
- 50 rows are about four screens of 54 dp rows; a page takes 0.3–1.2 s (measured: offset 9950 took
  1.2 s, the first page 0.3 s). 10,000 is a multiple of 50, so the last page ends exactly at the
  catalogue's limit (R6).

**Alternatives considered**: a separate `RadioRepository` created beside `HubRepository` (a second
factory and a second "follow the address" path for four read-only calls); the default page of 20
(more round trips while scrolling); 100 (slower first paint for no benefit).

## R3. Knowing whether radio is offered (FR-001, FR-002, FR-033)

**Decision**: one app-scoped object, `RadioAvailability` (`ui/session/`), holds
`state: StateFlow<RadioOffer>` with `Unknown`, `Offered`, `NotOffered`.

- `refresh()` calls `HubRepository.extensions()`; an answer for a repository that is no longer the
  session's is dropped. `Offered` when the inventory has an extension whose id is `radio` (letter
  case ignored) with status `Active`; otherwise `NotOffered`. A failed request leaves the state as
  it was. The rule is one pure function, `radioOffered(inventory)`.
- The state goes back to `Unknown` when the hub address changes.
- `markGone()` sets `NotOffered` (called when a radio request shows the extension is gone, R9).
- Start Playback's view model calls `refresh()` each time the screen becomes visible, and again
  after each successful session refresh while the state is still `Unknown` (the hub was unreachable
  when the screen opened). It shows the radio row and the recent stations only while the state is
  `Offered`.

**Rationale**:
- The row is absent until the hub has answered and is never drawn disabled (`Unknown` and
  `NotOffered` draw the same: nothing).
- The hub's inventory is fixed at its start-up, so asking on every 2.5 s poll would add a request
  per poll for a value that changes only on a hub restart. One request per opening of Start
  Playback is enough, and it gives "appears the next time Start Playback is opened".
- Keeping the last answer across openings means the row does not pop in late on the second visit;
  the fresh answer corrects it within a round trip.

**Alternatives considered**: adding the extensions request to `HubRepository.snapshot()` (a sixth
request on every poll, and a failing extensions call would fail the whole refresh); asking only
once per app start (a hub that gains or loses the extension would be noticed only after an app
restart).

## R4. A station as a kind of source (FR-025 to FR-028)

Checked live (backlog item): a played station is an input with `source = EPHEMERAL`,
`autoRemove`, not pauseable, named after the station, whose `uri` stays
`radiobrowser://<uuid>` (lower case).

**Decision**, all in `domain/SourceKind.kt`, the file that already holds the kind rules:

- `isStationAddress(uri)`: true when the trimmed address starts with `radiobrowser://`, letter
  case ignored. `sameStation(a, b)`: both are station addresses and equal ignoring case.
- New `SourceKind.Radio`. `inferSourceKind` checks `isStationAddress` **first**, before the
  "added at run time means Link" rule, so a station is `Radio` whatever its origin.
- `isLiveStream(pauseable, uri)`: not pauseable and the address is `http(s)` **or** a station
  address.
- `kindLabel(Radio)` = "Radio". `addressDetail(Radio, …)` and `sourceDetail(Radio, …)` = null, so
  Now Playing's subtitle is "Radio" alone.
- `forKind(Radio)` = the stream colours, `iconForKind(Radio)` = the stream icon (`ui/rooms/KindStyle.kt`).
- Settings' runtime source row hard-codes the Link tile (`RuntimeSourceSettingRow` in
  `ui/settings/SettingRows.kt`). `RuntimeSourceRow` gains `kind`, and the row draws that kind's
  tile, so a playing station shows the stream tile there (User Story 2, scenario 3).

Nothing else needs touching: `cardStatus`, Rooms' status line ("Radio · Live stream") and Now
Playing's "Live streams can't be paused" all derive from these functions.

**Rationale**: FR-028 asks for one place. The scheme is the only thing that marks a station; the
hub has no kind field (a known hub gap).

**Alternatives considered**: keeping `Link` and special-casing texts in each screen (three
screens would each re-derive it, against Constitution II); the label "Stream" (decision 6 of the
backlog item chose "Radio").

## R5. Starting a station (FR-015 to FR-020)

**Decision**:

- `StartWhat.Station(station: Station)` joins `Source` and `Link` (`domain/StartRequest.kt`).
- The request is the existing `HubRepository.playLink(station.uri, target)`: `POST /api/v2/play`
  with `uri`, `targetId`, `targetType` and nothing else. No `displayName` is sent, so the hub
  names the input after the station. The 30 s client already covers the hub's 10 s resolve
  timeout. The start does not go through `checkLink`, which accepts only `http(s)`.
- `PlaybackStarter` gains `StartKind.Station`. `StartNames.source` carries the station's display
  name ("Unnamed station" when it has none).
- Failure mapping (`startFailure` in `ui/Messages.kt`), for `StartKind.Station`: a `reason`
  refusal, a 404 and an unreachable hub keep their existing meaning (the target is turned off,
  the room is full, the target is gone, "Couldn't reach the hub"); every other hub answer (400,
  422, 502, 503, anything else) is the new `StartFailure.StationFailed(name)`, worded "Couldn't
  start <station>". No cause is stated.
- "Already playing" (`StartConsequence.of`): for a station, `alreadyThere` is true when a live
  route addressed to the chosen target plays an input whose `uri` is the same station
  (`sameStation`). Each start makes a new input, so the input id cannot be compared.
- `effectiveJoinMode(Station)` = `Replace`, as for a link.
- Recovery after a lost answer (`PlaybackStarter.findStarted`): a station is found the way a link
  is, by a runtime input whose `uri` matches (`sameLink` already lower-cases the part before the
  first `/`, which for `radiobrowser://<uuid>` is the whole address).
- The Play button's label is `PlayLabel.PlaySource(name, target)`; the starting state is the
  existing one.

**Where the failure is shown** (decided by the owner on 2026-11-16): in the snackbar, as the spec
says ("where link failures are shown today") and as every other start failure is shown. The
station stays selected and the screen stays open. A start whose screen was closed reports to
Rooms as today (`AppMessages`).

The canvas (`StartPlaybackRadio.dc.html`, property `startFailed`) draws the message as a line
above Play instead. That state is **not built** in this feature: it would make a failed station
start look different from a failed link start on the same screen. Showing every start failure as
a line above Play is the backlog item
[start-failure-above-play.md](../../docs/backlog/start-failure-above-play.md); until it ships,
the canvas's `startFailed` state is ahead of the app, a departure from the design that this
paragraph records (Constitution VI).

**Rationale**: one way of showing start failures on one screen; no new pattern introduced by a
feature about radio.

**Alternatives considered**: a new repository method `playStation` (the same HTTP call under a
second name); the canvas's line above Play for a station failure only (two ways of showing the
same kind of failure on one screen).

## R6. The radio screen's list (FR-003, FR-004, FR-005, FR-012)

**Decision**: a `RadioViewModel` (`ui/radio/`) owns the list. All requests run in its
`viewModelScope`, so leaving the screen cancels them and no message can appear later (FR-034).

- **Query**: `StationQuery(name, countryCode, tag, language, order)`. `stationName(text)` (pure,
  `domain/Radio.kt`) removes every `%`, trims, cuts to 100 characters and returns null when
  nothing is left. The search field itself also drops `%` and stops at 100 characters, as the
  canvas does; the pure function is the guarantee that the hub never sees them.
- **Debounce**: a change of the typed name starts a 500 ms wait; a change of a filter or the order
  loads at once. Either cancels the load in flight (`collectLatest` on the query), so an answer
  for earlier text can never replace a later one. A name that cleans to the same query as before
  (a trailing space) loads nothing.
- **Paging**: the first page replaces the list; the next page is requested when the list is
  scrolled to within 10 rows of its end and no load is running. Stations whose `uuid` is already
  listed are dropped. A page that adds no new station but has a next offset is followed at once
  by the next request, so the list cannot stall on a page of duplicates.
- **End**: no further page when `nextOffset` is null, or when `nextOffset >= 10000`. The second
  case sets `limitReached`, which shows "Showing the first 10,000 stations. Narrow the search to
  see others." Checked live: `offset=9950&limit=50` answers 50 stations and `nextOffset: 10000`,
  and `offset=10000` would answer 50 more, so the app stops by itself to keep the promise of
  10,000.
- **A failed next page** keeps the listed stations and shows the catalogue message with Retry
  above them (FR-030); Retry repeats the failed request, first page or next.
- **Heading**: "Stations in <country>" when a country is chosen, otherwise "Stations", as the
  canvas shows it ("Stations in Ukraine"). The order control beside it names the order.
- **State kept**: the view model outlives rotation and the app going to the background (it lives
  in the navigation entry's store). The typed name is also in `SavedStateHandle`, so a restored
  process shows the same name and loads its list again.

**Rationale**: these are the rules FR-004 and FR-012 state; `collectLatest` gives "latest wins"
without a hand-kept generation counter.

**Alternatives considered**: a paging library (a new dependency for one list; Constitution VII);
loading on a "Load more" button (the spec asks for loading on scroll).

## R7. Filters and their sheets (FR-006 to FR-011)

**Decision**:

- `RadioFilters(country: Country?, tag: String?, language: String?, order: StationOrder)` is kept
  on the phone (R10) and written on every change. The country is stored with its code and name, so
  its chip can be drawn before any list has loaded, and a value the catalogue no longer lists is
  still shown and applied.
- **First visit** (no filters stored yet): the view model loads the country list first, matches
  the phone's region code against `Country.code` ignoring case, stores the result (that country or
  none) and then loads the stations. If the country list cannot be loaded, the screen shows the
  catalogue message with Retry and stores nothing, so the next opening is still a first visit.
- **The phone's region**: `androidx.compose.ui.text.intl.Locale.current.region`, read in
  `AppGraph` and passed to the view model as a plain `() -> String` (empty when unknown). It is
  Compose Multiplatform common API, so no `expect`/`actual` and no platform code is added. No
  location permission is involved.
- **Country and Language sheets**: the whole list is loaded when the sheet first opens and kept by
  the view model while the radio screen lives. The field narrows it on the phone (the name
  contains the typed text, letter case ignored). Opening with a value chosen scrolls that row into
  view.
- **Tag sheet**: paged from the hub (50 per page, next page on scroll, "Loading more tags…"). The
  field sends `name` after the same 500 ms wait; `tagName(text)` removes `%` and `/`, trims and
  cuts to 100 characters. A chosen tag is shown as the first row, selected, and is left out of its
  place further down, since it may be thousands of rows away; its count shows once a loaded page
  contains it.
- **Order**: the fourth kind of the same sheet, without a field: "Most voted" ("The catalogue's
  all-time favourites"), "Trending" ("Most plays in the last 24 hours"), "Name A–Z". They map to
  `order=votes`, `clickCount` and `name`; the hub's default direction for each is the wanted one.
- **Names** are shown as the catalogue gives them; nothing is translated or re-cased (FR-008).
  Counts are grouped in thousands with a comma ("6,965"), by one pure function.
- **A sheet that cannot load** shows the catalogue message with Retry inside the sheet; the
  station list behind it is not touched.
- **"Clear filters"** (the "No stations match" state) removes country, tag and language, keeps the
  order and the typed name, and is offered only when at least one of the three is chosen.

**Rationale**: 225 countries and 585 languages are small enough to filter on the phone and the hub
remembers them for 6 hours; the tag list has more than ten thousand entries and the hub pages and
searches it.

**Alternatives considered**: country names localised from the code (the spec's clarification chose
the catalogue's English names); a platform `expect fun` for the region (not needed).

## R8. Recent stations and the 30-second rule (FR-021 to FR-024a)

**Decision**: an app-scoped `RecentStationTracker` (`ui/session/`), created in `AppGraph` beside
`PlaybackStarter`.

- When a station start succeeds (also when the answer was recovered from a snapshot, and also when
  Start Playback was already closed), `PlaybackStarter` calls
  `tracker.track(station, routeId, fence)`. The tracker stores a **pending** entry: the station,
  the route id and the wall-clock time of that moment. Pending entries are kept on the phone too
  (R10), so one survives the app's process being ended in the background.
- On every successful session refresh the tracker judges each pending entry with one pure
  function, `judgeRecent(pending, snapshot, nowMillis)`:

  | The snapshot shows | Verdict |
  |---|---|
  | no live route with that id | drop |
  | the route's input is missing, or its address is not the same station | drop |
  | the route's status is `Failed` | drop |
  | status `Active` and at least 30,000 ms since the start | **recent**: add, then drop the pending entry |
  | anything else (still `Starting`, or younger than 30 s) | wait |

- A pending entry made in this process is judged only by a refresh that started after the start's
  answer (the fence rule of `HubSession`, with the `startedSeq` the starter already captures). One
  restored from storage has no fence: any refresh judges it.
- Adding puts the station first, removes an older entry with the same `uuid`, and cuts the list to
  ten (`addRecent`, pure).
- The tracker never polls. While the app is in the background nothing is judged; the first
  refresh on return does it, which is exactly FR-021a.

Why each spec case holds:

| Case | Why |
|---|---|
| stopped, replaced or ended within 30 s | the route is gone from the next snapshot: drop |
| a failed start | `track` is never called |
| left the app before 30 s, still playing on return | pending survived; first refresh: `Active`, older than 30 s |
| moved to another room | a transfer keeps the route id (API 0.1.22) and the start time is not touched |
| several stations sampled in a row | each start replaces the route before it: only the last survives |
| two stations in two rooms | two pending entries, each with its own route id |
| started again and stopped sooner | nothing is added, so its place does not change |

- **Removal** (`RadioStore.removeRecent(uuid)`) only edits the stored list. It does not touch
  pending entries, a selection or a playback, so a removed station that is playing can still
  qualify again later, as FR-024a asks.
- **What a recent station holds**: what its row shows (`uuid`, `uri`, name, country code, codec,
  bitrate), as known when it was started. It is never refreshed from the catalogue and is selected
  without a lookup (FR-023).
- **Where they show**: all ten on the radio screen while the cleaned name is empty, unfiltered;
  the first three in Start Playback while radio is offered.

**Rationale**: the hub is the only authority on what plays (Constitution II), and FR-021a asks for
the judgement "by what the hub reports". The time is wall-clock because it must survive the
process; a clock change during those 30 seconds can at worst let one station in early or late.

**Alternatives considered**: a 30 s timer in the starter (wrong while the app is in the background,
and blind to a playback stopped from another client); counting from the route's first `Active`
snapshot (the start's answer is the simpler moment and differs by at most one poll).

## R9. Radio errors in plain words (FR-029 to FR-034)

Checked live on 2026-11-16:

| Request | Answer |
|---|---|
| `GET /api/radio/stations?name=%25` | 400 `application/problem+json`, `type` `urn:multiroom:radio:error:invalid-parameter`, `parameter: "name"` |
| `GET /api/radio/nope` | 404 `application/problem+json`, `type` `urn:multiroom:radio:error:not-found` |
| `GET /api/nothing/here` (what a radio path answers without the extension) | 404 `application/json`, no `type` |

**Decision**: the repository keeps returning `HubError`. The existing `rejection()` in
`data/HttpClients.kt` already reads `type` from an RFC 7807 body through `ErrorResponse`, whose
fields are all optional, so a radio problem arrives as `HubError.Rejected(status, problemType)`
with the URN. One pure function in `ui/Messages.kt` classifies it:

```text
radioFailure(error: HubError): RadioFailure
  Unreachable                                              → HubUnreachable
  Rejected, status 404, type not starting with
    "urn:multiroom:radio:"                                 → Gone
  everything else (502, 503, 500, 400, an unknown radio
    type, Unexpected)                                      → Catalogue
```

| `RadioFailure` | What the radio screen does |
|---|---|
| `Catalogue` | "The station catalogue isn't answering" with Retry, above the rows already listed (or inside the sheet that asked) |
| `HubUnreachable` | asks the session for a refresh. While the session says the hub is unreachable, the app's usual stale banner shows and no catalogue message; when the hub answers again, the failed request is repeated by itself. If the session is live all the same, it is shown as `Catalogue` |
| `Gone` | `RadioAvailability.markGone()`, then the screen closes; Start Playback shows "Radio is no longer available on this hub" once, drops its radio rows and deselects a selected station |

A 400 can only mean an app bug (R6 and R7 clean every value); it is shown as `Catalogue`, never
as "invalid parameter" (SC-007). The hub's `title` and `detail` never leave `rejection()`.

**To confirm in a contract test**: that Ktor's `ContentNegotiation` decodes a body sent as
`application/problem+json` into `ErrorResponse` (its JSON matcher accepts `application/*+json`).
If the test shows it does not, `rejection()` reads the body as text and decodes it with `HubJson`;
the mapping above does not change.

**Rationale**: one error type across the repository, and the copy stays in the one file that holds
all error wording.

## R10. What is kept on the phone

**Decision**: a `RadioStore` interface (`data/RadioStore.kt`) with a DataStore implementation and
an in-memory one for tests.

| Key | Value | Limit |
|---|---|---|
| `radio_recent` | JSON array of stations, most recent first | 10 |
| `radio_filters` | JSON object: country (code, name), tag, language, order; absent before the first visit | |
| `radio_pending` | JSON array of pending entries: station, route id, start time (epoch ms) | pruned by every judgement |

- The app keeps one preferences file and DataStore allows one instance per file, so `AppGraph`
  creates the `DataStore<Preferences>` once and hands it to both `DataStoreHubAddressStore` (whose
  constructor changes from a path to the DataStore) and `DataStoreRadioStore`. `SonoraApplication`
  and the file name do not change.
- JSON is written with kotlinx.serialization through private `@Serializable` classes in `data/`,
  with `ignoreUnknownKeys`. A value that cannot be read counts as absent (empty list, first
  visit); it never crashes the app. An unknown `order` reads as "Most voted".
- `encodeStation` / `decodeStation` from the same file also serve Start Playback's saved state
  (the selected station in `SavedStateHandle`).
- The three values belong to the phone, not to a hub address: switching hubs keeps them (spec
  Assumptions).

**Rationale**: no new dependency; DataStore Preferences and kotlinx.serialization are already in
the build. Three small values do not justify a database.

**Alternatives considered**: a second preferences file (a second path to derive in common code for
no gain); typed keys per field (ten stations do not fit flat keys).

## R11. Navigation and handing the station back (FR-015)

**Decision**:

- New `Destination.Radio` (a data object), saved as `radio`, pushed on top of Start Playback. It
  covers the bottom bar like every detail destination.
- An app-scoped `StationPicker` (`ui/session/`, in `AppGraph` like `SettingsNavigator`) carries
  the result: `picked: StateFlow<Station?>` and `gone: StateFlow<Boolean>`. The radio view model
  calls `pick(station)` or `reportGone()` and sets its exit; navigation pops the radio entry. Start
  Playback's view model, which lives on beneath, collects both and consumes them.
- Only one Start Playback can be on the stack at a time (a start replaces it, and "Change source…"
  opens a new one on a Now Playing), so one holder is enough.
- Back from the radio screen without choosing pops the entry and nothing else: Start Playback is
  as it was, including a station chosen earlier.
- `AppNavigation.onBack` keeps detaching the starter only when Start Playback is on top; the radio
  screen never has a start in flight.
- The radio screen holds the session (`acquire` on visible, `release` on hidden) like every
  screen, so the one poll loop keeps running under it and the connection state is current
  (FR-032). It runs no loop of its own.

**Rationale**: Navigation 3 entries have no result channel; the app already passes cross-screen
requests through small app-scoped holders (`SettingsNavigator`, `AppMessages`).

**Alternatives considered**: sharing Start Playback's view model with the radio entry (view models
live in their entry's store by design, 002 research R2); encoding the station into a destination
key (a station is not navigation state, and the stack would have to be rewritten on return).

## R12. Start Playback with radio (FR-002, FR-016, FR-016a, FR-022a)

**Decision** ([UI contract](contracts/radio-ui.md)):

- A section "Or a radio station" between the link field and "Or pick a source", present only
  while radio is offered. It holds the "Find a radio station" row and up to three recent stations.
- **Selection**: `StartPlaybackUiState.selectedStation: Station?`. At most one of source, link and
  station is chosen: choosing a source or typing a non-empty link clears the station; choosing a
  station clears the source and the link text.
- **Which row is the selected one** (`radioRows`, pure): when the selected station is one of the
  three recent ones, that row is selected and the "Find a radio station" row stays; otherwise the
  "Find a radio station" row is replaced by the station's row with "Change", which reopens the
  radio screen. A station is therefore never listed twice.
- **Second line**: "Radio" followed by the known parts ("Radio · DE · MP3 · 128 kbps"), from
  `stationDetail(station)`; the radio screen shows the parts alone.
- **Long names**: the selected station's name uses the existing `MarqueeText` (one line, the same
  stale, focus and animation rules); every other station row is one line ending in "…".
- **The Play button** is drawn in three parts ("Play ", the name, " in <target>"), and only the
  name may shrink and end in "…". This applies to source names too, which the single truncated
  text cuts at the end today, hiding the target.
- **Saved state**: the selected station is kept in `SavedStateHandle` as JSON (R10).
- A selected station stays selected when radio stops being offered by a later answer of the
  extensions request only if it was not reported gone (R9 deselects on `Gone`); the next Play then
  fails or succeeds as the hub decides.

**Rationale**: the canvas `StartPlaybackRadio.dc.html`, and the spec's clarification that a station
is shown once.

## R13. Dependencies and versions

**Decision**: no new dependency and no version change. `gradle/libs.versions.toml` stays as it is
on this branch (Kotlin 2.4.21, AGP 9.5.0, Compose Multiplatform 1.12.2, material3 1.9.0, Navigation
3 1.2.0, Ktor 3.6.0, kotlinx.serialization 1.12.0, coroutines 1.11.0, DataStore 1.3.0, OpenAPI
Generator 7.14.0). Bottom sheets use material3's `ModalBottomSheet`, as `MoveSheet` and
`HubAddressSheet` do; the search fields use `BasicTextField`, as `HubAddressSheet` does.

Station images stay out (FR-014), so no image loader is needed.

## R14. Testing

Test-first, in `commonTest`, each seen failing before its implementation:

| Suite | Proves |
|---|---|
| `SourceKindTest`, `AddressDetailTest`, `SourceDetailTest` (extended) | a station address is `Radio` for any origin; live when not pauseable; no address detail; `http(s)` and line-in rules unchanged |
| `RadioTest` (new, `domain/`) | `stationName`, `tagName`, `stationDetail`, `stationDisplayName`, `mergePage` (duplicates, the 10,000 limit), `addRecent`, `judgeRecent` (every row of R8), `radioOffered`, `regionCountry`, `narrow`, `groupThousands`, `radioRows` |
| `StartConsequenceTest` (extended) | "already playing" for a station by address, whichever input id; another station on the target is "will stop"; a station replaces |
| `MessagesTest` (extended) | `startFailure` for `StartKind.Station` (422, 502, 503 → `StationFailed`; reasons, 404, unreachable unchanged); `radioFailure` (every row of R9) |
| `KtorHubRepositoryRadioTest` (new, MockEngine) | [contract tests](contracts/hub-repository.md#contract-tests-mockengine) |
| `RadioStoreTest` (new) | JSON round trips, unreadable values, the cap of ten, removal |
| `RadioAvailabilityTest`, `RecentStationTrackerTest`, `StationPickerTest` (new, `ui/session/`) | R3 and R8 with the fake session and virtual time |
| `PlaybackStarterTest` (extended) | a station start calls `playLink` with the station address, tracks on success, recovers by address, fails as `StationFailed` |
| `RadioViewModelTest` (new) | debounce and "latest wins", paging, dedupe, the limit note, first visit, filter persistence, sheets, Recent visibility, removal, the three failures, nothing after the screen is cleared |
| `StartPlaybackViewModelTest`, `StartPlaybackTextTest` (extended) | the row only when offered, three recent rows, selection exclusivity, the selected row rule, Play label parts, the failure message with the station kept selected, saved state |
| `AppBackStackTest` (extended) | `radio` encodes and decodes; the bottom bar is hidden on it |
| `ContrastTest` (extended) | the new text and background pairs of the canvas |

Layout, scrolling, the keyboard, TalkBack and real playback are checked on the device
([quickstart](quickstart.md) §2).

## R15. Left to the hub, or left out

- Why a station failed to start is unknown to the app (hub backlog
  `play-resolution-failure-reason.md`). Listed under "Hub gaps" in AGENTS.md by this feature.
- Whether the hub refuses HLS is hub configuration no request exposes; rows carry no mark.
- `languages`, `tags`, `favicon`, `homepage`, `hls`, `lastCheckOk`, `votes` and `clickCount` of a
  station are not mapped into the domain: no screen shows them (FR-014). The Key Entities list of
  the spec names languages and tags as properties of a station; the app filters by them through
  the hub and does not need them per station.
- Station lookup by UUID, favourites, voting and images: out of scope (FR-035).
