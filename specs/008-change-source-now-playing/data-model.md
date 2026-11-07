# Data Model: Change What Plays from Now Playing

**Feature**: [spec.md](spec.md) | **Research**: [research.md](research.md)

No hub type and no repository type changes. Everything below is app state in `commonMain`.

## Navigation

### `Destination.StartPlayback` (changed, `ui/nav/Destinations.kt`)

| Field | Type | Meaning |
|---|---|---|
| `targetId` | `String?` | Rooms' "Play in room": the room to preselect; the picker is shown. Null for "Play something" and for Change source |
| `fixedTarget` | `Target?` (new) | Change source: the playback's addressed target, `Target.Room` or `Target.Group`. Non-null ⇒ no picker, title "Change source" |
| `fixedName` | `String?` (new) | The target's name as Now Playing showed it. Not saved in the stack; the view model keeps it (R3) |

Saved form: `play`, `play:<id>` (unchanged), `change:room:<id>`, `change:group:<id>` (new). Any other
`change:` string decodes to null (dropped, as unknown strings are today).

### `AppBackStack` (changed)

| Member | Behaviour |
|---|---|
| `replaceTopTwo(d)` (new) | Removes the top two entries and pushes `d`; when that would remove the root, behaves as `replaceTop(d)` |
| `showStarted(d: NowPlaying)` (new) | Top is `StartPlayback` with `fixedTarget != null` and the entry below is `NowPlaying` → `replaceTopTwo(d)`; top is any other `StartPlayback` → `replaceTop(d)`; otherwise nothing (the screen already left) |

## Domain (`domain/`)

### `FixedTargetCard` (new, `domain/FixedTarget.kt`)

| Field | Type | Meaning |
|---|---|---|
| `target` | `Target` | As passed in |
| `name` | `String` | From the snapshot; else the known name; else the id |
| `status` | `TargetStatus` | As `StartPlaybackBuilder` would give the target's tile; `Removed` when it is not in the snapshot |
| `unusable` | `Unusable?` | Null ⇒ a start may be sent there |

`Unusable`: `TurnedOff`, `AllRoomsTurnedOff`, `NotConnected`, `NoRooms`, `Removed`.

`FixedTarget.of(snapshot, target, knownName): FixedTargetCard` — the only place that decides it:

| Target | Condition (in this order) | `unusable` |
|---|---|---|
| room | not in `snapshot.rooms` | `Removed` |
| room | `!enabled` | `TurnedOff` |
| room | `!available` | `NotConnected` |
| group | not in `snapshot.groups` | `Removed` |
| group | `!enabled` | `TurnedOff` |
| group | no known members | `NoRooms` |
| group | every member `!enabled` | `AllRoomsTurnedOff` |
| group | no member enabled and available | `NotConnected` |
| either | otherwise | null |

`status` reuses `StartPlaybackBuilder`'s per-target status (factored out so both share it).

### `TargetStatus` (changed, `domain/StartPlaybackBuilder.kt`)

- `GroupPlaying(sources: List<String>)` → `GroupPlaying(status: CardStatus, sources: List<String>)`.
  `status` is `cardStatus(...)` of the group's first non-announcement route; the picker tile text
  ignores it.
- New `Removed` (only produced by `FixedTarget.of`; never in `StartPlaybackContent.targets`).
  Not selectable.

### `ConsequenceLine.AlreadyPlaying` (unchanged type, new meaning)

Still decided only in `StartConsequence.of` (same source on the same **addressed** target). It now
also blocks Play (R4).

## View state

### `StartPlaybackUiState` (changed)

| Field | Type | Meaning |
|---|---|---|
| `fixed` | `FixedTargetCard?` (new) | Non-null in Change source mode once a snapshot exists; the screen draws the card in place of the picker and the title "Change source" |
| `changeSource` | `Boolean` (new) | Opened with a fixed target; true even before the first snapshot, so the title and layout do not jump |
| `selectedTarget` | `Target?` | In fixed mode always `fixedTarget` |
| `consequence` | `Consequence?` | Null also while `fixed.unusable != null` |
| `playEnabled` | `Boolean` | Adds: `consequence.line !is AlreadyPlaying`, and in fixed mode `fixed.unusable == null` |

Saved state keys (view model `SavedStateHandle`): existing `sourceId`, `target`, `link`,
`preselected`; new `fixedName` (last known name of the fixed target).

### `NowPlayingUiState` / Now Playing view model (changed)

- View model: private `visible` flag set by `onVisible` / cleared by `onHidden`; `onGone` does
  nothing while not visible; `onVisible` also requests a refresh (R6, FR-013).
- `NowPlayingContent.Playback` (domain, `NowPlayingBuilder.kt`) gains `changeSourceVisible:
  Boolean` = `route.target !is Target.Unknown`, beside `moveVisible` (FR-002).
- New pure `actionRowLayout(content: NowPlayingContent.Playback): ActionRowLayout`:

| Field | Meaning |
|---|---|
| `count` | 1 (Stop) + `pauseVisible` + `changeSourceVisible` + `moveVisible` |
| `compact` | `count == 4`: four equal columns, 64 dp buttons |

Enabled state is not part of it: the screen combines `controlsEnabled` with in-flight Stop/Move,
as for the other buttons.

`NowPlayingActions` gains `onChangeSource: () -> Unit`; `NowPlayingScreen` gains a matching
parameter that navigation wires to `push(StartPlayback(null, target, name))`.

## State transitions (one Change source)

```text
Rooms ─tap card→ [Rooms, NowPlaying(r1)]
      ─Change source…→ [Rooms, NowPlaying(r1), StartPlayback(fixed=T)]   r1 keeps playing (SC-005)
         ├─ Close / Back ──────────→ [Rooms, NowPlaying(r1)]               nothing sent (FR-009)
         ├─ Play, failure ─────────→ same stack, failure message (FR-010); r1 may be gone:
         │                            Close → NowPlaying(r1) leaves with "ended" as today
         └─ Play, success (r2) ────→ [Rooms, NowPlaying(r2)]               (FR-008, SC-004)
```
