# Data Model: Now Playing and "Move to room…"

Phase 1 of [plan.md](plan.md). Domain types from feature 001 (`Room`, `Group`, `Source`,
`SourceKind`, `Target`, `Route`, `RouteStatus`, `HubSnapshot`, `CardStatus`) are reused unchanged.
See [001 data-model](../001-rooms-screen-foundation/data-model.md). Everything below lives in
`commonMain` and contains no generated types (Constitution I).

## Session (`ui/session/`)

```kotlin
sealed interface SessionState {
    data object Initial : SessionState            // address not read yet
    data object NoAddress : SessionState          // no request is ever made
    data class Connected(
        val address: HubAddress,
        val connection: Connection,               // Loading | Live | Unreachable(lastSuccessAt), from 001
        val snapshot: HubSnapshot?,               // last successful refresh
        val refreshSeq: Long,                     // seq of the refresh that produced `connection`
    ) : SessionState
}
```

- `HubSession.acquire()` / `release()` maintain a holder count. The loop runs while the count is
  above 0 and an address exists. The address changing restarts the loop and clears `snapshot`.
- `refreshSeq` increments when a refresh **starts**. A consumer fences on it: "a refresh that
  started after X" means `seq > seqAtX` (research R3, R5).

`AppMessages`: `post(text: String)` plus a `messages` flow that is collected once (conflated buffer,
latest wins).

## Now Playing (`domain/NowPlayingBuilder.kt`)

```kotlin
sealed interface NowPlayingContent {
    data object Gone : NowPlayingContent          // route absent or STOPPED
    data class Playback(
        val routeId: String,
        val sourceName: String,                   // source name, else inputId
        val kind: SourceKind,
        val kindLabel: String,                    // "Stream" | "Line-in" | "File" | "Link"
        val addressDetail: String?,               // R7; null → subtitle is just kindLabel
        val status: CardStatus,                   // Playing | Paused | Starting | Stopping | Failed | Unknown (never LiveStream)
        val live: Boolean,                        // isLiveStream(pauseable, uri)
        val target: TargetLine,
        val pauseVisible: Boolean,                // route.pauseable
        val pauseEnabled: Boolean,                // status ∈ {Playing, Paused}
        val paused: Boolean,                      // status == Paused → button reads Resume
        val moveVisible: Boolean,                 // transferable && status == Playing
        val volume: VolumeSection?,               // null for Target.Unknown / target missing
    ) : NowPlayingContent
}

data class TargetLine(
    val target: Target,
    val name: String,                             // room/group name, else the id
    val memberNames: List<String>,                // group only, listed members in outputIds order
    val notConnected: Boolean,                    // room, or any listed member, available = false
)

data class VolumeSection(
    val main: PillModel,                          // room pill, or the group pill
    val members: List<PillModel>,                 // group only; one per listed member
    val mute: MuteModel,
    val masterMuted: Boolean,                     // → "All rooms are muted"
)

data class PillModel(
    val key: String,                              // "main" or "member:<roomId>"
    val label: String,                            // room / group name
    val roomVolumes: Map<String, Int>,            // hub volumes of the rooms this pill covers (drag base)
    val muted: Boolean,                           // masterMuted || target/room muted → not draggable
)

data class MuteModel(val target: Target, val name: String, val muted: Boolean, val enabled: Boolean)
```

What a pill shows is derived in the view model, not stored: `max(roomVolumes[id] overlaid with the
controller's pending[id])`, where pending values win (research R5).

**Validation / rules** (each one is a test in `NowPlayingBuilderTest`):
- A `STOPPED` or absent route → `Gone`. `FAILED` → `Playback` with status Failed, Stop only (pause
  hidden or disabled per `pauseable`, move hidden).
- A live stream → `status = Playing`, `live = true`. Line-in or file (non-pauseable) →
  `live = false`.
- The group's `memberNames` and `members` follow `GroupResponse.outputIds` order and skip unknown
  ids. A group with no known members → `volume = null`.
- `mute.muted = masterMuted || (room|group).muted`. `mute.enabled = !masterMuted`.
- A member pill's `muted = masterMuted || member.muted`. The main pill's
  `muted = masterMuted || target.muted`.

## Move destinations (`domain/MoveDestinations.kt`)

