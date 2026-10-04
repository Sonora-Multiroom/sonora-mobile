# Research: Start Playback (003)

Phase 0 of [plan.md](plan.md). Each item: Decision, Rationale, Alternatives considered. Items from
001 and 002 still apply (one `HubSession` poll loop, fence rule on `startedSeq`, `AppMessages`,
error copy only in `ui/Messages.kt`); they are referenced, not repeated.

## R1. Reconciling the contract change 0.1.20 → 0.1.21

**Decision**: Treat the refresh as additive for every call the app already makes, and say so in
a reconciliation task with a regression test run. The changes that matter:

| Change in 0.1.21 | Effect on the app |
|---|---|
| `RouteResponse.joinMode` (`REPLACE`/`MIX`/`DUCK_OTHERS`) | Mapped to `Route.joinMode` (R3). Rooms and Now Playing ignore it in this feature (backlog [rooms-with-several-playbacks](../../docs/backlog/rooms-with-several-playbacks.md)) |
| `InputResponse.defaultJoinMode` (nullable) | Mapped to `Source.defaultJoinMode` (R3) |
| `CreateRouteRequest.joinMode`, `PlaybackRequest.joinMode` | Never sent: the app names no mode (spec Clarifications) |
| `ErrorResponse.reason`, `ErrorResponse.outputId` | Kept on `HubError.Rejected` (R6) |
| `GET /outputs/{id}/routes` (`RouteOnOutputResponse`, `duckState`, `main`) | Not used: `GET /routes` already carries `joinMode`, and one request per room per poll would multiply traffic |
| Existing calls (volume, mute, stop, pause, transfer, snapshot GETs) | Paths, bodies and status codes unchanged. Transfer may now be refused with an admission `reason`; its copy stays the general "Couldn't move…" (002) |

AGENTS.md is synced in the same feature: API version 0.1.21, and the domain rule "an output plays
at most one route" is qualified (several routes per output since 0.1.21; Rooms/Now Playing still
show one, see backlog).

**Rationale**: Constitution I requires reconciling every affected call before further work touches
them. The diff of `api/openapi.json` (commit `9f4e9fa`) shows only additions on paths the app
calls; the generated client gains fields with `null` defaults, which the lenient `HubJson`
tolerates.

**Alternatives considered**: using `GET /outputs/{id}/routes` for status lines (rejected: N extra
requests per poll, and it adds nothing the target status lines need; `duckState`/`main` matter to
the 004 backlog item, not here).

## R2. Where the multi-route view of the hub lives

**Decision**: Add one helper in `domain/PlaybackRules.kt`:
`routesByRoom(snapshot): Map<roomId, List<Route>>`. Every live route (not `Stopped`) is listed
under every room it covers (a room route under its room, a group route under each known member),
in hub order. `occupancy()` (first route wins) stays unchanged for Rooms and Now Playing.

**Rationale**: since 0.1.21 a room may carry several routes. Start Playback must see all of them
(status line "sources joined with +", every affected playback named). Changing `occupancy()` would
change Rooms and the Move sheet, which the spec defers to the backlog.

**Alternatives considered**: rewriting `occupancy()` to return lists (rejected: out of scope,
risks 001/002 behaviour).

## R3. Join modes in the domain

**Decision**: `enum class JoinMode { Replace, Mix, Announcement }`.
- `Route.joinMode: JoinMode`: `REPLACE` → Replace, `MIX` → Mix, `DUCK_OTHERS` → Announcement. A
  missing or unrecognised value (older or newer hub) → **Replace**. The playback is then a normal one
  and is named as stopping, so the user is never told less than may stop.
