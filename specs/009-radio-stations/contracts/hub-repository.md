# Contract: Hub repository (additions in MOB-009)

This extends the earlier repository contracts, against `api/openapi.json` **0.1.34** (fetched
2026-11-15). The existing methods keep their behaviour. The radio requests are the one allowed
use of a path outside `/api/v2` (constitution 1.6.0, Principle I).

## New methods

```kotlin
interface HubRepository {
    // ... existing methods unchanged ...

    /**
     * One page of stations for [query], starting at [offset] (0, then the previous page's
     * nextOffset). The hub never lists stations the catalogue marks as broken.
     */
    suspend fun searchStations(query: StationQuery, offset: Int): HubResult<StationPage>

    /** Every country with stations, largest first. */
    suspend fun radioCountries(): HubResult<List<Country>>

    /** Every language with stations, largest first. */
    suspend fun radioLanguages(): HubResult<List<Language>>

    /** One page of tags, largest first; [name] is part of a tag's name, already cleaned, or null. */
    suspend fun radioTags(name: String?, offset: Int): HubResult<TagPage>
}
```

`playLink(uri, target)` is unchanged and also starts a station: its `uri` is then the station's
`radiobrowser://<uuid>` address, passed as the hub gave it.

Still **absent**: `GET /api/radio/stations/{uuid}`, every `/api/tts/*` path, group volume,
`joinMode`, `chime`, `displayName` on a start.

## Endpoint mapping

| Method | Generated call | HTTP | Query parameters sent |
|---|---|---|---|
| `searchStations` | `RadioApi.searchRadioStations` | `GET /api/radio/stations` | `limit=50`, `offset`, `order`; `name`, `countryCode`, `tag`, `language` only when not null |
| `radioCountries` | `RadioApi.listRadioCountries` | `GET /api/radio/countries` | none |
| `radioLanguages` | `RadioApi.listRadioLanguages` | `GET /api/radio/languages` | none |
| `radioTags` | `RadioApi.listRadioTags` | `GET /api/radio/tags` | `limit=50`, `offset`; `name` only when not null |

- `order`: `MostVoted` → `votes`, `Trending` → `clickCount`, `NameAZ` → `name`.
- Never sent: `direction`, `codec`, `bitrateMin`, an empty parameter, a parameter twice, a `%` in
  `name`, a `/` in a tag `name`, a `name` over 100 characters. The callers clean their text with
  `stationName` / `tagName` ([data-model](../data-model.md)); the repository sends what it is
  given.
- If the generator names operations differently, the HTTP column is binding.
- These four calls use a client with a **6 s** request and socket timeout and the usual 3 s
  connect timeout (`RADIO_TIMEOUT_MILLIS`): the hub bounds a radio request at 5 s and answers 503
  itself when the catalogue is slow. Every other call keeps its timeout.

## Mapping to domain types

| Wire | Domain | Rule |
|---|---|---|
| `RouteResponse.startedAt` | `Route.startedAt: Instant?` | the hub's "timestamp when audio began playing"; null, missing, blank or unreadable → null, and the refresh never fails because of it (as `createdAt` of an input) |
| `RadioStation` | `Station` | `uuid` and `uri` as given; a blank one drops the station. `name`, `countryCode`: trimmed, blank → null. `codec`: trimmed, blank or `UNKNOWN` in any letter case → null. `bitrate`: null or ≤ 0 → null. Nothing else is mapped |
| `RadioStationPage` | `StationPage` | `stations` in order, `nextOffset` as given |
| `RadioCountry` | `Country` | blank `code` or `name` drops the entry |
| `RadioLanguage` | `Language` | blank `name` drops the entry; `code` is not mapped |
| `RadioTag`, `RadioTagPage` | `Tag`, `TagPage` | blank `name` drops the entry |

## Error mapping

`HubError`, by the existing rules. A radio problem body (`application/problem+json`, schema
`RadioProblem`) is read through the same `rejection()` as v2 problems: only its `type` is kept, in
`HubError.Rejected.problemType`; `reason` and `outputId` are null; `title`, `detail` and
`parameter` never leave the data layer.

