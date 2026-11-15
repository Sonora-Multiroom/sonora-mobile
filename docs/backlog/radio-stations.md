# Radio: find and play internet stations from the radio-browser.info catalogue

- **Status:** Proposed, not started. Written 2026-11-13, rewritten 2026-11-15 against the shipped
  search contract, reviewed 2026-11-16 against the contract, the app code and the live hub.
- **Origin:** `sonora-radio` (RADIO-001 play by UUID, RADIO-002 search and browse). The extension's
  own repository records the decision that clients stay thin and that the mobile app gets a browse
  and search screen.
- **Depends on:**
  - RADIO-001 and RADIO-002 (`sonora-radio` 0.2.0) on the hub: deployed and `ACTIVE` on
    `multiroom.lan` (seen 2026-11-16); the PR `Sonora-Multiroom/sonora-radio#2` is still open.
  - A constitution amendment allowing `/api/radio/**` (decision 1 below), with AGENTS.md synced in
    the same change.
  - The refreshed `api/openapi.json` (API 0.1.34, which carries the radio paths) committed and
    reconciled: besides radio it adds `GET /api/v2/chimes` and changes `InputResponse`,
    `OutputResponse`, `GroupResponse`, `PlaybackRequest`, `CreateRouteRequest` and
    `CreateInputRequest` (Constitution I: reconcile before further work touches those calls).
- **Effort:** Medium to large. A contract addition, domain mapping, a browse and search screen with
  paging, a way to start a station from it, a design pass first, and tests.

## Problem

Today the app plays a station only if it is a configured source or the user pastes its stream
address into Start Playback (MOB-003). Finding a station means leaving the app. The hub's `radio`
extension now lets any client search the radio-browser.info catalogue and play a result, and the
app has nothing that uses it.

## What the hub offers now (RADIO-002)

Read-only requests outside REST v2, unversioned, answered only while the extension is installed and
enabled. The contract is in the hub's `/api-docs` (so in `api/openapi.json` from 0.1.34, operations
tagged `Radio`) and in `sonora-radio/specs/002-station-search-api/contracts/radio-rest-api.yaml`.

| Request | Gives |
|---|---|
| `GET /api/radio/stations` | A page of stations. Optional filters `name`, `tag`, `countryCode`, `language`, `codec`, `bitrateMin`; `order` (`name`, `votes`, `clickCount`, `bitrate`), `direction`, `limit` (1–100, default 20), `offset` (0–10000). No parameters: the 20 most voted. **Never returns stations the catalogue marks as broken** |
| `GET /api/radio/stations/{uuid}` | One station, same shape, even if the catalogue marks it broken (`lastCheckOk = false`); may be up to 5 minutes old |
| `GET /api/radio/countries`, `/languages` | The whole list, most stations first, for browse screens (225 countries and 585 languages on 2026-11-16); remembered by the hub for 6 hours |
| `GET /api/radio/tags` | A page of tags (more than ten thousand in all), filterable by `name` |

- A station (`RadioStation`) has `uuid`, `uri`, `name`, `favicon`, `homepage`, `countryCode`,
  `languages`, `tags`, `codec`, `bitrate`, `hls`, `lastCheckOk`, `votes`, `clickCount`. `uri` is
  `radiobrowser://<uuid>` and is passed unchanged to `POST /api/v2/play`.
- `name`, `favicon`, `homepage`, `countryCode`, `codec` and `bitrate` may be `null`, and `codec`
  may be the label `UNKNOWN` (all seen live). The list needs a fallback for each it shows. `name`
  and tags are community-edited text (one live station has the single tag "club dance electronic
  house trance").
- Paging: a page has `nextOffset`, null on the last page. Order among stations with equal values is
  not stable, so the app drops stations whose `uuid` it already has. The largest `offset` is 10000
  (10001 answers 400), so the app stops paging there even if `nextOffset` is not null.
- `tag` and `language` match the whole value, so the filter values come from the tag and language
  lists; `countryCode` comes from the country list.
- Values the hub refuses with 400, which the app must therefore never send: `%` in a station
  `name` (confirmed live), `%` or `/` in a tag `name`, a `name` over 100 characters, a `limit`
  outside 1–100, an unknown parameter, a parameter sent twice. Blank counts as not given.
- Errors are problem details (`application/problem+json`, schema `RadioProblem`) whose `type` is a
  URN: `urn:multiroom:radio:error:invalid-parameter` (with a `parameter`), `:station-not-found`
  (404), `:catalogue-bad-answer` (502), `:catalogue-unavailable` (503 with `Retry-After: 30`),
  `:not-found` (404, an unknown path under `/api/radio/`), `:method-not-allowed`,
  `:internal-error`; more may be added. There is no `reason` field as on v2 errors.
- Without the extension, a radio path answers the hub's plain 404 (JSON without a problem `type`).
- Timing seen live: first search 1.4 s (cold server list), later 0.3–0.4 s, a remembered list
  0.06 s (0.9 s for a cold country list). The hub bounds a request at 5 s
  (`multiroom.radio.request-timeout`).
- `favicon` and `homepage` are `http(s)` addresses the hub has not fetched or checked.

### Playing a station (RADIO-001)

- `POST /api/v2/play` with the station's `uri`. The hub registers an ephemeral input named after
  the station when the client sends no name, and the route is not pauseable.
- **A failed start gives no reason.** Every failure (unknown station, a codec the hub cannot
  decode, HLS switched off, catalogue down) answers 422 "Route Error" with only "Failed to create
  route…"; the cause is in the hub log (the hub's backlog item
  `play-resolution-failure-reason.md`). The app can say only that the station could not be
  started. As with any failed start, what the room was playing has already stopped.
