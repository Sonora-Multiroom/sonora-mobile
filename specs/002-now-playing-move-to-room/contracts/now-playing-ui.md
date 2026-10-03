# Contract: Now Playing screen and Move playback sheet (UI)

Visual references: [NowPlaying.dc.html](../../../design/screens/NowPlaying.dc.html) and
[Transfer.dc.html](../../../design/screens/Transfer.dc.html) (390×844), canvas linked in AGENTS.md.
Tokens are listed in AGENTS.md "Design". This contract fixes texts, states and labels. Sizes and
colours come from the design files and are repeated here only where the design has no token.

## Navigation

- Rooms card tap → push `Destination.NowPlaying(routeId)` (replaces the 001 placeholder, FR-001).
  The bottom bar is hidden, as for every detail destination.
- "Rooms" back control (44 dp, chevron + "Rooms", 15 sp, `#C9CBD1`) and system Back pop. With
  the sheet open, Back closes the sheet first.
- `exit = Stopped` → pop, no message. `exit = Ended(name)` → pop, and Rooms shows "Playback on
  <name> ended" in its snackbar (via `AppMessages`).
- Polling: `acquire()` while the entry is `STARTED`, `release()` otherwise (FR-003).

## Screen states

| State | Shown when | Content |
|---|---|---|
| Loading | no snapshot yet | back control only, subdued "Connecting to <address>…" |
| Stale | `Unreachable`, content present | content dimmed + banner "Can't reach the hub · showing last known state" (001 copy). Every control disabled, "Move to room…" disabled (FR-003, edge case) |
| Can't reach | `Unreachable`, no content | "Can't reach the hub", "Tried <address>. Retrying…" |
| Live | `Live`, `Playback` | layout below |

## Layout (top to bottom, FR-004)

1. **Back control** (above).
2. **Decorative panel**: 248 dp high, radius 28, margin 20 horizontal. Background, rings and
   centre come from the kind:
   - Stream: panel `#2A1F10`, rings accent at 14 / 24 / 38 % alpha (380 / 276 / 176 dp), 92 dp
     centre disc `accent` with a 42 dp `onAccent` kind icon, exactly as in the design.
   - Other kinds: panel = the kind's tile background (`kind.bg`), rings and disc = the kind's icon
     colour, icon on the disc = `background` (spec Assumptions).
   - "Live stream" badge bottom-left when `live`: 11 sp, 600, uppercase, letter-spacing 0.08 em,
     `#F2D3A4` on black 40 %, radius 8. Purely decorative: the panel is `clearAndSetSemantics {}`,
     and the badge text is announced through the subtitle row instead.
3. **Title**: source name, Sora 26 sp 600, letter-spacing −0.02 em, max 2 lines, ellipsis.
4. **Subtitle**: `"<Kind> · <detail>"` or `"<Kind>"` (research R7), 14 sp textMuted, 1 line.
5. **Status row**: chip (13 sp 600, 6 dp dot, radius full, padding 4×10) and "on <target>" (13 sp
   textMuted, 1 line, ellipsis on the member list):
   - Chip text and colours per status: Playing (accent on `#2A1F10`), Paused, Starting…, Stopping…,
     Couldn't play, Unknown. The non-Playing states reuse the 001 Rooms status colours.
   - Line: "on Bedroom", "on Downstairs · Living Room + Kitchen", plus " · Not connected" (FR-007).
6. **Actions** (centered, gap 40, 76 dp round buttons with 12 sp captions below):
   - **Stop**: always present (FR-009). Accent fill, onAccent icon, caption and label "Stop", as
     drawn.
   - **Pause / Resume**: only when `pauseVisible`, placed between Stop and Move. It uses the Move
     button's `surface` style because the design draws no pauseable variant, so it adds no new
     emphasis. Caption "Pause" / "Resume", label "Pause <target>" / "Resume <target>". Disabled
     (dimmed, not clickable) when `!pauseEnabled`.
   - **Move to room…**: only when `moveVisible`. `surface` fill, arrow icon, caption "Move to room…"
     (agreed label), label "Move to room".
   - Under the row, when `live`: "Live streams can't be paused" (12 sp textMuted, FR-011).
   - Every action is disabled while its own request is in flight (FR-013).
