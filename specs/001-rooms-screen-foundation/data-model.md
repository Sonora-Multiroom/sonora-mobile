# Data Model: Rooms Screen Foundation

**Feature**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)

Three layers. Only the middle one is shared by screens and logic:

1. **Generated wire types** (`ai.sonora.mobile.hub.generated.*`): `OutputResponse`,
   `GroupResponse`, `RouteResponse`, `InputResponse`, `MasterMuteResponse`, `ErrorResponse`, …
   They are visible only to `data/`.
2. **Domain types** (`ai.sonora.mobile.domain`): immutable, non-null where the app needs a value.
   Produced by the repository's mapping functions.
3. **UI state** (`ai.sonora.mobile.ui.rooms`): what the Rooms screen draws. Produced by
   `RoomsBuilder` (pure) and decorated by `RoomsViewModel` (overrides, in-flight flags, connection).

## Domain types

### Room (from `OutputResponse`)

| Field | Type | From | Rule |
|---|---|---|---|
| id | `String` | `outputId` | outputs with a null/blank id are dropped |
| name | `String` | `displayName` | falls back to `id` when null/blank |
| volume | `Int` | `volume` | clamped 0..100, null → 0 |
| muted | `Boolean` | `muted` | null → false |
| enabled | `Boolean` | `enabled` | null → true (cannot assume "Off" without data) |
| available | `Boolean` | `available` | null → true |

### Group (from `GroupResponse`)

| Field | Type | From | Rule |
|---|---|---|---|
| id | `String` | `groupId` | null/blank → dropped |
| name | `String` | `displayName` | fallback `id` |
| memberIds | `List<String>` | `outputIds` | null → empty, order kept, duplicates removed |
| muted | `Boolean` | `muted` | null → false |
| enabled | `Boolean` | `enabled` | null → true |

### Source (from `InputResponse`)

| Field | Type | From | Rule |
|---|---|---|---|
| id | `String` | `inputId` | null/blank → dropped |
| name | `String` | `displayName` | fallback `id` |
| uri | `String?` | `uri` | |
| origin | `SourceOrigin` | `source` | `STATIC` → Configured, `EPHEMERAL` → Runtime, null/unknown → Configured |
| pauseable | `Boolean` | `pauseable` | null → false |
| enabled | `Boolean` | `enabled` | null → true |
| kind | `SourceKind` | derived | `inferSourceKind(origin, uri)`, see below |

### SourceKind: `Stream | LineIn | File | Link`

`inferSourceKind(origin: SourceOrigin, uri: String?): SourceKind` in `domain/SourceKind.kt` is the
**only** place kind is decided (FR-009, Constitution II):

1. `origin == Runtime` → `Link`
2. `uri` starts with `http://` or `https://` (case-insensitive) → `Stream`
3. `uri` starts with `file:`, or is a path (`/…`, `./…`, `../…`, `~/…`, a Windows drive path
   `X:\…` / `X:/…`, or a `\\` UNC path) → `File`
4. anything else, including null/blank → `LineIn`

`isLiveStream(pauseable: Boolean, uri: String?): Boolean` in the same file is the **only** place
a route is judged live (FR-008): `!pauseable` and `uri` starts with `http://` or `https://`
(case-insensitive). Origin is ignored, so an internet radio added at runtime is live while its
tile kind stays `Link`.

A route whose input the hub no longer lists is drawn with `inferSourceKind(Configured, null)` =
`LineIn` and the input id as name (spec Edge Cases). This follows FR-009 rule 4 literally.

### Route (from `RouteResponse`)

