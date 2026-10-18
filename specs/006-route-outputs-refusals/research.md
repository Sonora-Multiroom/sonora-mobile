# Research: Rooms a Playback Is Really On, and Plain Refusal Messages

Feature: [spec.md](spec.md) · Plan: [plan.md](plan.md)

No item in the Technical Context needed clarification. The items below record the decisions the
design rests on, read from the code on `006-route-outputs-refusals` (2026-10-18) and from
`api/openapi.json` (API 0.1.25).

## R1. Contract and the hub behaviour

- **Decision**: no new calls and no contract refresh. Use `RouteResponse.outputs` and the existing
  `ErrorResponse.reason` / `outputId`.
- **Rationale**: `api/openapi.json` is 0.1.25. `RouteResponse.outputs` is `array of string`,
  described as "Outputs the route plays on right now, in join order. A group route lists only the
  members it plays on". The generated model already has `outputs: List<String>? = null`. Missing
  from the body gives `null`; `[]` gives an empty list. `POST /routes`, `POST /play` and
  `POST /routes/{id}/transfer` list a 409 answer. `HttpClients.rejection()` already keeps
  `reason` and `outputId` from any RFC 7807 body. The new reasons (`OUTPUT_DISABLED`,
  `GROUP_DISABLED`, `INPUT_DISABLED`, `ALL_MEMBERS_DISABLED` since 0.1.22,
  `ROUTE_CHANGING_OUTPUTS` since 0.1.25) are confirmed in the hub's source and recorded in
  AGENTS.md. The contract's description of `reason` still names only the two older ones (hub
  backlog `error-reason-schema-description`).
- **Alternatives considered**: waiting for the hub to document the reasons in the schema. Rejected:
  the reasons are confirmed and the gap is descriptive only (spec Assumptions).

## R2. `Route.outputs` in the domain

- **Decision**: add `val outputs: List<String>? = null` to the domain `Route`. `toRoute()` maps it
  as `outputs?.filter { it.isNotBlank() }?.distinct()`, in the hub's order. `null` means the hub
  did not report it; an empty list stays empty.
- **Rationale**: the default `null` keeps every existing `Route(...)` in tests and fixtures valid
  and gives SC-005 for free: a route without the list behaves as before. Keeping `null` and `[]`
  apart costs nothing, and only the occupancy rule (R3) reads them. Both fall back the same way
  (FR-002).
- **Alternatives considered**: a non-null list with `emptyList()` for "absent". Rejected: it hides
  whether the hub reported the list, which the tests should be able to state.

## R3. One occupancy rule

- **Decision**: one internal function in `domain/PlaybackRules.kt`:

  `occupiedRooms(route, rooms, groups): List<Room>`. It returns the known rooms the hub lists in
  `route.outputs`, or, when the list is null, empty or names no known room, today's rule: the
  room of a room target, the known members of a group target, nothing for an unknown target. For a
  group target, the order is the group's member order, followed by listed rooms outside the group
  in the hub's order. For any other target, the order is the hub's.

  Every other rule that asks "which rooms does this playback occupy" calls it: `occupancy`,
  `routesByRoom` and the new `describeRoute` (R4).