7. **Volume section** (margin 24 top, 20 horizontal, gap 8), absent when `volume == null`:
   - Main row: pill 52 dp (radius 26), speaker icon in accent, label = target name 14 sp 600,
     percentage 13 sp 600 tabular. Mute button 52 dp round on `surface`, icon speaker / muted
     speaker, label "Mute <target>" / "Unmute <target>" (FR-016).
   - Group: member pills in a 2-column grid, 44 dp (radius 22), label = room name 14 sp `#E4E3DF`,
     no icon, percentage right. Odd counts leave the last cell empty.
   - Pill a11y: "<name> volume" with a range of 0–100 (FR-027). A muted pill shows the muted icon
     (main) or the textMuted percentage (member) and ignores drags (FR-017). The pill fill is
     `accentContainer` on `surfaceRaised` and never looks like a progress bar (agreed detail).
   - Master mute on: the mute button is disabled and shows muted, and a line "All rooms are muted"
     (13 sp textMuted) appears under the pills (FR-018).
8. Snackbar for errors that do not leave the screen (`actionErrorMessage`).

Nothing else is drawn: no progress bar, position, metadata or artwork (FR-008).

## Move playback sheet

Opened by "Move to room…" when Live and `moveVisible`. Closes on Cancel, swipe down, scrim tap and
Back without any request (US3-7). It also closes when a refresh makes the playback not Playing
(FR-012), and after a confirm returns.

- Container `surface`, top radius 28, padding 10/20/28, handle 36×4 `#3A3E46`, scrim
  `rgba(5,6,8,0.72)`, gap 14.
- Title "Move playback" (Sora 22 sp 600). Subtitle "<source> · now on <target>" (14 sp textMuted).
  The target is the room or group name only, without members.
- Section legend "MOVE TO" (12 sp 600 uppercase, letter-spacing 0.08 em, textMuted).
  - Rooms: rows in [research R8](../research.md#r8-move-destinations-as-a-pure-function) order.
  - "GROUPS" legend in the same style, then group rows. Hidden when there are none. Its exact look
    follows the design update (research R12, gate).
- Row: min 58 dp, radius 14, padding 0 12, gap 12. A 38 dp tile (radius 10, `surfaceRaised`, icon
  `#C9CBD1`: speaker icon for rooms, group icon for groups). Label 15 sp 600 + note 12 sp (warning
  colour `#F2D3A4` for "will stop" notes, else textMuted). A radio button (20 dp, accent) on the
  right. Selected row: bg `#1F1A12`, inset ring 1 dp `#6B4C1F`. Unselectable: opacity 0.5, not
  clickable, radio disabled.
- The list scrolls when it doesn't fit. Below it, pinned: the note "Keeps playing while it moves,
  no restart" with a 16 dp check icon in `#5FD3C4`, 13 sp textMuted. Then the primary button
  (56 dp, radius 28, accent/onAccent, 16 sp 600, arrow icon) and Cancel (44 dp, 15 sp 500,
  `#C9CBD1`).
- Primary button: "Move to <ctaName>" when a destination is selected. With nothing selected it
  reads "Move" and is disabled (FR-023). It is also disabled while stale or while the move is in
  flight (FR-024).
- Accessibility: the list is a `selectableGroup`. Each row is `selectable(role = RadioButton)` with
  the content description "<label>, <note>", so TalkBack reads "Kitchen only, Living Room stops,
  radio button, not selected". Unselectable rows are announced as disabled.

## Accessibility summary (FR-027)

| Control | Label |
|---|---|
| Back control | "Rooms" (role button) |
| Stop | "Stop" |
| Pause / Resume | "Pause <target>" / "Resume <target>" |
| Move to room… | "Move to room" |
| Mute | "Mute <target>" / "Unmute <target>" |
| Main pill | "<target> volume" |
| Member pill | "<room> volume" |
| Sheet rows | "<label>, <note>" as radio buttons in one group |

All touch targets are ≥ 44 dp, and all text meets 4.5:1 on its background. `ContrastTest` gains
`#C9CBD1`, `#E4E3DF` and `#F2D3A4` on `surface` / `background`, and the kind icon colours used as
text-free decorations are exempt.