| Hub answer | `HubError` | `radioFailure` ([research R9](../research.md#r9-radio-errors-in-plain-words-fr-029-to-fr-034)) | The user sees |
|---|---|---|---|
| 503 `…:catalogue-unavailable`, 502 `…:catalogue-bad-answer` | `Rejected(503 / 502, urn)` | `Catalogue` | "The station catalogue isn't answering" + Retry |
| 500 `…:internal-error`, 400 `…:invalid-parameter`, 404 `…:not-found`, an unknown radio type | `Rejected(status, urn)` | `Catalogue` | the same |
| 200 with a body that does not decode | `Unexpected` | `Catalogue` | the same |
| 404 without a radio `type` (the extension is not there) | `Rejected(404, null)` | `Gone` | the radio screen closes; "Radio is no longer available on this hub" |
| no answer within 6 s, connection refused or lost | `Unreachable` | `HubUnreachable` | the app's usual "hub unreachable" state |

A failed station **start** is not a radio request: it is `playLink`'s answer and is mapped by
`startFailure` with `StartKind.Station` ([data-model](../data-model.md#messages-uimessageskt)).

## Contract tests (`MockEngine`)

`KtorHubRepositoryRadioTest` (new):

1. `searchStations(StationQuery(null, null, null, null, MostVoted), 0)` sends
   `GET /api/radio/stations` with exactly `limit=50`, `offset=0`, `order=votes`.
2. With `name = "mango"`, `countryCode = "DE"`, `tag = "jazz"`, `language = "german"`,
   `order = Trending`, `offset = 50`: each value arrives once, `order=clickCount`, `offset=50`,
   and no `direction`, `codec` or `bitrateMin`. `NameAZ` sends `order=name`.
3. A name with a space, `&` and non-ASCII letters ("Хіт FM & co") arrives percent-encoded and
   decodes to the same text.
4. The live sample page (2026-11-16, three Ukrainian stations) maps to three `Station`s with name,
   `UA`, `MP3` and the bitrate; `nextOffset` is carried.
5. A station with `name: null`, `countryCode: null`, `codec: "UNKNOWN"`, `bitrate: null` (and one
   with `codec: "unknown"`, `bitrate: 0`, a blank name) maps to a `Station` with those four null.
   A station with a blank `uuid` is left out and the rest of the page is kept.
6. A page with an unknown extra field per station and at top level decodes.
7. `nextOffset: null` and a missing `nextOffset` both map to null.
8. `radioCountries` sends `GET /api/radio/countries` with no query and maps code, name and count;
   `radioLanguages` likewise for `/api/radio/languages`, ignoring `code`.
9. `radioTags(null, 0)` sends `limit=50&offset=0` and no `name`; `radioTags("jazz", 50)` sends
   `name=jazz&offset=50`.
10. 503 with the body `{"type":"urn:multiroom:radio:error:catalogue-unavailable",…}` sent as
    `application/problem+json` → `Rejected(503, that URN, null, null)`. The same for 502
    `catalogue-bad-answer` and for 400 `invalid-parameter` (the `parameter` field is ignored).
11. 404 with the hub's plain body (`{"timestamp":…,"status":404,"error":"Not Found",…}`,
    `application/json`) → `Rejected(404, null, null, null)`.
12. 404 with `urn:multiroom:radio:error:not-found` → `Rejected(404, that URN, …)`.
13. A 200 HTML body, and a 200 JSON body of the wrong shape, → `Unexpected`.
14. An answer after 5.5 s (virtual time) succeeds; one after 6.5 s → `Unreachable`. A snapshot
    request answering after 3.5 s is still `Unreachable` (the radio timeout did not leak).
15. A network failure on each of the four calls → `Unreachable`.
16. `playLink("radiobrowser://9617a958-0601-11e8-ae97-52543be04c81", Target.Room("kitchen"))`
    posts `{"uri":"radiobrowser://9617a958-…","targetId":"kitchen","targetType":"SINGLE_OUTPUT"}`
    and nothing else (no `displayName`, `joinMode`, `chime` or volume).

`KtorHubRepositorySnapshotTest` (extended):

17. An input with `uri: "radiobrowser://…"`, `source: "EPHEMERAL"` maps to a `Source` of kind
    `Radio`; one with `source: "STATIC"` and the same scheme too.
18. A route with `startedAt: "2026-11-16T10:00:05Z"` maps to that instant; `null`, a missing field,
    `""` and `"soon"` map to null, and the snapshot never fails because of it.
