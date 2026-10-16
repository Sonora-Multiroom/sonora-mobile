# Research: Confirm Before Turning Off a Playing Source (005)

Phase 0 of [plan.md](plan.md). Each item gives a Decision, its Rationale and the Alternatives
considered. 004's research still applies and is referenced rather than repeated: the switch flow in
`SettingsActions` (004 R5), the confirmation rules decided once in `SettingsConfirm` (004 R4), the
row content decided once in `SettingsBuilder` (004 R3), and the one `HubSession` poll loop.

The Technical Context has no open questions: the spec's only clarification (a status line on
source rows) was answered on 2026-10-16.

## R1. Contract and the hub behaviour

**Decision**: No contract or repository change. `api/openapi.json` is 0.1.25 (refreshed
2026-10-16, PR #10). The feature uses only `PUT /api/v2/inputs/{id}/enabled` `{enabled}`, which
004 already calls through `HubRepository.setSourceEnabled`. Its 0.1.25 description says what the
spec relies on: disabling an input stops every route playing it, paused ones included, and an
input registered with auto-remove is then removed. Its answers are unchanged (200, 400, 404), so
004's failure mapping (FR-013) applies as it is.

**Rationale**: Constitution I. The hub change is behaviour, not wire format; the generated client
is the one 004 already uses.

**Alternatives considered**: a version check for hubs older than 0.1.25 (rejected, as for 004: the
hub has no capability flag, and the production hub runs 0.1.25).

## R2. One rule for "where a source plays"

**Decision**: Extract the place list that `removeConfirmation` computes today into one internal
function in `domain/SettingsConfirm.kt`:

```text
sourcePlaces(sourceId, snapshot): List<String>
  = live routes (status ≠ Stopped) whose inputId == sourceId,
    each described by its target's title (room name or group name),
    distinct, in the hub's route order
```

Three callers use it: `removeConfirmation` (unchanged behaviour), the new turn-off confirmation for
a source (R3), and the configured-source row's "Playing in" line (R4). A row reads "Playing in …"
exactly when the switch will ask, because both come from this function plus the same "is on"
check.

**Rationale**: FR-007 and FR-009 (one place; the line and the dialog agree). `liveRoutes` already
treats Playing, Paused, Starting, Failed and Unknown as live and Stopped as history, which is the
spec's "any state other than stopped" (FR-001) and matches what the hub stops.

**Alternatives considered**:
- Naming member rooms for a group route (as the group dialog does): rejected, the spec names a
  group by its own name, as the removal dialog does (Edge Cases).
- A second, row-only rule in `SettingsBuilder`: rejected, two rules could drift and a row would then
  promise a dialog that does not come.

## R3. The turn-off confirmation for a source

**Decision**:
- `Confirmation` gains `TurnOffSource(source: String, where: List<String>)`.
- `turnOffConfirmation(key, snapshot)` returns it for `ItemKind.Source` when the source is listed,
  is on, and `sourcePlaces` is not empty; otherwise null. Rooms and groups are unchanged.
- `SettingsText`: title "Turn off <source>?", body "<source> is playing in <where>. Turning it off
  stops playback there." (`joinNames`, as the removal body), confirm label "Turn off".
- The existing `ConfirmDialog` shows it; nothing about the dialog itself changes.

**Rationale**: FR-001–FR-003. The confirmation lives with the room, group and removal ones (004 R4),
so `SettingsViewModel.onToggle` needs no new branch: it already asks `turnOffConfirmation` for every
kind.

**Alternatives considered**: reusing `Confirmation.Remove` with a flag (rejected: the two lead to
different requests, and `onConfirm` dispatches on the type).

## R4. The "Playing in" line on configured source rows

**Decision**:
- `ConfiguredSourceRow` gains `playingIn: List<String>`: `sourcePlaces(id, snapshot)` when the source
  is on, otherwise empty. `SettingsBuilder` fills it.
- `SettingsText.configuredLine(row)` returns "Playing in <where>" (`joinNames`) when `playingIn` is
  not empty, else the kind line as today. One new predicate decides the colour: accent while
  playing, `textMuted` otherwise.
- `ConfiguredSourceSettingRow` keeps one line with an ellipsis. While playing, the line uses the
  rooms' status style (`body13`, accent), as `RoomSettingRow` does; idle, it keeps the kind line's
  `label12` in `textMuted`. Decided in the design review of 2026-10-16 (canvas version 19).

**Rationale**: FR-009. The kind stays visible through the tile's icon and colours, so replacing the
line while playing loses nothing a user needs at that moment.

**Alternatives considered**: a second line under the kind line, as group rows have (rejected: it
makes the row taller only while playing, so the list jumps on every refresh that starts or ends a
playback); the kind line's `label12` for the playing line too (the first canvas draft; the user
chose the rooms' 13 sp so playing rows read alike across tabs).

## R5. The dialog follows the hub while open

**Decision**: `SettingsViewModel.confirmationFor`, which refreshes the open dialog's text on each
session update, picks the rule by the open confirmation's type instead of by the item's kind:
`Remove` → `removeConfirmation`, anything else → `turnOffConfirmation`. When the fresh rule returns
null (the playback ended), the dialog keeps its last text, as for rooms and groups (004 Edge Cases).

**Rationale**: a source key can now open either a removal or a turn-off dialog; keyed by kind, an
open turn-off dialog would be refreshed into a removal dialog.

**Alternatives considered**: a separate `ItemKind` for configured sources (rejected: the key also
drives `SettingsActions`' pending map, which must not change).

## R6. Removing "keeps playing"

**Decision**: delete `keepsPlayingMessage` (SettingsText), `keepsPlaying()` (SettingsConfirm) and
the `keepsPlaying` parameter of `SettingsActions.setEnabled` with its use in `onToggle` and
`onConfirm`. The tests that asserted the message are rewritten to assert that turning a source off
reports nothing on success (FR-004, FR-005).

**Rationale**: FR-005. A parameter that is always false is dead code.

## R7. Design update first

**Decision**: before implementation, update the Claude Design canvas's Settings screen
(Sources tab): one configured source shown playing ("Playing in Living Room" in amber, in place of
its kind line), and the source's turn-off dialog in the existing dialog's style. Then copy the
canvas to `design/screens/Settings.dc.html`. This is a local task (the canvas needs the Artifact
tool and the user's review), listed first in `tasks.md`; implementation starts after it.

**Rationale**: FR-010, Constitution VI.

**Alternatives considered**: implementing first and updating the canvas afterwards (rejected by
Constitution VI).

## R8. Feature 004's spec, the version and dependencies

**Decision**:
- 004's spec is not edited (FR-008); 005's spec supersedes its source sentences.
- The first implementation commit sets `sonora.versionName=0.5.0-alpha`, `sonora.versionCode=5`.
- No new dependency.
- When 005 ships, `docs/backlog/source-off-stops-playback.md` is deleted and the backlog index
  updated (AGENTS.md "Backlog").

**Rationale**: AGENTS.md workflow; Constitution VII.
