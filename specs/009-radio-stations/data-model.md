# Data Model: Find and Play Radio Stations

**Feature**: [spec.md](spec.md) | **Research**: [research.md](research.md)

Everything below is app code in `commonMain`. Wire types (`RadioStation`, `RadioCountry`, …) are
generated and stay inside `data/`.

## Domain (`domain/`)

### `Station` (new, `domain/Radio.kt`)

| Field | Type | From the hub | Rule |
|---|---|---|---|
| `uuid` | `String` | `uuid` | identity; blank ⇒ the station is dropped from the page |
| `uri` | `String` | `uri` | passed unchanged to `POST /api/v2/play`; blank ⇒ dropped |
| `name` | `String?` | `name` | trimmed; blank ⇒ null |
| `countryCode` | `String?` | `countryCode` | trimmed; blank ⇒ null |
| `codec` | `String?` | `codec` | trimmed; blank or `UNKNOWN` (any letter case) ⇒ null |
| `bitrate` | `Int?` | `bitrate` | kbit/s; null or ≤ 0 ⇒ null |

Pure functions beside it:

| Function | Result |
|---|---|
| `stationDisplayName(station)` | the name, or "Unnamed station" |
| `stationDetail(station)` | the known parts joined with " · ": country code, codec, "<bitrate> kbps"; null when none is known |
| `stationName(text)` | the name to search for: `%` removed, trimmed, at most 100 characters; null when empty |
| `tagName(text)` | as `stationName`, with `/` removed too |
| `mergePage(listed, page)` | `listed` plus the stations of `page` whose `uuid` is not yet listed, in page order |
| `canLoadMore(nextOffset)` / `limitReached(nextOffset)` | `nextOffset != null && nextOffset < 10000` / `nextOffset != null && nextOffset >= 10000`. `canLoadMore` also ends the Tag sheet's paging |
| `regionCountry(region, countries)` | the country whose code equals the phone's region ignoring case, or null |
| `narrow(items, text)` | the items whose name contains the trimmed text ignoring case; all when blank |
| `groupThousands(count)` | "6,965" |

### `StationQuery`, `StationOrder`, `StationPage`

| Type | Fields |
|---|---|
| `StationQuery` | `name: String?` (already cleaned), `countryCode: String?`, `tag: String?`, `language: String?`, `order: StationOrder` |
| `StationOrder` | `MostVoted` (default), `Trending`, `NameAZ` |
| `StationPage` | `stations: List<Station>`, `nextOffset: Int?` |

### `Country`, `Language`, `Tag`, `TagPage`

| Type | Fields |
|---|---|
| `Country` | `code: String`, `name: String`, `stationCount: Long` |
| `Language` | `name: String`, `stationCount: Long` |
| `Tag` | `name: String`, `stationCount: Long` |
| `TagPage` | `tags: List<Tag>`, `nextOffset: Int?` |

An entry with a blank name (or, for a country, a blank code) is dropped. `Language.code` of the
contract is not mapped: nothing uses it.

### `RadioFilters` (kept on the phone)

| Field | Type | Meaning |
|---|---|---|
| `country` | `Country?` | code and name as chosen (the stored count is not shown) |
| `tag` | `String?` | the whole tag, as the tag list gave it |
| `language` | `String?` | the whole language name |
| `order` | `StationOrder` | `MostVoted` until chosen otherwise |

Absent in storage ⇒ the first visit has not happened yet. `toQuery(name)` builds the
`StationQuery`.

### `RecentStation` and `PendingStation`

A recent station is a `Station` as known when it was started. The list holds at most ten, most
recent first, each `uuid` once.

| `PendingStation` field | Type | Meaning |
|---|---|---|
| `station` | `Station` | what will be added |
| `routeId` | `String` | the playback being watched |
| `trackedAt` | `Long` | the phone's clock (epoch ms) when the start request was sent (also when its answer was lost and the start recovered later); the audio cannot have begun earlier |
| `playingSince` | `Long?` | when the audio began, fixed once by the first refresh that shows the route `Active`: `max(route.startedAt ?: now, trackedAt)`; null until then |

