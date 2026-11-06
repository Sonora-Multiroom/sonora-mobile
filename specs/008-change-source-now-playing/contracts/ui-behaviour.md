# UI Contract: Change What Plays from Now Playing

**Feature**: [spec.md](spec.md) · Design: `design/screens/NowPlaying.dc.html`,
`NowPlayingFile.dc.html`, `StartPlaybackFixed.dc.html` · No hub call is added; the start is
Start Playback's existing `POST /api/v2/routes` or `POST /api/v2/play`.

## Now Playing: the action row

| Playback | Buttons, in order | Layout |
|---|---|---|
| live stream (not pauseable), movable | Stop · Change source… · Move to room… | 76 dp buttons, 28 dp icons, 24 dp gap, captions one line |
| not pauseable, not movable (e.g. failed) | Stop · Change source… | as above |
| pauseable, playing, movable | Stop · Pause · Change source… · Move to room… | 4 equal columns over the full width, 4 dp apart; 64 dp buttons, 24 dp icons; captions centred, may wrap to 2 lines |
| pauseable, paused (not movable) | Stop · Resume · Change source… | 76 dp row |
| unknown target type (newer hub) | Stop (plus Pause/Resume when pauseable); Change source… and Move to room… hidden | 76 dp row |

"Change source…": surface background, text-colour icon of two opposite arrows (`SonoraIcons.Swap`),
caption "Change source…" in `textMuted` 12 sp; spoken label **"Change source"**. Disabled (opacity
0.5, announced disabled) while the hub is unreachable or while a Stop or Move is in flight.
Tapping it sends nothing and opens Start Playback with the fixed target.

While Start Playback opened from it is showing, the covered Now Playing announces no "Playback on
… ended", whatever the cause; after Close it asks for a refresh at once and, if the playback is
gone, leaves with the usual notice on Rooms (FR-013).

## Start Playback opened from Change source

| Part | Contract |
|---|---|
| Title | "Change source" (from Rooms: "Play something", unchanged) |
| Link field, source list | As from Rooms; nothing selected on opening |
| "Play in" | Heading kept; one full-width card, no radio button, `surface`, radius 14, min height 64, padding 10/14: the target's name (15 sp semibold) and the status line (12 sp `textMuted`, one line, "…") from [research R5](../research.md#r5-the-fixed-target-cards-wording-fr-012). Not focusable as a control; read as "<name>, <status>" |
| Unusable target | Card at opacity 0.5; the note above Play in `textMuted` 13 sp (`Kitchen is turned off`, …, R5); Play disabled; no "will stop" line. When the target becomes usable again on a refresh, the note goes and Play follows the usual rules |
| Consequence line | As from Rooms: "<source> will stop in <target>" (warning colour, info icon), "Plays alongside …", "… will be lowered while it plays"; paused playbacks read the same |
| Play label | "Play <source> in <target>" / "Play link in <target>" / "Starting…" |
| Close / system Back | Return to the same Now Playing; a start in flight reports to Rooms (unchanged) |
| Success | Now Playing for the new playback replaces both screens; Back → Rooms |
| Failure | Existing failure snackbar; the screen stays |
| Restored by the system | Same fixed target, title and card, no picker |

## Start Playback, wherever it was opened from: "already playing"

| Selection | Footer | Play |
|---|---|---|
| source S already plays on the selected target T (addressed to T) | "S is already playing in T" (`textMuted`) | disabled, announced disabled, dimmed accent (`accent` at 0.38) |
| S plays on a group containing room T, not on T | "S will stop in <group>" | enabled |
| another source, or a valid link, on T | its usual line | enabled (if nothing else blocks it) |

## Accessibility

- New button: 64 or 76 dp, content description "Change source", role Button.
- The fixed card has no radio role: it is not a choice. Its name and status are one text node.
- Disabled Play keeps its label and is announced disabled; the "already playing" or FR-005 note is
  the text directly above it.
