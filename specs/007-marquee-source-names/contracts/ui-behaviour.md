# UI Behaviour: Scroll Long Source Names

**Feature**: [spec.md](../spec.md) | **Date**: 2026-10-20

## Where

| Place | Text | `maxLines` | Start delay | Stale from |
|---|---|---|---|---|
| Rooms, now-playing card | source name under the room title (`type.body14`, `primary`) | 1 | `marqueeStartDelay(index)` | card's `stale` |
| Now Playing | title (`cardTitle` 26 sp, `primary`) | 2 | 1 200 ms | `state.stale` |

Nothing else changes (FR-012): room/group titles, status lines, the Now Playing subtitle and target
line, the Move to room sheet, Start Playback, Settings. Colours stay as today, including the
stale `textMuted`.

## Behaviour

| Situation | Rooms card | Now Playing title |
|---|---|---|
| Name fits | one line, still, no "…" | one or two lines, still, no "…" |
| Name too long, fresh, focused, animations on | one line scrolls right to left: still for the start delay, 30 dp/s, 1.2 s pause between cycles, repeats while shown, 12 dp faded edges | same, as one line, only when two lines are not enough |
| Name too long, stale | one line, still, ends in "…" | two lines, still, ends in "…" |
| Window not focused (shade pulled down, another window in front) | as stale, until focus returns; then starts from the beginning with the start delay | same |
| App in background | the frame clock is paused; nothing moves | same |
| "Remove animations" on | as stale | as stale |
| Refresh with the same name | no change, the marquee keeps its position | same |
| Name changes | starts from the beginning with the start delay | same |
| Width changes (badge, font scale, rotation) | measured again; may switch layout; a marquee restarts | same |

"Another window" includes the app's own dialogs and sheets when Android gives them a window of
their own (the confirmation dialogs, and the Move to room sheet if it opens one): the name behind
them stops, and starts again from its beginning when they close. This is expected, not a defect.

## Accessibility

- The text node always holds the full name; TalkBack reads it once whatever the layout.
- No focus stop and no touch handling are added to the name.
- Contrast is unchanged (same colours). The faded edges only touch text that is moving or about to
  move, and do not affect the readable middle of the line.

## Design

The canvas draws names ending in "…". The scrolling layout is a recorded departure (spec FR-013);
the canvas is not changed. AGENTS.md "Agreed details" gains: "A source name too long for its line
scrolls as a marquee on the Rooms card and the Now Playing title (two lines first there); elsewhere
names end in '…'."