- `Source.defaultJoinMode: JoinMode?`: `null` = none declared. An unrecognised value is coerced to
  `null` by `HubJson` (`coerceInputValues`) and therefore also behaves as Replace (FR-010, "unknown
  mode treated as replace").
- **Effective mode** of a start, decided in one function `effectiveJoinMode(what)`: a link →
  Replace; a source → `defaultJoinMode ?: Replace`.

**Rationale**: the spec's rule set (FR-010) needs only these three values. Mapping the unknown
case to Replace in one place makes FR-010's "unknown → replace" testable at the mapping level
(fixture with `"joinMode": "SOMETHING_NEW"`) and at the domain level.

**Alternatives considered**: an explicit `JoinMode.Unknown` (rejected: every consumer would map it
to Replace anyway; one decision point is simpler).

## R4. Start Playback content: one builder, one consequence function

**Decision**: two pure, test-first objects in `domain/`:
- `StartPlaybackBuilder.build(snapshot): StartPlaybackContent`. It lists the sources (turned-on
  only, sorted case-insensitively, id as tie-break) and the targets (FR-007 ordering: selectable
  groups, selectable rooms, then unselectable, each alphabetical), each with a `TargetStatus`
  (FR-008) and `selectable` (FR-009). Unknown target types are never listed (they cannot be
  addressed).
- `StartConsequence.of(snapshot, what, target): Consequence` (FR-010/FR-011). It derives the
  affected playbacks from `routesByRoom` over the target's **known member rooms** (for a group: all
  members, including turned-off and not-connected ones, because the hub stops a group route as a
  whole wherever it plays), de-duplicated by route id, in hub order. Each affected playback is named
  with its own target (`<where>` = its room, or its group). The rules:
  - **Already playing**: some live route has `inputId == chosen source` and a target equal to the
    chosen target → `AlreadyPlaying`. This wins in every mode and is never true for a link (the hub
    always creates a new source for a link).
  - **Replace**: affected minus Announcement routes → `WillStop` (warning colour), or none.
  - **Mix**: affected minus Announcement routes → `PlaysAlongside`, or none. An announcement is not
    named here: the spec says it is named only when a new announcement lowers it.
  - **Announcement**: all affected, announcements included → `WillBeLowered`, or none.
  - Notes (FR-011): `WontPlay(turnedOff, notConnected)` for a group's unplayable members;
    `AllRoomsMuted` when master mute is on, else `TargetMuted` when the room is muted or the group
    is muted or every member is muted.

The wording lives in `ui/startplayback/StartPlaybackText.kt` (status line, consequence line,
notes), like `destinationNoteText` in 002.

**Rationale**: Constitution II (one documented place per derived value) and IV (test-first). The
Move sheet's `MoveDestinations` already has similar rules, but it assumes one route per room and
its notes have a different shape. Sharing only the low-level helpers (`describeTarget`,
`liveRoutes`, the new `routesByRoom`, `cardStatus`) keeps 002 untouched.

**Alternatives considered**: extending `MoveDestinations` (rejected: different rules for "only"
options, and it would drag the multi-route change into the Move sheet).

## R5. Status words on targets

**Decision**: a room's own playback reads `<state> · <source>` with the state words of 001 FR-008:
Playing, Paused, Starting…, Stopping…, Couldn't play, Unknown. A live stream reads **Playing**
here, not "Live stream", because the spec's list for this screen omits it and the design draws
"Playing · Radio Paradise". The status uses the existing `cardStatus()` and maps `LiveStream` →
Playing in the text function. With several playbacks on a room the sources are joined with " + "
after the state of the first one (hub order), e.g. "Playing · Jazz24 + Doorbell". Announcements are
excluded before this (FR-008).

A room covered only by a group's playback reads "In <group>"; when it carries both its own playback
and a group's, its own wins. A group reads "Group · <sources>" from the playbacks addressed to the
group itself, otherwise "Group · <member rooms>" (playable members, joined with " + ", ellipsised
by the layout).

**Rationale**: the spec fixes the strings. Choosing "its own playback wins" keeps the status line
short. The consequence line still names every affected playback.

## R6. Hub errors for a start