| Field | Type | From | Rule |
|---|---|---|---|
| id | `String` | `routeId` | null/blank → dropped |
| inputId | `String` | `inputId` | null → `""` |
| target | `Target` | `targetId` + `targetType` | `SINGLE_OUTPUT` → `Target.Room(id)`, `OUTPUT_GROUP` → `Target.Group(id)`. Null/unknown type → route dropped (can't place it) |
| status | `RouteStatus` | `status` | `Starting, Active, Stopping, Stopped, Failed, Unknown` (null/unknown → `Unknown`) |
| paused | `Boolean` | `paused` | null → false |
| pauseable | `Boolean` | `pauseable` | null → false |
| transferable | `Boolean` | `transferable` | null → false (not used in this feature) |

### HubSnapshot

`data class HubSnapshot(rooms: List<Room>, groups: List<Group>, routes: List<Route>,
sources: List<Source>, masterMuted: Boolean)`. One successful refresh = one snapshot.

### HubAddress

Value class around the normalised base URL string (see research R6). `HubAddress.parse(String)`
returns `Valid(HubAddress)` or `Invalid(message)`. Persisted by `HubAddressStore`.

### HubError / HubResult

See [contracts/hub-repository.md](contracts/hub-repository.md).

## Rooms state (pure join): `RoomsBuilder.build(snapshot): RoomsContent`

### Algorithm

1. `live = routes.filter { it.status != Stopped }` (FR-006). Failed and Unknown routes count as
   live: they have a card and occupy rooms.
2. For each live route, resolve occupied room ids:
   - `Target.Room(id)` → `{id}`
   - `Target.Group(id)` → that group's `memberIds` if the group is listed, else `{}`
3. `occupied = union of all occupied sets ∩ known room ids`.
4. One `NowPlayingCard` per live route (step 5). One `IdleRow` per room not in `occupied`
   (step 6). A room that is in a group listed as a member of a *non-routed* group is unaffected
   (spec: "occupied only by the group that actually has a route").
5. Card fields:
   - `key` = route id
   - `title` = room/group name, fallback target id
   - `isGroup` = target is a group
   - `memberNames` = names of the group's listed member rooms in group order; unknown members
     skipped. Joined with `" + "` in the UI and truncated with an ellipsis.
   - `sourceName` = source name, fallback `inputId`
   - `kind` = source kind (above)
   - `status` = `CardStatus` (below)
   - `volume` = room volume (single) or `max(member volumes)` over known members, 0 if none
     (FR-013a)
   - `memberVolumes: Map<String, Int>` (group only), the base for scaling
   - `muted` = `masterMuted || target.muted`. Room: `room.muted`, group: `group.muted`. Unknown
     target → `masterMuted` only.
   - `notConnected` = single room with `available == false`. Group: false. The groups API has no
     availability flag, and the member list already shows which rooms are in it.
   - `action` = `CardAction` (below)
6. Idle row fields: `roomId`, `name`, `state`:
   - `!enabled` → `TurnedOff` (takes precedence)
   - `!available` → `NotConnected`
   - else `NothingPlaying` (has the play action)
7. Sorting: cards by `title`, idle rows by `name`, both `lowercase()` compare, then id as a
   tiebreaker so equal names keep a stable order (FR-007, FR-010).
8. Header: `inUse = occupied.size`, `total = rooms.size` → "N of M rooms in use" (FR-011).
   `masterMuted` from snapshot.
9. `rooms.isEmpty()` → `RoomsContent.NoRooms`.

### CardStatus

Derived from `(route.status, route.paused, route.pauseable, source.uri, kind)`:

| Route status | Condition | Status | Text (single room) |
|---|---|---|---|
| Starting | | `Starting` | "Starting…" |
| Stopping | | `Stopping` | "Stopping…" |
| Failed | | `Failed` | "Couldn't play" |
| Unknown | | `Unknown` | "Unknown" |
| Active | `pauseable && paused` | `Paused` | "<Kind> · Paused" |
| Active | `isLiveStream(pauseable, source.uri)` | `LiveStream` | "Live stream" |
| Active | otherwise | `Playing` | "<Kind> · Playing" |

`<Kind>` = "Stream", "Line-in", "File", "Link". Group cards replace the kind prefix with member names:
"Living Room + Kitchen · Live stream", "Living Room + Kitchen · Paused". This matches the design's
"File · Paused" and "Living Room + Kitchen · Live stream". The text is assembled in one UI
function. `CardStatus` stays text-free so tests assert on the enum.

### CardAction (exactly one per card, FR-015/FR-016)

| Condition | Action | Enabled |
|---|---|---|
| status == Failed | `Stop` | yes |
| `!pauseable` | `Stop` | yes |
| pauseable, Active and paused | `Resume` | yes |
| pauseable, Active and not paused | `Pause` | yes |
| pauseable, Starting / Stopping / Unknown | `Pause` | **no** (FR-016) |

### Volume drag permission (FR-014a)

`canDrag = !muted` (muted already includes master mute). The ViewModel also requires
`connection == Live`.

## View-model state: `RoomsUiState`

```text
RoomsUiState
├── NoAddress                          # FR-003: no request is made
└── Connected(address)
    ├── connection: Loading | Live | Unreachable(lastSuccessAt?)   # FR-004
    ├── content: RoomsContent?          # last successful build; null before first success
    ├── volumeOverrides: Map<cardKey, Int>   # drag in progress or awaiting completion
    ├── inFlight: Set<ActionKey>        # (cardKey, Stop|Pause|Resume) and MasterMute
    └── message: UserMessage?           # one-shot snackbar text
```

### Transitions

| From | Event | To |
|---|---|---|
| any | address saved | `Connected(addr, Loading, content = null)` + immediate refresh |
| Loading / Live / Unreachable | refresh ok | `Live`, `content = build(snapshot)` |
| Loading | refresh failed | `Unreachable(null)`, content stays null → full "Can't reach the hub" state |
| Live | refresh failed | `Unreachable(lastSuccessAt)`, content kept but shown **stale**, all card controls + master mute disabled (FR-004) |
| Unreachable | refresh ok | `Live` (controls re-enable) |
| Live | drag start/move | override set, refresh results don't change that card's shown volume |
| Live | drag end → request done | override removed, refresh triggered |
| Live | action tapped | key added to `inFlight` (control disabled, FR-019), request sent |
| Live | action done | key removed. On failure `message` set (FR-018). Refresh triggered either way |
| foreground → background | | polling cancelled. In-flight action requests are allowed to finish |
| background → foreground | | immediate refresh, then every 2.5 s |

Displayed volume for a card = `volumeOverrides[key] ?: content.card.volume`.

## Settings state: `SettingsUiState`

`text: String`, `savedAddress: HubAddress?`, `error: String?`, `saved: Boolean`.
On save: `HubAddress.parse(text)`. If valid, store it and replace the text with the normalised
form. If invalid, set `error` (FR-001, US1 scenario 4).