- **Rationale**: FR-003 asks for one place. The group's order keeps card and heading names stable.
  The hub's list is in join order, so a member that rejoins would otherwise jump to the end
  ("Kitchen Speakers + Office Speaker" after a toggle instead of "Office Speaker + Kitchen
  Speakers"). Unknown rooms are dropped, as unknown group members are today. A listed room outside
  the addressed group is kept (spec Edge Cases: trust the list).
  For an unknown target type, the list is the only knowledge the app has of where it plays, so the
  rooms count as in use. The card still has no volume pill, because a drag cannot address it.
- **Alternatives considered**: (a) the hub's order everywhere. Rejected because names would reorder
  on rejoin. (b) Intersect the list with the group's members. Rejected: it contradicts "trust the
  list" and would hide a room the hub says is playing.

## R4. Describing a route, not just a target

- **Decision**: add `describeRoute(route, rooms, groups, masterMuted): TargetDescription`. It
  takes title, `isGroup` and `muted` from the target, as `describeTarget` does today. It takes
  `memberNames`, `volume` (the loudest), `adjustable`, `memberVolumes`, `notConnected`, `occupies`
  and `roomId` from `occupiedRooms`. `describeTarget` stays for things that are targets, not
  routes: Start Playback's chosen target, a group's name. Callers switch as follows:

  | Where | Today | 006 |
  |---|---|---|
  | `RoomsBuilder` cards, `inUse`, idle and Off lists | `describeTarget(route.target)` | `describeRoute(route)` |
  | `NowPlayingBuilder` heading (`TargetLine`) and `volumeSection` | target and group members | `describeRoute` / `occupiedRooms` |
  | `MoveDestinations` current rooms, "only" notes | `current.occupies`, group members | `occupiedRooms` (R5) |
  | `MoveDestinations` "will stop" of other routes | `occupancy` | `occupancy` (now list-based) |
  | `StartConsequence` "will stop" | `routesByRoom` | unchanged call, list-based |
  | `SettingsBuilder` Rooms tab "Playing · …" | `routesByRoom` | unchanged call, list-based |
  | `turnOffConfirmation` room | `routesByRoom` | unchanged call, list-based |
  | `turnOffConfirmation` group's "playing on <rooms>" | all group members | occupied rooms of the group's own live routes, in group order, falling back to the members when there are none |
  | Start Playback tiles, Settings Groups tab, `sourcePlaces` | group / target names | unchanged (they name targets, not rooms) |

- **Rationale**: FR-001 lists the screens. The group turn-off dialog states which rooms a playback
  plays on, so FR-001's general clause covers it, even though the list does not name it. Without
  it, the dialog would claim the music plays in a room that is off. Keeping `describeTarget` for
  chosen targets matters: Start Playback must still count every member of the group the user
  picks.
- **Alternatives considered**: changing `describeTarget` to take an optional outputs list.
  Rejected: every caller would have to decide what to pass, which is the drift FR-003 forbids.

## R5. Move sheet rows

- **Decision**:
  - Plain room rows leave out the rooms of the current playback and every member of the current
    group, so a member that is off appears once, as its "<room> only" row ("Turned off"), as today.
  - A "<member> only" row that is on and connected reads `OthersStop(occupied names minus the
    member)`. When that list is empty, it reads the new note `LeavesGroup(group name)`, with the
    text "Leaves <group>". It is not a warning (2026-10-18 clarification).
- **Rationale**: acceptance scenario 3. Excluding by occupied rooms alone would list Office Speaker
  twice, as "Office Speaker" and as "Office Speaker only". An empty `OthersStop` would print
  " stop".
- **Alternatives considered**: "Idle" (wrong, the room is playing), repeating the room's name
  (says nothing), no note line (rows of uneven height). The user chose "Leaves <group>".

## R6. Group volume

- **Decision**: no new code. A group card's `memberVolumes` and Now Playing's main pill
  `roomVolumes` come from `describeRoute` / `occupiedRooms`. So the pill shows the loudest room
  that is playing, and `VolumeDragController` + `GroupVolume.scale` touch only those rooms. Now
  Playing's member pills are the occupied rooms.
- **Rationale**: FR-005. The drag code takes its base from the model it is given; it needs no
  change.
- **Alternatives considered**: none.

## R7. Refusal messages

- **Decision**: in `ui/Messages.kt`, the one place copy is written:
  - A sealed `TurnedOff` with `Room(name)`, `Group(name)`, `Source(name)` and `AllRooms(group)`,
    one function `turnedOff(error, target, source, roomName): TurnedOff?` that maps a `reason`
    whatever the status, and `turnedOffMessage(TurnedOff)` with the four FR-006 texts.
    `OUTPUT_DISABLED` names the `outputId`'s room when known, else the chosen target (FR-009).
    `GROUP_DISABLED` and `ALL_MEMBERS_DISABLED` name the chosen target. `INPUT_DISABLED` names the
    source; with no source name (a link start), it returns null and the generic message stays.
  - `StartFailure.TurnedOff(what)` with `needsRefresh = true`. `startFailure` checks it before the
    status rules. `PlaybackStarter.fail` calls `session.requestRefresh()` when the failure needs
    one, on the screen or detached.
  - `actionErrorMessage` gains `roomName: (String) -> String? = { null }`. In the `Move` branch,
    `turnedOff` comes first, with the destination's name as the target and the source's name. Then
    `ROUTE_CHANGING_OUTPUTS` gives "Couldn't move <source> to <place>. Try again in a moment."
    Everything else is unchanged. The Move refresh already happens after every answer
    (`NowPlayingViewModel.onConfirmMove`).
  - The reason strings become constants (`RefusalReason`) next to `HubError` in
    `data/HubRepository.kt`, so the starter, the messages and the view model share them.
- **Rationale**: FR-006 to FR-011. Messages never include hub wording (Constitution V). Start
  messages keep their style (no full stop); move messages keep theirs. FR-007 asks for "the same
  message", so a turned-off refusal reads the same on both.
- **Alternatives considered**: a separate `MoveFailure` type like `StartFailure`. Rejected: moves
  only need a string, and `actionErrorMessage` already owns that copy.

## R8. One quiet retry of a move

- **Decision**: in `NowPlayingViewModel.onConfirmMove`, when the first answer is `Rejected` with
  `ROUTE_CHANGING_OUTPUTS`, wait `MOVE_RETRY_DELAY = 1.seconds` (`delay` in `viewModelScope`) and
  send the same transfer (same route id, same target) once more. Move stays in flight and the sheet
  stays open and busy until the final answer, which takes the normal path. A second
  `ROUTE_CHANGING_OUTPUTS` gives the "Try again in a moment." message. Any other answer is handled
  as a first answer (Unreachable, 404, turned off, success with its fence). No further retries.
- **Rationale**: FR-008 and user story 3. The hub clears the state "within moments" (AGENTS.md
  0.1.25). Closing Now Playing clears its view model and cancels the pending retry, so nothing
  reaches another screen (acceptance scenario 3 of story 3). A refresh during the wait cannot
  announce "ended", because `onGone` ignores refreshes while Move is in flight. Tests run on
  virtual time.
- **Alternatives considered**: (a) retry in the repository. Rejected: a delay and policy in the
  data layer would apply to starts too, which FR-008 does not ask for. (b) Run the move in the app
  scope, like starts. Rejected: moves are screen-bound today and the spec allows dropping the
  retry.

## R9. Design, documentation and version

- **Decision**:
  - The design gets one copy addition, the Move sheet's "Leaves <group>" note. It is recorded in
    the spec's 2026-10-18 clarification, as Constitution VI allows, and added to
    `design/screens/Transfer.dc.html` (sample row) and the canvas before the UI code.
  - The first commit sets `sonora.versionName=0.6.0-alpha` and `sonora.versionCode=6`.
  - Once implemented: in AGENTS.md, the "No rooms in a playback's answer" hub gap is noted as used
    by the app, the domain rule "what plays on output X" now reads `outputs`, and the five reasons
    are listed as mapped. The backlog item `refusal-reasons-and-route-outputs.md` and its INDEX row
    are deleted. `docs/testing/full-app-test.md` gets rows for the quickstart §2 checks, N6's
    notes are updated, and its header is bumped.
- **Rationale**: FR-013, the constitution's full-app-test gate, and the version rule in AGENTS.md.
- **Alternatives considered**: none.
