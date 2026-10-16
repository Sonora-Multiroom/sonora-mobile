# UI Contract delta: Sources tab (005)

Changes to [004's Settings UI contract](../../004-settings-sources/contracts/settings-ui.md). Anything
not listed here is unchanged. No hub contract changes (see [research R1](../research.md#r1-contract-and-the-hub-behaviour)).

## Configured source row

| State | Second line | Style | Colour |
|---|---|---|---|
| On, used by a live playback | `Playing in <where>` | 13 sp (`body13`, as the rooms' status) | accent `#F2A541` |
| On, idle | `<Kind> · <detail>` or `<Kind>` (as 004) | 12 sp (`label12`, as 004) | textMuted `#9A9DA6` |
| Off | `<Kind> · <detail>` or `<Kind>` (as 004) | 12 sp (`label12`) | textMuted |

- `<where>`: the rooms and groups, each once, joined as `joinNames` joins them ("A", "A and B",
  "A, B and C").
- One line, ellipsis at the end. Styles as approved on the design canvas, version 19
  ([research R4](../research.md#r4-the-playing-in-line-on-configured-source-rows)).
- Tile, name, switch and row height are unchanged.

## Turn-off dialog for a source

Same component, layout and colours as the room and group dialogs (004 "Confirmation dialog").

| Part | Text |
|---|---|
| Title | `Turn off <source>?` |
| Body | `<source> is playing in <where>. Turning it off stops playback there.` |
| Dismiss button | `Keep playing` |
| Confirm button | `Turn off` |

Shown when the user flips a configured source's switch off and the row reads "Playing in …" at that
moment (the same rule). Back and a tap outside dismiss it.

## Copy removed

| Text | Was shown |
|---|---|
| `<name> is off. What's playing from it keeps playing.` | after turning off a source in use (004 FR-014) |

## Copy table additions (for `SettingsTextTest`)

| Input | Output |
|---|---|
| row playing in `["Kitchen"]` | `Playing in Kitchen` |
| row playing in `["Kitchen", "Downstairs"]` | `Playing in Kitchen and Downstairs` |
| `TurnOffSource("Radio Paradise", ["Kitchen"])` title | `Turn off Radio Paradise?` |
| same, body | `Radio Paradise is playing in Kitchen. Turning it off stops playback there.` |
| same, confirm label | `Turn off` |
