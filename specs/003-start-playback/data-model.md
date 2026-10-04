# Data Model: Start Playback (003)

Phase 1 of [plan.md](plan.md). It covers the domain types added or changed by this feature. Types
from [001](../001-rooms-screen-foundation/data-model.md) and
[002](../002-now-playing-move-to-room/data-model.md) are unchanged unless listed here. Generated wire
types stay in `data/` (Constitution I).

## Changed domain types (`domain/Models.kt`)

| Type | Change | Mapping from the hub (`data/ApiMapping.kt`) |
|---|---|---|
| `JoinMode` (new) | `enum { Replace, Mix, Announcement, Unknown }` | `REPLACE`, `MIX`, `DUCK_OTHERS`; anything else → `Unknown` |
| `Route` | `+ joinMode: JoinMode` | `RouteResponse.joinMode`; missing/unknown → `Unknown` ([R3](research.md#r3-join-modes-in-the-domain)) |
| `Source` | `+ defaultJoinMode: JoinMode?` | `InputResponse.defaultJoinMode`; missing → `null`. An unknown value is coerced to `null` by `HubJson` and cannot be told apart (recorded deviation, R3); both behave as Replace |

`Route.isAnnouncement` = `joinMode == Announcement` (extension in `PlaybackRules.kt`), so an
`Unknown` route is treated like a Replace route and named as stopping.

## Changed data types (`data/HubRepository.kt`)

| Type | Change |
|---|---|
| `HubError.Rejected` | `+ reason: String?` (`ROUTE_LIMIT_REACHED`, `INPUT_ALREADY_ON_OUTPUT`, or anything newer), `+ outputId: String?`. Both come from the RFC 7807 body, are `null` when absent, and are never shown as text |

## New domain types

### Shared rule (`domain/PlaybackRules.kt`)

`routesByRoom(snapshot): Map<String, List<Route>>`. For each room id, the live routes (status ≠
Stopped) that cover it, in hub order: a room route under its room, a group route under every known
member. Unknown-target routes cover nothing. `occupancy()` is unchanged.

### `LinkCheck` (`domain/LinkAddress.kt`)

```text
sealed LinkCheck
  Empty                    text is blank after trimming
  Invalid                  not an http(s) address with a valid host
  Valid(uri: String)       normalised: trimmed, "https://" prepended when no scheme
```

`checkLink(text)`. Rules in [R11](research.md#r11-link-normalisation-and-the-inline-message-fr-006).

### `StartWhat`, `StartNames` (`domain/StartRequest.kt`)

Plain types with no logic, shared by the starter (US1) and the consequence (US2).

```text
sealed StartWhat
  Source(id: String)
  Link(uri: String)        always a LinkCheck.Valid uri

StartNames                 captured by the view model when Play is tapped
  source: String?          the chosen source's name; null for a link (a link has no name yet)
  target: String           the chosen room's or group's name
```

`StartNames` lets the starter name things in failure copy and in the hand-off to Now Playing even
after they vanish from the snapshot (404, R7) or the screen has closed (R9). Room names for
`outputId` are looked up in the latest snapshot at failure time, not stored here.

`effectiveJoinMode(what, snapshot): JoinMode` lives in `domain/StartConsequence.kt` (US2). Link →
Replace. Source → its `defaultJoinMode`; `null` or `Unknown` → Replace. A source missing from the
snapshot also → Replace. Never returns `Unknown`.

### `StartPlaybackContent` (`domain/StartPlaybackBuilder.kt`)

```text
StartPlaybackContent
  sources: List<SourceOption>     turned-on sources, name A→Z (case-insensitive), id tie-break
  targets: List<TargetOption>     FR-007 order (below)

SourceOption
  id, name, kind: SourceKind      kind from inferSourceKind (001 FR-009)

TargetOption
  target: Target                  Room or Group only; Unknown types are not listed
  name: String
  status: TargetStatus
  selectable: Boolean

sealed TargetStatus                                      text (StartPlaybackText)
  Idle                                                   "Idle"
  Playing(status: CardStatus, sources: List<String>)     "<state> · A + B"  (LiveStream reads "Playing")
  InGroup(group: String)                                 "In <group>"
  GroupPlaying(sources: List<String>)                    "Group · A + B"
  GroupMembers(rooms: List<String>)                      "Group · Kitchen + Patio"
  TurnedOff                                              "Turned off"
  NotConnected                                           "Not connected"
  NoRooms                                                "No rooms"
```

Rules (FR-008/FR-009, [R5](research.md#r5-status-words-on-targets)):
- Room: disabled → `TurnedOff`. Else unavailable → `NotConnected`. Else its own non-announcement
  routes → `Playing` (status of the first). Else a group's non-announcement route → `InGroup`
  (the first group's name). Else `Idle`.
- Group: disabled → `TurnedOff`. Else no known members → `NoRooms`. Else no member both enabled
  and available → `TurnedOff` if every member is disabled, otherwise `NotConnected` (002 FR-020a).
  Else its own non-announcement routes → `GroupPlaying`. Else `GroupMembers(playable members)`.
- `selectable` = not `TurnedOff`, `NotConnected` or `NoRooms`.
- Order: selectable groups, selectable rooms, then every unselectable target. Each part is sorted by
  name A→Z (case-insensitive), with id as tie-break.

### `Consequence` (`domain/StartConsequence.kt`)

```text
Consequence
  line: ConsequenceLine?            null = nothing to say
  wontPlay: WontPlay?               group members that will not play
  mute: MuteNote?

AffectedPlayback(source: String, where: String)   where = the playback's own room or group name

sealed ConsequenceLine                                    colour     text (StartPlaybackText)
  WillStop(items: List<AffectedPlayback>)                 warning    "Jazz24 will stop in Office and Morning playlist in Kitchen"
  PlaysAlongside(items)                                   muted      "Plays alongside Jazz24 in Bedroom"
  WillBeLowered(items)                                    muted      "Jazz24 will be lowered in Bedroom while it plays"
  AlreadyPlaying(source: String, target: String)          muted      "Jazz24 is already playing in Bedroom"

WontPlay(turnedOff: List<String>, notConnected: List<String>)
                                                          muted      "Won't play in Patio (not connected)"; both kinds → two clauses
sealed MuteNote
  AllRoomsMuted                                           muted      "All rooms are muted"
  TargetMuted(name)                                       muted      "<target> is muted"
```

`StartConsequence.of(snapshot, what, target): Consequence`. Rules in
[R4](research.md#r4-start-playback-content-one-builder-one-consequence-function):
- Affected = de-duplicated routes from `routesByRoom` over the target's known member rooms (a
  room: itself), in hub order. Names joined with `joinNames` style ("A in X, B in Y and C in Z").
- `AlreadyPlaying` first (source only, exact same target, any mode).
- Effective mode Replace → `WillStop(affected − announcements)`. Mix → `PlaysAlongside(affected −
  announcements)`. Announcement → `WillBeLowered(affected)`. An empty list → `line = null`.
- `mute`: master mute → `AllRoomsMuted`. Else room muted, or group muted, or all members muted →
  `TargetMuted`.

### `StartFailure` (`ui/Messages.kt` holds the copy)

```text
sealed StartFailure
  RoomFull(room: String)              "<room> can't play more at once"
  AlreadyThere(source, room)          "<source> is already playing in <room>"
  LinkUnusable                        "The hub couldn't play this link"
  LinkUnreachable                     "Couldn't reach that link"
  ServiceDown                         "That service isn't available right now. Try again later."
  NoLongerOnHub(name)                 "<name> is no longer on the hub"
  HubUnreachable                      "Couldn't reach the hub"
  Other                               "Couldn't start playback"
```

Mapping table in [R6](research.md#r6-hub-errors-for-a-start).

`StartFailure.AlreadyThere` (a refusal, shown as a short message after Play) and
`ConsequenceLine.AlreadyPlaying` (a muted preview line before Play) deliberately share the wording
"<source> is already playing in <…>". They are separate types built in separate places
(`ui/Messages.kt` and `StartPlaybackText.kt`); do not merge them.

## UI state (`ui/startplayback/StartPlaybackUiState.kt`)

```text
StartPlaybackUiState
  address: HubAddress?                  null → "Set your hub address" state (spec edge case)
  connection: Connection                Live / Loading / Unreachable(lastSuccessAt) (001)
  content: StartPlaybackContent?        null before the first snapshot
  linkText: String
  linkMessageShown: Boolean             FR-006 / R11
  selectedSourceId: String?             FR-005: at most one of source / valid link
  selectedTarget: Target?
  consequence: Consequence?             only when something to play and a target are selected
  starting: Boolean                     FR-014: "Starting…", selections locked
  playLabel: PlayLabel                  Play | PlaySource(source, target) | PlayLink(target) | Starting
  playEnabled: Boolean                  FR-012
  message: String?                      one-shot failure text (FR-016)
  exit: StartExit?                      Closed | Started(routeId, startedAfterSeq, targetName)
```

### State transitions

```text
Idle selection ─pick source / type link / pick target─▶ Ready (playEnabled when connection Live)
Ready ─Play─▶ Starting
Starting ─Ok(route)─▶ exit = Started(route.id)  (back stack replaces this entry with Now Playing)
Starting ─Unreachable─▶ Recovering (still "Starting…") ─match─▶ Started / ─no match─▶ Ready + "Couldn't reach the hub"
Starting ─404─▶ Refreshing (still "Starting…") ─▶ Ready + "<name> is no longer on the hub"
Starting ─other error─▶ Ready + message
any ─Close/Back─▶ exit = Closed (a start in flight continues in PlaybackStarter, R9)
```

## Navigation (`ui/nav/Destinations.kt`)

- `Destination.NowPlaying(routeId, startedAfterSeq: Long? = null, targetName: String? = null)`.
  The two new fields are not encoded by the saver ([R10](research.md#r10-opening-now-playing-for-a-playback-the-snapshot-does-not-know-yet)).
- `AppBackStack.replaceTop(destination)`: swaps the top entry. It is used by Start Playback on
  success, so Back from Now Playing returns to Rooms (FR-015).