```kotlin
data class MoveSheetContent(
    val sourceName: String,
    val currentTargetName: String,                // "<source> · now on <target>"
    val rooms: List<Destination>,
    val groups: List<Destination>,                // empty → section hidden
)

data class Destination(
    val target: Target,                           // Target.Room or Target.Group only
    val label: String,                            // "Kitchen", "Kitchen only", "Downstairs"
    val ctaName: String,                          // "Kitchen" (also for "Kitchen only"), group name
    val kind: DestinationKind,                    // Room | MemberOnly | Group
    val note: DestinationNote,
    val selectable: Boolean,
)

sealed interface DestinationNote {
    data object Idle : DestinationNote
    data class WillStop(val sources: List<String>, val notConnected: List<String> = emptyList()) : DestinationNote
    data class WillStopOnGroup(val source: String, val group: String) : DestinationNote
    data class OthersStop(val rooms: List<String>) : DestinationNote
    data class Members(val rooms: List<String>, val notConnected: List<String> = emptyList()) : DestinationNote
    data object TurnedOff : DestinationNote
    data object NotConnected : DestinationNote
    data object NoRooms : DestinationNote
}
val DestinationNote.warning: Boolean get() = this is WillStop || this is WillStopOnGroup
```

`MoveDestinations.build(snapshot, routeId)` returns `null` when the route is gone, and an `Unknown`
target yields no destinations. Rules and ordering: [research R8](research.md#r8-move-destinations-as-a-pure-function).
The copy comes from `destinationNoteText(note)` (`ui/nowplaying/DestinationText.kt`), and
`joinNames(list)` (`"A"`, `"A and B"`, `"A, B and C"`) lives in `domain/Names.kt`.

**FR-025 test matrix** (`MoveDestinationsTest`, `DestinationTextTest`):

| # | Case | Expected |
|---|---|---|
| 1 | Idle room | `Idle`, selectable |
| 2 | Room with other single-room route | `WillStop([src])`, warning |
| 3 | Room in other group's route | `WillStopOnGroup(src, group)` |
| 4 | Group route, 2 members | "A only" → `OthersStop([B])` |
| 5 | Group route, 3 members | "A only" → `OthersStop([B, C])` → "B and C stop" |
| 6 | Disabled room | `TurnedOff`, unselectable |
| 7 | Unavailable room | `NotConnected`, unselectable. Disabled + unavailable → `TurnedOff` |
| 8 | Room ordering | selectable → only → unselectable, each case-insensitive |
| 9 | Free group | `Members([A, B])` → "A + B" |
| 10 | Group, one other route on a member | `WillStop([x])` |
| 11 | Group, two other routes (one on another group) | `WillStop([x, y])` → "x and y will stop" |
| 12 | Group overlapping the current target only | `Members`, nothing "will stop" |
| 13 | Disabled group | `TurnedOff`, unselectable |
| 14 | Group with 1 / 2 unavailable members | "… · P not connected" / "… · P and Q not connected", selectable |
| 15 | Current group excluded; single-room route lists all groups | |
| 16 | Group ordering | selectable alphabetical → unselectable alphabetical |
| 17 | Current room excluded (single-room route) | |
| 18 | Group with no known members | `NoRooms`, unselectable |

## View-model state

`NowPlayingUiState`:

```kotlin
data class NowPlayingUiState(
    val connection: Connection,
    val content: NowPlayingContent.Playback?,     // null until first data
    val pending: Map<String, Int>,                // per-room volumes from VolumeDragController
    val inFlight: Set<NowPlayingAction>,          // Stop | PauseResume | Mute | Move
    val sheet: MoveSheetState?,                   // null = closed
    val message: String?,                         // one-shot snackbar
    val exit: Exit?,                              // one-shot: Stopped (no message) | Ended(targetName)
)

data class MoveSheetState(val content: MoveSheetContent, val selected: Target?)
```

State transitions:

```
open ─▶ (first snapshot) ─▶ Playback ──Stop ok──────────▶ exit = Stopped
                              │  │  └─route gone (fenced)─▶ exit = Ended(name)  → AppMessages "Playback on <name> ended"
                              │  └─Move… (Live && moveVisible) ─▶ sheet(selected=null)
                              │        ├─ pick selectable ─▶ selected
                              │        ├─ refresh: selected unselectable/gone ─▶ selected=null
                              │        ├─ refresh: status != Playing ─▶ sheet=null (FR-012)
                              │        ├─ Cancel / swipe / Back ─▶ sheet=null
                              │        └─ Confirm (Live) ─▶ inFlight+Move
                              │              ├─ Ok(newRoute) ─▶ followedId = newRoute.id, fence seq, sheet=null
                              │              └─ Err ─▶ sheet=null, message
                              └─ Unreachable ─▶ stale content, all controls disabled, confirm disabled
```

The `followedRouteId` is kept in the view model (research R3).
