# Contract: Start Playback screen (UI)

Source of truth: `design/screens/StartPlayback.dc.html` (390×844). This contract lists what the
implementation must match and where it departs; departures are those in spec Assumptions.
Colours come from theme tokens only (`ui/theme/Tokens.kt`), never literals.

## Layout

```text
┌ Header ─────────────────────────────────────────────┐ padding 16 top, 12 sides; gap 4
│ [Close 44×44, Icons.Close, textSoft]  "Play something" (Sora 20 sp, 600)
├ Body (scrolls) ─────────────────────────────────────┤ padding 12 top, 20 sides; gap 10
│ PASTE A LINK               label 12 sp 600, +0.08em, uppercase, textMuted
│ ┌ link field ─────────────────────────────────────┐ h 52, radius 16, surface, padding 0 14, gap 10
│ │ [Icons.Link] SoundCloud, YouTube or stream URL   │ text 15 sp; placeholder textMuted
│ └─────────────────────────────────────────────────┘
│ Enter a web address (https://…)                     13 sp, warningText, only when shown (R11)
│ OR PICK A SOURCE           legend, margin-bottom 8
│ ┌ source row ─────────────────────────────────────┐ min-h 54, radius 14, padding 0 12 0 8, gap 12
│ │ [tile 38, r10, kind bg/fg] Name (15 sp 600)     │ kind label 12 sp textMuted
│ │                            Stream          (◉)  │ radio 20, accent
│ └─────────────────────────────────────────────────┘ rows gap 4
│ PLAY IN                    legend
│ ┌ target ─────────┐ ┌ target ─────────┐            2 columns, gap 8
│ │ Name 15 sp 600 ◉│ │ Bedroom        ○│            min-h 64, radius 14, padding 10 12
│ │ status 12 sp …  │ │ Idle            │            status textMuted, 1 line, ellipsis
│ └─────────────────┘ └─────────────────┘            unselectable: opacity 0.5
├ Footer (fixed) ─────────────────────────────────────┤ padding 12 20 28; gap 10
│ [⚠ 16] Radio Paradise will stop in Bedroom          13 sp, warningText
│ Won't play in Patio (not connected)                 13 sp, textMuted (same layout, no icon)
│ All rooms are muted                                 13 sp, textMuted
│ ┌ Play Jazz24 in Bedroom ───────────────────────┐   h 56, radius 28, accent bg, onAccent 16 sp 600
└─────────────────────────────────────────────────────┘
```

Selected rows and tiles use `selectedBg` with an inset 1 dp `selectedOutline` ring. Unselected
rows use `surface` with no ring. The footer shows only lines that apply: the consequence line
first, then `WontPlay`, then the mute note.

## Text (all from `StartPlaybackText.kt` / `Messages.kt`)

| Element | Text |
|---|---|
| Title | "Play something" |
| Link placeholder | "SoundCloud, YouTube or stream URL" |
| Link message | "Enter a web address (https://…)" |
| Empty source list | "No sources on the hub. Paste a link above." |
| Play button | "Play" · "Play <source> in <target>" · "Play link in <target>" · "Starting…" (one line, ellipsis in the middle part if needed) |
| Target status | [data-model](../data-model.md) `TargetStatus` |
| Consequence, notes | [data-model](../data-model.md) `ConsequenceLine`, `WontPlay`, `MuteNote` |
| Failure | [data-model](../data-model.md) `StartFailure`, shown in a snackbar as on Rooms |

## States

| State | Shows |
|---|---|
| No hub address | the Rooms "Set your hub address" block (reused composable), linking to Settings |
| Loading (no snapshot yet) | header, link field, and a quiet loading indicator in place of the lists. Play disabled |
| Unreachable with snapshot | the Rooms stale banner above the body, last lists, selections kept. Play disabled |
| Unreachable without snapshot | the Rooms "Can't reach the hub" block, link field kept. Play disabled |
| Starting | button "Starting…" in accent fill, not tappable. List rows, tiles and the field are not interactive |

## Interaction

- Close (and system Back) → exit `Closed`, which pops the entry. A start in flight continues (research R9).
- Tapping a source row selects it and clears the link field. Typing or pasting a non-empty link
  deselects the source (FR-005).
- Tapping a selectable target selects it (single choice). Tapping the selected one keeps it
  selected (radio semantics). Unselectable targets ignore taps.
- Keyboard action on the link field: Done. It hides the keyboard and may show the link message (R11).
- On success the entry is replaced by Now Playing (research R10).

## Accessibility (FR-019)

- Close: content description "Close". It is an `IconButton`, 44 dp.
- Link field: label "Paste a link". The message is attached as the field's error text.
- Source list and target grid: each is a `selectableGroup()`. Each item is `selectable(role =
  Role.RadioButton)` and announces "<name>, <kind>" or "<name>, <status line>". Unselectable
  targets are announced as disabled. The decorative radio dot and the kind tile are merged into
  the item.
- Rows ≥ 54 dp, tiles ≥ 64 dp, button 56 dp: all ≥ 44 dp.
- Contrast: `warning text` and `textMuted` on `background` must pass 4.5:1 (`ContrastTest` already
  covers both). Dimmed unselectable tiles are exempt (Constitution VI).

## Departures from the design (spec Assumptions)

- Nothing preselected, except the room that opened the screen.
- The body scrolls and the footer is fixed (the design's sample fits on one screen).
- The link message, muted notes, "already playing", "Starting…", empty, loading and stale states
  are not drawn in the design and reuse the styles above.
- A room played by a group is named by the group in the warning ("… will stop in Downstairs").
