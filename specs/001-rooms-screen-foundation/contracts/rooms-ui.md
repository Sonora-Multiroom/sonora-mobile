# Contract: Rooms screen and app shell (UI)

Visual reference: [design/screens/Main.dc.html](../../../design/screens/Main.dc.html) (390×844).
Tokens: CLAUDE.md "Design". This contract fixes texts, states and accessibility labels. Sizes and
colours come from the design file.

## Screen states (top to bottom priority)

| State | Shown when | Content |
|---|---|---|
| No hub set | `RoomsUiState.NoAddress` | Title "Rooms", message "Set your hub address", body "Rooms will appear here once the app knows where your hub is.", button "Open Settings" → Settings tab. No network request (FR-003) |
| Connecting | `Loading`, no content yet | Title, subdued "Connecting to <address>…" |
| Can't reach | `Unreachable`, no content | "Can't reach the hub", "Tried <address>. Retrying…", button "Open Settings" |
| Stale | `Unreachable`, content present | Normal content dimmed + banner "Can't reach the hub · showing last known state". Volume pills, Pause/Resume, Stop and master mute disabled. Card/row taps, "Play something" and navigation stay active (FR-004) |
| No rooms | `Live`, `NoRooms` | "Your hub has no rooms configured." |
| Live | `Live`, content | Header + sections below |

## Header

- Title "Rooms" (Sora 30 sp).
- Subtitle "N of M rooms in use" (DM Sans, textMuted). Uses "room" when M == 1.
- Master mute icon button, 44 dp, label "Mute all rooms" / "Unmute all rooms". Shows the active
  (accent) state when `masterMuted`. Disabled while in flight or stale.

## "Now playing" section (hidden when no cards)

Card (surface, radius 22). Tapping the card body opens the **Now Playing placeholder**:

- Kind tile (radius 12–14, kind colours) · title (Sora 17 sp) · "Group" badge for groups
- Source name
- Status line. For groups: member names joined with " + ", single line, ellipsis, then
  " · " + status. Texts per [data-model.md](../data-model.md) CardStatus. Single rooms that are not
  connected append " · Not connected".
- Volume pill (full width) + one action button (44 dp):
  - `Stop`: label "Stop <title>"
  - `Pause` / `Resume`: label "Pause <title>" / "Resume <title>". A disabled Pause is dimmed and
    not focusable as an action
- Pill: label "<title> volume". Muted → muted-speaker icon, percentage kept, not draggable.

## "Idle" section (hidden when no idle rooms)

Row per room, sorted by name:

| State | Secondary text | Trailing | Row action |
|---|---|---|---|
| NothingPlaying | "Nothing playing" | play icon button, label "Play something in <name>" | opens **Start Playback placeholder** for that room |
| TurnedOff | "Turned off" | "Off" label | none; row dimmed |
| NotConnected | "Not connected" | none | none; row dimmed (distinct icon/text from Off) |

A "Play something" button follows the Idle section as in the design and opens the
**Start Playback placeholder** with no target.

## Bottom navigation

Three items with icon and label: **Rooms**, **Sources**, **Settings**. The selected item uses the
accent colour. Tapping a selected item does nothing. Back from any placeholder returns to the
previous screen. Back from a top-level tab other than Rooms returns to Rooms. Back from Rooms
leaves the app.

## Placeholders

`Sources`, `Now Playing`, `Start Playback`: title + "Coming soon" + a one-line description of what
will be there. Placeholders opened from cards/rows have a back arrow labelled "Back".

## Settings (this feature)

- Title "Settings", field "Hub address" (placeholder "multiroom.lan:8080"), helper
  "The hub's name or IP on your home network", button "Save".
- Invalid input → inline error under the field. Valid input → field shows the normalised address,
  confirmation "Saved". Rooms then loads from it.
- Pre-filled with the saved address on launch.

## Accessibility (FR-023)

- All interactive elements ≥ 44 dp.
- Every icon-only control has the labels listed above.
- Text on its background ≥ 4.5:1 with the CLAUDE.md tokens. Dimmed (stale/off) content uses
  `textMuted` on `surface`, not alpha below that ratio.
