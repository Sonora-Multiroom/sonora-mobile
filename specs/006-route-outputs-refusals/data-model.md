# Data Model: Rooms a Playback Is Really On, and Plain Refusal Messages

Feature: [spec.md](spec.md) · Decisions: [research.md](research.md)

This file lists only the changes to 001–005's model. Everything not named here is unchanged.

## Wire → domain

### `RouteResponse.outputs` → `Route.outputs`

| Wire (`RouteResponse.outputs`) | Domain (`Route.outputs: List<String>?`, default `null`) |
|---|---|
| missing | `null` (the hub did not report it) |
| `[]` | `[]` |
| `["kitchen", "", "kitchen", "office"]` | `["kitchen", "office"]`: blanks dropped, duplicates dropped, hub order kept |

No other wire field is newly mapped. `ErrorResponse.reason` and `outputId` already reach
`HubError.Rejected`.

### Refusal reasons

New constants in `data/HubRepository.kt`, next to `HubError` (R7):

| `RefusalReason` | Hub | Seen on |
|---|---|---|
| `ROUTE_LIMIT_REACHED`, `INPUT_ALREADY_ON_OUTPUT` | 0.1.21 (already mapped) | start |
| `OUTPUT_DISABLED`, `GROUP_DISABLED`, `INPUT_DISABLED`, `ALL_MEMBERS_DISABLED` | 0.1.22 | start, move |
| `ROUTE_CHANGING_OUTPUTS` | 0.1.25 | move |

## Occupancy (`domain/PlaybackRules.kt`)

### `occupiedRooms(route, rooms, groups): List<Room>` (new, the one rule, R3)

```
listed = route.outputs.orEmpty().mapNotNull { rooms[it] }
if listed is not empty:
    Group target → known group members in listed, in group order, then the other listed, in hub order
    other targets → listed, in hub order
else (outputs null, empty, or no known room):
    Room target    → [rooms[id]] if known, else []
    Group target   → known members of groups[id], in group order
    Unknown target → []
```

| Case | `outputs` | Result |
|---|---|---|
| Group G = [office, kitchen], all playing | `["kitchen", "office"]` | office, kitchen (group order) |
| G, office turned off (2026-10-17) | `["kitchen"]` | kitchen |
| G, list missing | `null` | office, kitchen |
| G, list empty (starting) | `[]` | office, kitchen |
| G, list has an unknown id | `["ghost", "kitchen"]` | kitchen |
| G, list only unknown ids | `["ghost"]` | office, kitchen (fallback) |
| G, list names a room outside G | `["kitchen", "bedroom"]` | kitchen, bedroom |
| Room target bedroom | `["bedroom"]` or `null` | bedroom |
| Unknown target | `["kitchen"]` | kitchen (no pill, R3) |

### Rules that now call it

- `occupancy(snapshot)`: room id → first live route. A route occupies `occupiedRooms(route)`.
- `routesByRoom(snapshot)`: room id → every live route covering it, from `occupiedRooms(route)`.
- `describeRoute(route, rooms, groups, masterMuted): TargetDescription` (new, R4):

  | Field | Source |
  |---|---|
  | `title`, `isGroup`, `muted` | the target, as `describeTarget` |
  | `memberNames` | group target: names of `occupiedRooms`. Otherwise empty |
  | `volume` | room target: the room's volume (0 when unknown). Group target: max over `occupiedRooms` (0 when none). Unknown target: `null` |
  | `adjustable` | room target: the room is known. Group target: `occupiedRooms` is not empty. Unknown target: false |
  | `memberVolumes` | group target: `occupiedRooms` id → volume. Otherwise empty |
  | `notConnected` | any of `occupiedRooms` has `available = false` |
  | `occupies` | ids of `occupiedRooms` |
  | `roomId` | room target: the room's id when known |

- `describeTarget` is unchanged and keeps its callers that describe a *chosen target*
  (`StartConsequence.chosen`, `MoveDestinations` header title, `sourcePlaces`, playback "where"
  names).

## Screen models

| Model | Field | 006 |
|---|---|---|
| `NowPlayingCard` | `memberNames`, `volume`, `volumeAdjustable`, `memberVolumes`, `notConnected` | from `describeRoute` |
| `RoomsContent.Rooms` | `inUse`, `idle` | from `describeRoute(...).occupies` |
| `TargetLine` | `memberNames`, `notConnected` | from `describeRoute` |
| `VolumeSection` (group) | `main.roomVolumes`, `members` | `occupiedRooms`, group order; `null` when none |
| `MoveDestinationNote` | **new** `LeavesGroup(group: String)` | not a warning; text "Leaves <group>" |
| `Confirmation.TurnOffGroup` | `members` | occupied rooms of the group's own live routes, in group order; the known members when no route reports any |

### Move sheet rows (R5)

- `current = occupiedRooms(route)`.
- Plain room rows: every room not in `current` and not a member of the current group.
- "<member> only" rows: one per known member of the current group, as today.
  - turned off → `TurnedOff`; not connected → `NotConnected`.
  - otherwise `others = current − member`: when not empty, `OthersStop(names of others)`; when
    empty, `LeavesGroup(group name)`.

## Refusals (`ui/Messages.kt`)

### `TurnedOff` (new)

| Case | Made from | Message (`turnedOffMessage`) |
|---|---|---|
| `Room(name)` | `OUTPUT_DISABLED`; name = `roomName(outputId)` ?: chosen target's name | "<room> is turned off" |
| `Group(name)` | `GROUP_DISABLED`; name = chosen target's name | "<group> is turned off" |
| `AllRooms(group)` | `ALL_MEMBERS_DISABLED`; group = chosen target's name | "All rooms in <group> are turned off" |
| `Source(name)` | `INPUT_DISABLED`; name = the source's name; **null** when there is none (link) | "<source> is turned off" |

`turnedOff(error: HubError, target: String, source: String?, roomName: (String) -> String?):
TurnedOff?` returns null for anything else. The reason decides whatever the HTTP status.

### `StartFailure`

- New case `TurnedOff(what: TurnedOff)`. `startFailureMessage` gives `turnedOffMessage(what)`.
- New `val needsRefresh: Boolean`, true only for `TurnedOff`.
- In `startFailure`, the order of checks is: `ROUTE_LIMIT_REACHED`, `INPUT_ALREADY_ON_OUTPUT`, then
  `turnedOff(...)`, then the status rules as today.

### Move (`actionErrorMessage(UserAction.Move(destination), source, error, roomName)`)

| Answer | Message |
|---|---|
| a `turnedOff(error, destination, source, roomName)` case | `turnedOffMessage(...)` |
| `ROUTE_CHANGING_OUTPUTS` (only after the retry, R8) | "Couldn't move <source> to <destination>. Try again in a moment." |
| unreachable | "Couldn't move <source> to <destination>. Can't reach the hub." (unchanged) |
| anything else | "Couldn't move <source> to <destination>." (unchanged) |

## State transitions: a move (R8)

```
Confirm ──► transfer #1 ──Ok──► follow id, fence, close sheet, refresh
                │
                ├─Err ROUTE_CHANGING_OUTPUTS──► wait 1 s (Move in flight, sheet open, busy)
                │                                  └─► transfer #2 ──Ok──► as Ok above
                │                                                ├─Err ROUTE_CHANGING_OUTPUTS──► close sheet, "Try again in a moment.", refresh
                │                                                └─Err other──► as "Err other" below
                └─Err other──► close sheet, actionErrorMessage, refresh
Screen closed during the wait: the view model is cleared and the retry is cancelled (no message anywhere).
```
