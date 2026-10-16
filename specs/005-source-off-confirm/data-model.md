# Data Model: Confirm Before Turning Off a Playing Source (005)

Changes to 004's domain types only ([004 data model](../004-settings-sources/data-model.md)). No
hub-facing type changes; `HubSnapshot`, `Source` and `Route` are as they are.

## Changed: `ConfiguredSourceRow` (`domain/SettingsBuilder.kt`)

| Field | Type | Meaning |
|---|---|---|
| id, name, enabled, kind, detail | unchanged | as in 004 |
| **playingIn** | `List<String>` | Rooms and groups where a live playback uses this source, each named once, in the hub's route order. Empty when nothing plays it **or the source is off** ([R4](research.md#r4-the-playing-in-line-on-configured-source-rows)) |

Rule: `playingIn = if (source.enabled) sourcePlaces(source.id, snapshot) else emptyList()`.

## New: `Confirmation.TurnOffSource` (`domain/SettingsConfirm.kt`)

| Field | Type | Meaning |
|---|---|---|
| source | `String` | The source's name, for the title and the body |
| where | `List<String>` | `sourcePlaces(...)`, never empty |

Produced by `turnOffConfirmation(key, snapshot)` for `ItemKind.Source` when the source is listed,
is on and `sourcePlaces` is not empty; null otherwise, which means "send at once"
([R3](research.md#r3-the-turn-off-confirmation-for-a-source)).

## New: `sourcePlaces(sourceId, snapshot)` (`domain/SettingsConfirm.kt`, internal)

The one rule for "where does this source play" ([R2](research.md#r2-one-rule-for-where-a-source-plays)):
live routes (`liveRoutes`: every status but Stopped) with `inputId == sourceId`, mapped to their
target's title (room or group name; `describeTarget`), distinct. Used by `removeConfirmation`,
`turnOffConfirmation` (sources) and `SettingsBuilder` (`playingIn`).

## Removed

| What | Where | Why |
|---|---|---|
| `keepsPlaying(sourceId, snapshot)` | `domain/SettingsConfirm.kt` | The hub no longer leaves playback running (FR-005) |
| `keepsPlayingMessage(name)` | `ui/settings/SettingsText.kt` | FR-005 |
| `keepsPlaying` parameter | `SettingsActions.setEnabled` | Always false after FR-005 |

## Dialog life cycle (unchanged except the source case)

```text
switch flipped off ─► turnOffConfirmation ─ null ─► SettingsActions.setEnabled(false) ─► hub
                                          └ TurnOffSource ─► dialog open, switch stays on
dialog open ─► each session update: refreshed by the rule of the open confirmation's type
            ─► "Keep playing" / Back / outside ─► closed, nothing sent
            ─► "Turn off" ─► SettingsActions.setEnabled(false) ─► hub stops the playbacks
```