**Decision**: extend `HubError.Rejected` with `reason: String?` and `outputId: String?` from the
RFC 7807 body (only the app's own copy reaches the screen, Constitution V). Starts map to a sealed
`StartFailure` in one function `startFailure(kind, error)` (`ui/Messages.kt` keeps the copy):

| Hub answer | Source start (`POST /routes`) | Link (`POST /play`) | Copy (FR-016) |
|---|---|---|---|
| any status, `reason = ROUTE_LIMIT_REACHED` | RoomFull(outputId) | same | "<room> can't play more at once" |
| any status, `reason = INPUT_ALREADY_ON_OUTPUT` | AlreadyThere(outputId) | same | "<source> is already playing in <room>" |
| 400 | Other | LinkUnusable | "Couldn't start playback" / "The hub couldn't play this link" |
| 404 | Gone → R7 | Gone (target) → R7 | "<name> is no longer on the hub" |
| 422 without reason | Other | LinkUnusable | as 400 |
| 502 | Other | LinkUnreachable | "Couldn't reach that link" |
| 503 | Other | ServiceDown | "That service isn't available right now. Try again later." |
| Unreachable (connect error, timeout) | Recovery → R8 | Recovery → R8 | else "Couldn't reach the hub" |
| anything else | Other | Other | "Couldn't start playback" |

`<room>` is the name of `outputId` in the latest snapshot, falling back to the chosen target's
name when `outputId` is missing or unknown.

**Rationale**: the contract documents `reason`/`outputId` but not the status code of admission
refusals, so the app keys on `reason` regardless of status. The 400/422/502/503 split for `/play`
follows the documented response descriptions ("invalid URI", "Route creation failed", "URI
unreachable", "Upstream service unavailable").

**Alternatives considered**: keying on problem `type` URNs (rejected: only
`urn:multiroom:error:validation-error` is documented).

## R7. "No longer on the hub" (404)

**Decision**: on 404, capture `session.startedSeq`, request a refresh and wait for the first state
with `refreshSeq` greater than it (the 001/002 fence rule), at most 5 s. In that snapshot the
missing one is named: the source if it is gone or turned off, else the target if it is gone, else
the general "Couldn't start playback". Then the screen's normal pruning deselects the vanished item
(spec edge case "Selected source or target disappears").

**Rationale**: `POST /routes` returns 404 for "Input, output, or group not found" without saying
which. The spec asks for the name and an immediate refresh, which this provides in one step.

## R8. Timeout recovery (FR-016a) and the long link timeout (FR-014)

**Decision**:
- **Timeouts.** The shared client keeps 3 s. `KtorHubRepository` gets a second client for
  `POST /play` only, derived with `client.config { install(HttpTimeout) { request/socket = 30 s,
  connect = 3 s } }`. It shares the engine, and only the play call waits longer. The generated API
  offers no per-request hook, so the second client is the cleanest seam.
- **Recovery.** When a start ends in `HubError.Unreachable`, the view model (still showing
  "Starting…") waits for a fresh snapshot with the fence rule of R7, at most 5 s. It looks for a
  live route addressed to exactly the chosen target whose input is the chosen source, or, for a
  link, an input with origin Runtime whose `uri` equals the normalised link (compared trimmed,
  case-insensitive scheme and host). If several match, it takes the last in hub order. Found →
  success (FR-015) with that route id. Not found or no fresh snapshot → "Couldn't reach the hub".
- A source start that the hub answers with an existing route (same source, same target) is a
  normal success: the route it returns is opened.

**Rationale**: implements the clarification of 2026-10-04. Hub status is the confirmation
(Constitution II), so a lost answer is not reported as a failure while the hub may be playing.

**Risk**: the hub may store a link's input `uri` differently from what was sent (e.g. after
resolving it). The match then fails and the user sees "Couldn't reach the hub" although the link
plays; Rooms shows it at the next refresh. Recorded as a check for on-device verification
([quickstart](quickstart.md) §2).

## R9. A start that outlives the screen (Close while in flight)

**Decision**: the request runs in an app-scoped `PlaybackStarter` (`ui/session/`, created in
`AppGraph`), not in `viewModelScope`. The view model calls `starter.start(request)` and observes a
`StateFlow<StartAttempt?>`. When the screen is closed mid-request (view model cleared), the starter
finishes the request, including R7/R8, and posts a failure through `AppMessages` ("Couldn't start
playback", etc.), which Rooms already collects. On success after Close nothing is posted: Rooms
shows the playback at the next refresh. Only one start runs at a time; Play is disabled while one
is in flight (FR-014).

**Rationale**: the spec edge case requires the request to continue and failures to reach Rooms.
`AppMessages` exists for exactly this (002 R4).

**Alternatives considered**: `NonCancellable` inside `viewModelScope` (rejected: no owner left to
post the message, and it hides the lifetime).

## R10. Opening Now Playing for a playback the snapshot does not know yet

**Decision**: on success the back stack **replaces** Start Playback with
`Destination.NowPlaying(routeId, startedAfterSeq, targetName)` (new `AppBackStack.replaceTop`). The
Now Playing view model uses the existing fence: a missing route counts as ended only once a
refresh that started after `startedAfterSeq` has arrived. `targetName` seeds `lastTargetName`, so
an early end reads "Playback on <target> ended" (FR-015). The starter calls `requestRefresh()`
right after success, so the new playback shows within one round trip (SC-004). The fence and name
are not saved across process death (as in 002 R3: after a restore, a fresh snapshot decides), so
`encodeDestination` still writes `now:<id>`.

**Rationale**: without the fence, the first snapshot (taken before the start) lacks the new route
and Now Playing would announce "ended" immediately.

**Alternatives considered**: waiting on Start Playback for the route to appear before navigating
(rejected: adds up to 2.5 s with "Starting…" and duplicates the fence logic Now Playing already has).

## R11. Link normalisation and the inline message (FR-006)

**Decision**: one pure function `checkLink(text): LinkCheck` in `domain/LinkAddress.kt`
(`Empty`, `Invalid`, `Valid(uri)`). It trims the text, prepends `https://` when there is no
`scheme://`, and accepts only `http`/`https` with a non-empty host, no whitespace, and a host made of
letters, digits, `.`, `-`, or an IPv6 literal in brackets, optional `:port`. Single-label hosts are
accepted (LAN stream servers). The view model holds `linkMessageShown: Boolean`. It is set on paste
(a change that inserts more than one character at once), on focus loss, or on the keyboard's
Done/Go when the text is `Invalid`, and cleared as soon as the text is `Valid` or `Empty`. Play is
disabled whenever the text is `Invalid` (clarification of 2026-10-04).

**Rationale**: a hand-written parser keeps `commonMain` free of JVM `java.net.URI`
(Constitution III). Paste detection by insertion length is what Compose's `onValueChange` allows
without platform clipboard hooks.

## R12. Screen state, selection rules and refresh

**Decision**: `StartPlaybackViewModel(initialTargetId, savedState, session, starter)`:
- acquires/releases the session while visible (same pattern as Rooms/Now Playing; FR-002);
- keeps `selectedSourceId`, `linkText` and `selectedTarget` in `SavedStateHandle` (rotation and
  process death);
- on every snapshot rebuilds the content, deselects a source that is gone or turned off and a
  target that is gone or unselectable (spec edge case); a preselected room that is not selectable
  on the first snapshot is dropped silently;
- FR-005: setting a non-empty link clears the source; picking a source clears the link;
- Play is enabled when (source or valid link) and target are set, the connection is Live, and no
  start is in flight; while in flight, selections are locked (FR-014).

The screen layout: a scrollable body (link field, source list, target grid) and a fixed footer
(consequence line, notes, Play button). The design has no scroll because its sample fits; real hubs
may list more sources.

**Rationale**: matches 002's view-model shape, so tests reuse `FakeHub` and virtual time.

## R13. Dependencies

**Decision**: none new. `ModalBottomSheet` is not needed. The screen uses `foundation` lazy
layouts and the existing theme. Constitution VII: nothing to justify.