- Whether the hub refuses HLS stations is hub configuration (`multiroom.radio.allow-hls`, default
  `true`) that no request exposes, so the app cannot know it.
- A start that begins with a cold catalogue can take up to the hub's `resolve-timeout` (10 s),
  inside the app's 30 s link timeout.

## Proposal

- A radio screen: search by name, with filters for country, tag and language chosen from the lists,
  an order choice (most voted = `votes`, trending = `clickCount`, plays in the last 24 hours, and
  name), and a list of stations showing name, codec and bitrate, loading the next page from
  `nextOffset`.
- Starting a station from the list into a room or group, reusing Start Playback's room choice and its
  "what will stop" warning, with `uri` as the input. The app supplies no name. The start goes
  around Start Playback's link check (`checkLink` in `domain/LinkAddress.kt`), which accepts only
  `http(s)` addresses.
- The entry point is shown only while `GET /api/v2/extensions` lists `radio` as `ACTIVE`
  (decision 2). The app asks for that list today only while Settings' Extensions tab is open, so the
  feature also asks for it where the entry point lives.
- Radio requests use a timeout longer than the hub's 5 s bound (about 6 s). The app's ordinary hub
  timeout is 3 s (`HUB_TIMEOUT_MILLIS`), which would turn a slow catalogue into "hub unreachable"
  instead of the hub's own 503.
- Errors through the problem-details handling, extended for the radio `type` URNs: 503 and 502
  offer a retry; a 404 without a radio `type` (the extension went away) hides the entry point.
- Search text is cleaned before it is sent (no `%`, at most 100 characters), so the hub never
  answers 400.
- HLS stations may carry a mark, but cannot be marked as refused (see above). A station that fails
  to start shows a plain "couldn't start" message.
- Favicons are optional decoration only: bounded size, loaded lazily, never required. Loading
  images would be the app's first requests to hosts other than the hub and probably needs an
  image-loading dependency (Constitution VII), so the first version may leave them out.

Nothing here is designed. A design pass on the canvas comes first (Constitution VI).

### Effects on existing screens

Checked on the hub on 2026-11-16 (API 0.1.34, `radio` 0.2.0): the station MANGORADIO played in one
room, started in 2.4 s. Its input was `EPHEMERAL`, `autoRemove`, not pauseable, named "MANGORADIO",
and **kept the address `radiobrowser://<uuid>`**, not the stream's `http(s)` address. The route was
transferable. Stopping the route removed the input. So, with today's code:

- `inferSourceKind` shows it as Link (a runtime input), which is acceptable.
- `isLiveStream` asks for an `http(s)` address, so a station is **not** labelled live: Rooms shows
  no "Live stream" status and Now Playing has Stop only, without "Live streams can't be paused".
  The rule (Constitution II, one function) needs `radiobrowser://` added, test-first.
- `addressDetail` returns a non-`http(s)` address as typed, so Now Playing's subtitle would read
  "Link · radiobrowser://78012206-…". It needs a rule for this scheme (no detail, or "Radio").
- Each start makes a new input, so "<source> is already playing in <target>" cannot match a
  station by input id; matching by the input's `uri` would (the UUID is lower case in both).
- These three affect a station started by any client (CLI, MCP, voice) today, before this feature.

### What stays out

- Votes and favourites. The extension casts no votes and reports no plays for a search; an app
  control for them is a separate decision.
- Saved stations, and with them `lastCheckOk`: only the lookup by UUID can return a broken station,
  and only a saved station needs that lookup.
- Anything the hub already does: finding the current stream address, reporting the play, mirror
  failover and caching.

## Decisions (2026-11-16)

1. **Policy exception: allowed, pending the amendment.** The app may call `/api/radio/**` as the one
   extension namespace, because the extension is the single place that holds the catalogue logic
   and a hub-side `/api/v2` wrapper would duplicate it. Constitution Principle I names extension
   paths as out of bounds, so this needs an amendment through `/speckit-constitution` (a version
   bump) with AGENTS.md synced in the same change, before the feature's plan.
2. **How the app knows radio is installed:** `GET /api/v2/extensions`, an entry with id `radio` and
   status `ACTIVE`. It is a v2 request the app already makes, and it also gives the extension's
   version.

## Open questions

1. **Where does the client code come from?** The refreshed `api/openapi.json` has the `Radio*`
   shapes, and they match the extension's YAML. The generator keeps operations by tag
   (`openapiNormalizer` in `composeApp/build.gradle.kts`), so adding `Radio` to that list generates
   them. Recommended over hand-written code; the plan confirms the generator copes with the
   nullable fields.
2. **Saved stations.** The extension has no favourites, and its own backlog
   (`sonora-radio/docs/backlog/favourite-stations.md`) leans towards favourites kept by the hub and
   shared by every client. A list kept in the app is a separate item.
3. **Language of the lists.** Country names come in English with a code; the app can localise from
   `countryCode`. Language names are the catalogue's English names, and 447 of 585 have no code.
4. **Where does the entry point live?** A fifth bottom-navigation item, or a way into radio from
   Start Playback's source list. For the design pass.
