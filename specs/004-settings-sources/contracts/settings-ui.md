# Contract: Settings screen (UI)

Source of truth: `design/screens/Settings.dc.html` (390×844, updated 2026-10-04). This contract
lists what the implementation must match and where it departs. The departures are those in the
spec's Assumptions. Colours come from theme tokens only (`ui/theme/Tokens.kt`, new ones in
[research R12](../research.md#r12-ui-building-blocks)), never literals.

## Layout

```text
┌ Header ─────────────────────────────────────────────┐ padding 28 top, 20 sides
│ Settings                                            Sora 30 sp 600, -0.02em
├ Hub row (one button) ───────────────────────────────┤ margin 18 20 0; min-h 68; radius 20; surface
│ [tile 40, r12, surfaceRaised, Server icon textSoft]  padding 12 10 12 14; gap 12
│ Hub  ● Connected                                    15 sp 600 · status 12 sp 600 + 6 dp dot
│ http://multiroom.lan:8080                       [>] 13 sp textMuted, 1 line, ellipsis; chevron
├ Tab bar ────────────────────────────────────────────┤ margin 12 20 0; padding 4; radius 16; surface
│ [Rooms][Groups][Sources][Extensions]                4 equal columns, gap 4, h 40, radius 12,
│                                                     13 sp 600; selected bg outline + text,
│                                                     others transparent + textMuted
├ Tab content (scrolls) ──────────────────────────────┤ padding 16 20 0; gap 12
│ (per tab, below)
│ Sonora 0.4.0 · Alpha · Build 23 (abcdef)            footer at the END of the content (FR-001):
│                                                     12 sp textMuted, centred, padding 12 0 14
└ Bottom bar (unchanged; Settings highlighted) ───────┘
```

The design's footer sits between the content and the bottom bar. FR-001 puts it at the end of the
content, so it scrolls with long lists. Its wording and colour are the app's existing ones (spec
Assumptions).

### List card (all tabs)

The card is `surface`, radius 22 (card), padding 4 14. Rows are divided by 1 dp `rowDivider`
(none after the last row). Names of turned-off items use `textMuted` (dimmed, contrast-exempt).

| Tab | Intro / headings | Row | Line(s) |
|---|---|---|---|
| Rooms | "A room that's off can't start new playback." (13 sp, textMuted, 1.45) | min-h 64; tile 40 r12 surfaceRaised `Room` icon textSoft; name 16 sp 600; switch | status 13 sp: textMuted, or **accent** for "Playing · …" |
| Groups | "Groups play to several rooms in sync." | min-h 72; tile `GroupStack`; name 16 sp 600; switch | members 13 sp textMuted (1.35, ellipsis); optional "Playing · …" 13 sp accent |
| Sources | heading "FROM CONFIGURATION" (12 sp 600, +0.08em, upper, textMuted), card; heading "ADDED FROM APPS" (margin-top 4), card or the empty text | configured: min-h 60; kind tile 38 r10 (kind colours); name 15 sp 600; switch. Runtime: min-h 64, card padding 4 6 4 14; link tile; name 15 sp 600; trash button 44×44 textSoft | configured "<kind> · <detail>" 12 sp textMuted, 1 line, ellipsis; runtime added line 12 sp textMuted |
| Extensions | "Extensions are set in the server's configuration. This list is read-only." | min-h 60; name 15 sp 600; badge | connection 12 sp textMuted; badge: pill, padding 4 10, 12 sp 600, 6 dp dot |

Several runtime sources share **one card**, with dividers between them, like the other lists. The
design draws one sample row as its own card.

Badge colours: Active `positive` on `positiveContainer`; Rejected `danger` on `dangerContainer`;
Disabled, Inactive and Unknown `textMuted` on `surfaceRaised`.

Hub status colours: Connected `positive`; Not connected `danger`; Connecting… and Not set
`textMuted`.

### Switch

48×28 track, radius 14. Off: track `switchTrackOff`, thumb 22 `switchThumbOff` at left 3. On:
track `accent`, thumb `onAccent` at left 23. A 150 ms move. **The whole row toggles** and is the
switch for accessibility. While in flight it shows the requested value and ignores taps. While the
screen is stale it is disabled (the row is announced as disabled and dimmed to 0.5, exempt).

### Hub address sheet

`ModalBottomSheet`, `scrimColor = scrim`, container `surface`, top radius 28, padding 10 20 28,
gap 16. Handle 40×4 `outline`. Title row: "Hub address" Sora 20 sp 600 and Close 44×44
`Icons.Close` textSoft. "URL" label 13 sp 600 textSoft. Field: h 52, radius 14, 1 dp `outline`
border, `background` fill, 16 sp, placeholder "http://192.168.1.10:8080", URI keyboard, no
autocorrect. Hint 13 sp textMuted. Invalid address: the 001 message replaces the hint in
`warningText`.

Test status box (shown once a test ran): padding 12 14, radius 14, 14 sp 500, 8 dp dot.
- Checking…: `textSoft` on `surfaceRaised`
- Found: `positive` on `positiveContainer`
- Failed: `danger` on `dangerContainer`

It is announced as a live region.

