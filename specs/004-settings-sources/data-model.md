# Data Model: Settings Tabs and Sources (004)

Phase 1 of [plan.md](plan.md). It covers the domain and state types this feature adds or changes.
Types from 001–003 are unchanged unless listed here. Generated wire types stay in `data/`
(Constitution I; `verifyLayering`).

## Changed domain types (`domain/Models.kt`)

| Type | Change | Mapping from the hub (`data/ApiMapping.kt`) |
|---|---|---|
| `Source` | `+ autoRemove: Boolean = false` | `InputResponse.autoRemove`; missing → `false` |
| `Source` | `+ createdAt: kotlin.time.Instant? = null` | `InputResponse.createdAt` (string) via `Instant.parse`; missing, blank or unparseable → `null` ([R10](research.md#r10-added-when-and-the-time-zone)) |

## New domain types

### Extensions (`domain/Models.kt`)

```text
ExtensionInventory(loadingEnabled: Boolean, extensions: List<Extension>)
Extension(id: String, name: String, status: ExtensionStatus, connection: ExtensionConnection)
enum ExtensionStatus      { Active, Disabled, Rejected, Inactive, Unknown }
enum ExtensionConnection  { Connected, Disconnected, NotApplicable, Unknown }
```

Mapping rules are in [R8](research.md#r8-extensions-piggybacking-on-the-sessions-refreshes):
`INERT` → `Inactive`; missing/unrecognised → `Unknown`; blank name → id; blank id and name → dropped;
`loadingEnabled` missing → `true`. `rejectionReason` is not mapped.

### Settings content (`domain/SettingsBuilder.kt`)

`SettingsBuilder.build(snapshot, now: Instant, zone: TimeZone): SettingsContent`

```text
SettingsContent
  rooms:             List<RoomRow>            A→Z (case-insensitive), id tie-break
  groups:            List<GroupRow>           A→Z
  configuredSources: List<ConfiguredSourceRow> A→Z; origin Configured or Unknown
  runtimeSources:    List<RuntimeSourceRow>    createdAt newest first; undated last, A→Z

RoomRow(id, name, enabled, status: RoomStatus)
sealed RoomStatus
  Off                              !enabled (wins over everything)
  NotConnected                     !available
  Playing(sources: List<String>)   ≥1 live route covers it (own or group, any state but Stopped);
                                   source names in hub order, no duplicates
  InGroups(groups: List<String>)   group names containing it, A→Z
  Speaker                          none of the above

GroupRow(id, name, enabled, members: List<String>, playing: List<String>)
  members  = known rooms in memberIds order (unknown ids skipped; empty → "No rooms")
  playing  = source names of live routes with target == Group(id), only when enabled; else empty

ConfiguredSourceRow(id, name, enabled, kind: SourceKind, detail: String?)
  detail = sourceDetail(kind, uri)   (R11)

RuntimeSourceRow(id, name, enabled, added: AddedLine)
AddedLine(off: Boolean, at: AddedAt?, autoRemove: Boolean)
sealed AddedAt  Today(time) | Yesterday(time) | Earlier(day, month, time)   (local, 24 h)
```

`extensionRows(inventory): ExtensionsContent`

```text
sealed ExtensionsContent
  LoadingOff                           loadingEnabled == false
  Empty                                no extensions
  Rows(List<ExtensionRow>)             A→Z by name
ExtensionRow(id, name, badge: ExtensionBadge, line: ExtensionLine)
  badge: Active | Disabled | Rejected | Inactive | Unknown           (= status)
  line:  Rejected → CouldNotLoad; Disabled → TurnedOffInConfig; Inactive → NotInUse;
         else Connected | Disconnected | NoConnectionNeeded | ConnectionUnknown
```

### Confirmations (`domain/SettingsConfirm.kt`)

```text
ItemKind { Room, Group, Source }
ItemKey(kind: ItemKind, id: String)

Confirmation
  TurnOffRoom(room: String, sources: List<String>)
  TurnOffGroup(group: String, sources: List<String>, members: List<String>)
  Remove(source: String, where: List<String>)

turnOffConfirmation(key, snapshot): Confirmation?   null → send at once
removeConfirmation(sourceId, snapshot): Confirmation?
keepsPlaying(sourceId, snapshot): Boolean           a live route uses the source
```

| Situation | Result |
|---|---|
| Room on, covered by a live route (own or group) | `TurnOffRoom` |
| Room on, idle; room off (turning on) | `null` |
| Group on, own live route | `TurnOffGroup` (members as in `GroupRow`) |
| Group on, only member rooms play on their own | `null` (its own playback decides) |
| Group off (turning on) | `null` |
| Source switch | always `null` (FR-014): `keepsPlaying` decides the message instead |
| Runtime source, used by ≥1 live route | `Remove(where = route targets' names, hub order, no duplicates)` |
| Runtime source, unused | `null` |

### Source detail (`domain/SourceKind.kt`)

`sourceDetail(kind, uri): String?`. Stream → host. File → file name. Line-in → the address
without its `scheme://`, or as typed if it has none. Blank → `null`. Link is never asked for:
runtime rows show the added line instead.

## Data layer (`data/HubRepository.kt`)

New methods are described in [contracts/hub-repository.md](contracts/hub-repository.md):
`setRoomEnabled`, `setGroupEnabled`, `setSourceEnabled`, `removeSource`, `extensions`,
`countRooms`. `HubError` is unchanged.

## UI state

### `SettingsNavigator` (`ui/session/`, app-scoped)

```text
tab: StateFlow<SettingsTab>          SettingsTab { Rooms, Groups, Sources, Extensions }; starts Rooms
openSheetRequested: StateFlow<Boolean>
select(tab); openSheet(); consumeSheetRequest()
```

### `SettingsActions` (`ui/session/`, app-scoped)

```text
pending:  StateFlow<Map<ItemKey, Pending>>
Pending(value: Boolean, phase: InFlight | AwaitingRefresh(fence: Long))
removing: StateFlow<Set<String>>       source ids with a DELETE in flight
removed:  StateFlow<Set<String>>       ids hidden until a fenced refresh no longer lists them
messages: Flow<String>                 while attached; otherwise AppMessages
setEnabled(key, name, value, keepsPlaying: Boolean)
remove(sourceId, name)
attach() / detach()
```

State transitions of one switch:

```text
idle ──tap──► InFlight(value) ──Ok──► AwaitingRefresh(fence = startedSeq) ──refreshSeq > fence──► idle (hub decides)
                     │
                     └──Err──► idle + message (404: + requestRefresh)
```

A removal goes `idle → removing → (Ok | 404) → removed → idle` once a fenced snapshot no longer lists
the id. On another error it goes back to `idle` and shows a message. Every override is cleared when
the hub address changes.

### `SettingsUiState` (`ui/settings/SettingsUiState.kt`)

```text
SettingsUiState
  hub: HubRow(status: HubStatus, address: String?)
       HubStatus { Hidden, NotSet, Connecting, Connected, NotConnected }
  tab: SettingsTab
  body: Body
    Initial | NoAddress
    Lists(content: SettingsContent with overrides merged, stale: Boolean, controlsEnabled: Boolean)
    (Extensions tab: extensions: ExtensionsContent?   null before the first answer)
  rows carry: shownEnabled (override or hub), inFlight, removing
  confirm: ConfirmState?   (key + last Confirmation; text rebuilt per snapshot, last kept when null)
  sheet: SheetState?       (draft, error: String?, test: Idle | Checking | Found(n) | Failed)
  message: String?         one-shot snackbar
```

`controlsEnabled` = `connection == Live`. While stale, the lists keep the last snapshot and every
switch and trash button is disabled (spec Edge Cases). "Test connection" stays enabled.