| Function | Result |
|---|---|
| `addRecent(list, station)` | `station` first, any older entry with its `uuid` removed, cut to ten |
| `judgeRecent(pending, snapshot, nowMillis)` | `Recent`, `Drop` or `Wait(pending)`, where the entry carried by `Wait` has `playingSince` fixed when this snapshot is the first to show the route `Active`; a route already playing for 30 s at that moment is `Recent` in the same call ([research R8](research.md#r8-recent-stations-and-the-30-second-rule-fr-021-to-fr-024a)) |

`RECENT_AFTER_MILLIS = 30_000`, `RECENT_LIMIT = 10`, `RECENT_IN_START_PLAYBACK = 3`.

### Source kind (changed, `domain/SourceKind.kt`, `domain/Models.kt`)

| Item | Change |
|---|---|
| `SourceKind` | new value `Radio` |
| `isStationAddress(uri)` | new: the trimmed address starts with `radiobrowser://`, any letter case |
| `sameStation(a, b)` | new: both are station addresses, equal ignoring case |
| `inferSourceKind` | a station address is `Radio`, checked before the origin |
| `isLiveStream` | not pauseable and (`http(s)` or a station address) |
| `kindLabel` | `Radio` → "Radio" |
| `addressDetail`, `sourceDetail` | `Radio` → null |

### Starting (changed)

| Item | Change |
|---|---|
| `StartWhat` (`StartRequest.kt`) | new `Station(station: Station)` |
| `Route` (`Models.kt`) | gains `startedAt: Instant?`, the hub's `RouteResponse.startedAt` (when the audio began; null before playback starts, when missing or when unreadable) |
| `effectiveJoinMode` | `Station` → `Replace` |
| `StartConsequence.of` | `alreadyThere` for a station: a live route addressed to the target whose input address is the same station |
| `RuntimeSourceRow` (`SettingsBuilder.kt`) | gains `kind: SourceKind` |

### Radio rows in Start Playback (new, `domain/StartPlaybackBuilder.kt`)

`radioRows(offered, recent, selected): RadioRows?`: null when radio is not offered.

| Field | Meaning |
|---|---|
| `found: Station?` | the selected station when it is not among the first three recent ones; it then takes the place of the "Find a radio station" row |
| `recent: List<Station>` | the first three recent stations |
| `selectedUuid: String?` | the selected station's `uuid`, for marking a recent row |

`SourceOption` gains `stationUri: String?` (the source's address when its kind is `Radio`).
`hideListedStations(sources, rows, selectedSourceId): List<SourceOption>` leaves out every source
whose `stationUri` is the same station as `rows.found` or one of `rows.recent`, except the
selected source; with `rows == null` it returns the list unchanged.

### Radio availability

`radioOffered(inventory: ExtensionInventory): Boolean`: an extension with id `radio` (any letter
case) and status `Active`.

## Data (`data/`)

### `HubRepository` (changed)

Four new methods, see [contracts/hub-repository.md](contracts/hub-repository.md). `playLink` is
unchanged and also carries a station's address.

### `RadioStore` (new)

| Member | Behaviour |
|---|---|
| `recent: Flow<List<Station>>` | empty when nothing is stored or the value is unreadable |
| `filters: Flow<RadioFilters?>` | null before the first visit or when unreadable |
| `suspend fun pending(): List<PendingStation>` | read once by the tracker at start-up |
| `suspend fun addRecent(station)` | `addRecent` on the stored list |
| `suspend fun removeRecent(uuid)` | removes that entry; nothing else |
| `suspend fun saveFilters(filters)` | |
| `suspend fun savePending(list)` | replaces the stored list |

`DataStoreRadioStore(dataStore)` and `DataStoreHubAddressStore(dataStore)` share the app's one
`DataStore<Preferences>`. Keys and format: [research R10](research.md#r10-what-is-kept-on-the-phone).
`encodeStation(station): String` / `decodeStation(text): Station?` are also used for saved state.

## Session objects (`ui/session/`, app scope, created in `AppGraph`)

| Object | State | Operations |
|---|---|---|
| `RadioAvailability` | `state: StateFlow<RadioOffer>` (`Unknown`, `Offered`, `NotOffered`); `Unknown` again when the hub address changes | `suspend refresh()`, `markGone()` |
| `StationPicker` | `picked: StateFlow<Station?>`, `gone: StateFlow<Boolean>` | `pick(station)`, `reportGone()`, `consumePicked()`, `consumeGone()` |
| `RecentStationTracker` | pending entries (also stored) | `track(station, route, sentAt, fence)`; judges on every successful refresh and stores an entry whenever a judgement changed it |
| `PlaybackStarter` (changed) | | `StartKind.Station`; calls `tracker.track` when a station start succeeds |

`AppGraph` gains `radioStore`, the three objects above, `region: () -> String` and
`radioViewModel(savedState)`; `startPlaybackViewModel` also passes `RadioAvailability`,
`StationPicker` and `RadioStore`.

## Messages (`ui/Messages.kt`)

| Item | Change |
|---|---|
| `StartKind` | new `Station` |
| `StartFailure` | new `StationFailed(name)`: "Couldn't start <name>" |
| `startFailure` | for `Station`: reasons, 404 and unreachable as today; every other answer → `StationFailed(names.source)` |
| `RadioFailure` | new: `Catalogue`, `HubUnreachable`, `Gone` |
| `radioFailure(error)` | new, [research R9](research.md#r9-radio-errors-in-plain-words-fr-029-to-fr-034) |

## Navigation (`ui/nav/Destinations.kt`)

`Destination.Radio` (data object), saved as `radio`. Not a tab; the bottom bar is hidden on it.

## View state

### `StartPlaybackUiState` (changed)

| Field | Type | Meaning |
|---|---|---|
| `radio` | `RadioRows?` (new) | null ⇒ no "Or a radio station" section |
| `selectedStation` | `Station?` (new) | excludes `selectedSourceId` and a non-empty `linkText`. Deselected when radio becomes `NotOffered`, or leaves `Offered` for `Unknown` (a change of hub). One restored from saved state while radio is still `Unknown` is kept |
| effective station | derived | `selectedStation` while radio is `Offered`, else none. The only value the Play label, `playEnabled`, the consequence, `radio`, `hideListedStations` and the start read; while it is none the screen behaves as with no station selected |
| `content.sources` | `List<SourceOption>` | now passed through `hideListedStations` |
| `openRadio` | `Boolean` (new) | one-shot request to push `Destination.Radio`; consumed by the screen |
| `playLabel` | `PlayLabel` | `PlaySource(stationDisplayName, target)` for a station |
| `message` | `String?` | unchanged: the one-shot snackbar text; also carries "Couldn't start <station>" and "Radio is no longer available on this hub" |

Saved state keys: existing `sourceId`, `target`, `link`, `preselected`, `fixedName`; new `station`
(JSON).

`playLabelParts(label): PlayLabelParts(before, name, after)` replaces the single string for
drawing: `("Play ", "MANGORADIO", " in Bedroom")`; `name` is empty for "Play", "Play link in
<target>" and "Starting…", whose whole text is in `before`.

### `RadioUiState` (new, `ui/radio/`)

| Field | Type | Meaning |
|---|---|---|
| `connection` | `Connection` | from the session; unreachable ⇒ the stale banner |
| `nameText` | `String` | the field's text (at most 100 characters, no `%`) |
| `filters` | `RadioFilters` | what the chips and the order control show |
| `recent` | `List<Station>` | the recent stations; the "Recent" section shows while `stationName(nameText)` is null and `recent` itself is not empty, whatever the station list holds (also under "No stations match" and the catalogue message) |
| `list` | `StationList` | see below |
| `sheet` | `FilterSheetState?` | the open sheet, if any |
| `exit` | `RadioExit?` | `Chosen` or `Gone`; the screen consumes it and navigation pops |

`StationList`:

| Field | Meaning |
|---|---|
| `stations: List<Station>` | what is listed |
| `phase` | `LoadingFirst` (placeholder rows), `Loaded`, `LoadingMore` ("Loading more stations…") |
| `nextOffset: Int?` | for the next page |
| `limitReached: Boolean` | the 10,000 note |
| `problem: RadioFailure?` | `Catalogue` ⇒ the message with Retry above the rows |
| `empty` | derived: `Loaded`, no stations, no problem ⇒ "No stations match" |

`FilterSheetState`:

| Field | Meaning |
|---|---|
| `kind` | `Country`, `Tag`, `Language`, `Order` |
| `searchText: String` | the sheet's field (not for `Order`) |
| `rows: List<FilterRow>` | `FilterRow(name, count: Long?, detail: String?, selected)`; for a country the row also carries its code |
| `loading`, `loadingMore`, `problem` | as for the station list |

## State transitions

### One radio request

```text
query changed (name: after 500 ms; filter/order: at once) ─► LoadingFirst
  ├─ Ok ───────────────► Loaded (stations = page, nextOffset)
  ├─ Catalogue ────────► Loaded, problem = Catalogue ── Retry ─► LoadingFirst
  ├─ HubUnreachable ───► session.awaitFreshSnapshot()
  │                        ├─ a snapshot ─► as Catalogue (the hub answers, radio did not)
  │                        └─ null ───────► no message; repeated once, after the first successful
  │                                         refresh that started after the null (fence rule)
  └─ Gone ─────────────► markGone, reportGone, exit = Gone
Loaded, scrolled near the end, canLoadMore ─► LoadingMore
  ├─ Ok ───────────────► Loaded (mergePage); limitReached when nextOffset ≥ 10000
  ├─ Catalogue ────────► Loaded, rows kept, problem = Catalogue ── Retry ─► LoadingMore
  ├─ HubUnreachable ───► as for the first page; rows kept
  └─ Gone ─────────────► as for the first page
any state, query changed again ─► the request in flight is cancelled; its answer is never used
```

### One station, from choice to "Recent"

```text
Radio: tap a row ─► StationPicker.pick ─► [.., StartPlayback] selectedStation = S
Play ─► PlaybackStarter.start(StartWhat.Station(S))
  ├─ failed (no reason) ─► snackbar "Couldn't start S"; S stays selected; nothing tracked
  └─ started (route r) ──► tracker.track(S, r) ; Now Playing(r)
        each refresh: judgeRecent
          ├─ r gone / Failed / another input ─► dropped
          ├─ r Active for the first time ─────► playingSince = max(r.startedAt ?: now, trackedAt)
          ├─ r Active and ≥ 30 s since then ──► RadioStore.addRecent(S); dropped
          └─ otherwise ───────────────────────► wait (also across background and restart)
```

### Navigation

```text
[Rooms, StartPlayback]            ─"Find a radio station"─► [Rooms, StartPlayback, Radio]
[Rooms, NowPlaying, StartPlayback(fixed)] ─same─►            [.., StartPlayback(fixed), Radio]
Radio: Back ─► pop, nothing changed
Radio: row ──► pick, pop; StartPlayback selects the station
Radio: Gone ─► reportGone, pop; StartPlayback drops radio and says so once
```