Buttons: 2 columns, gap 10, h 52, radius 16, 15 sp 600. "Test connection" `text` on
`surfaceRaised`; "Save" `onAccent` on `accent`.

### Confirmation dialog (turn off, remove)

`Dialog`, content full width minus 24 dp margins, `surface`, radius 24, padding 24 20 20, gap 10.
Tile 44 r14 `dangerContainer` with `StopOutline` `danger`. Title Sora 20 sp 600 (margin-top 6).
Body 15 sp textSoft, 1.5 line height. Buttons (margin-top 10, 2 columns, gap 10, h 52, r16):
"Keep playing" `text` on `surfaceRaised`; "Turn off"/"Remove" `onDanger` on `danger`. The scrim
is the platform's dialog dim (R12).

## Copy

Made in `ui/settings/SettingsText.kt` from domain values. Error copy is in `ui/Messages.kt`.

| Value | Text |
|---|---|
| `RoomStatus.Off/NotConnected/Speaker` | "Off" / "Not connected" / "Speaker" |
| `RoomStatus.Playing(s)` | "Playing · " + s joined " + " |
| `RoomStatus.InGroups(g)` | "In " + g joined ", " |
| group members | joined ", "; none → "No rooms" |
| group playing | "Playing · " + sources joined " + " |
| configured line | "<kindLabel> · <detail>", or "<kindLabel>" when detail is null |
| added line | ["Off · "] + ["Added today 14:30" \| "Added yesterday 09:12" \| "Added 2 Oct 14:30"] + [" · removed when it stops"], with parts joined by " · " when the date is missing (R10) |
| runtime empty | "Nothing added. Links you play, and sources apps like DLNA add, show up here." |
| configured empty | "No sources in the hub's configuration." |
| extensions | badge "Active" / "Disabled" / "Rejected" / "Inactive" / "Unknown"; line "Couldn't be loaded" / "Turned off in configuration" / "Not in use" / "Connected" / "Disconnected" / "No connection needed" / "Connection unknown" |
| extensions off / empty | "Extensions are turned off in the hub's configuration." / "No extensions installed on the hub." |
| hub status | "Connected" / "Not connected" / "Connecting…" / "Not set" (address line "Set the hub address to start") |
| test | "Checking…" / "Hub found · 1 room" / "Hub found · N rooms" / "Can't reach the hub at this address" |
| room dialog | title "Turn off <room>?"; body "<sources joined ' and '> is playing in <room>. Turning the room off stops playback there." |
| group dialog | title "Turn off <group>?"; body "<sources joined ' and '> is playing on <members joined ', '>. Turning the group off stops playback in all of these rooms." |
| remove dialog | title "Remove <name>?"; body "<name> is playing in <where joined ' and '>. Removing it stops playback there."; button "Remove" |
| source turned off while in use | "<name> is off. What's playing from it keeps playing." |
| failures | FR-013 / FR-018 (contract [hub-repository.md](hub-repository.md)) |

"is playing" stays singular for several sources, as the spec writes it.

## States

| State | Hub row | Tab content |
|---|---|---|
| `Initial` | drawn without a status | nothing |
| No address | "Not set" | "Set your hub address" message (Rooms' `Message` block); its action opens the sheet |
| Loading, no snapshot | "Connecting…" | nothing yet |
| Live | "Connected" | lists, controls enabled |
| Unreachable with a snapshot | "Not connected" | Rooms' `StaleBanner` above the lists, which stay; switches and trash disabled |
| Unreachable, no snapshot | "Not connected" | Rooms' unreachable message |
| Extensions tab before its first answer | — | only the read-only note |

## Behaviour

- The tab bar selects `SettingsNavigator.tab` and the content swaps without animation. The bottom
  bar's Sources item opens Settings on the Sources tab.
- Polling: the screen acquires the session while visible (`repeatOnLifecycle(STARTED)`), as Rooms
  does. It attaches `SettingsActions` while visible.
- Messages: a snackbar at the bottom of the Settings content. Outcomes that arrive after leaving go
  to Rooms through `AppMessages` ([R7](../research.md#r7-where-messages-appear)).
- Back on Settings returns to Rooms (unchanged). Back with the sheet or the dialog open closes only
  that.
- Turning a switch off on a playing room or group opens the dialog and leaves the switch on.
  "Turn off" closes the dialog and starts the change. A trash tap on a source in use opens the
  remove dialog. Opening a dialog while the same item is in flight is impossible, because the row
  is locked.

## Accessibility (FR-023)

- Each row is a switch named after its item, with its state, and disabled when stale or in flight.
- The tab bar is a row of `Role.Tab` with `selected`, with the selected one announced.
- Hub row: "Hub connection: <status>, <address>. Change address", `Role.Button`. With no address:
  "Hub connection: Not set. Change address".
- Trash: "Remove <name>". While removing, it is disabled and the row line reads "Removing…".
- Sheet Close: "Close". The test status box is a polite live region.
- Dialog: `paneTitle` = the title. Title and body are read on open. Both buttons are ≥ 44 dp.
- Touch targets ≥ 44 dp everywhere: rows 60–72 and buttons 44–52. Each tab's pill is drawn at
  40 dp, but its touch area spans the bar's full 48 dp height, padding included.
