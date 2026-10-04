# Feature Specification: Settings Tabs and Sources

**Feature Branch**: `004-settings-sources`

**Created**: 2026-10-04

**Status**: Draft

**Input**: User description: "004: Settings tabs + Sources. The on/off toggles for rooms, groups and
sources, deleting runtime sources, and the Extensions list." — the full Settings screen
(`design/screens/Settings.dc.html`), replacing the hub-address-only stub from feature 001.

## Clarifications

### Session 2026-10-04

- Q: The bottom bar's "Sources" tab is still a placeholder, and Settings gets a Sources tab. What does the bottom-bar tab do? → A: It opens Settings with the Sources tab selected; there is one sources list and no placeholder left (FR-003).
- Q: Removing a runtime source makes the hub stop every playback using it. Does the trash button confirm? → A: Only when the source is in use: an idle source is removed at once, a playing one asks first and names what will stop (FR-017).
- Q: Where does the hub address go, now that Settings has tabs? → A: A "Hub" row above the tabs showing the connection state and address; tapping it opens a "Hub address" sheet with "Test connection" and "Save", as in the updated design (FR-005–FR-008).
- Q: When a room, group or source is turned off while it plays, what happens to the current playback? → A: The hub stops it for rooms and groups: a turned-off room's own playback stops and the room leaves any group playback (which plays on in its other rooms); a turned-off group's playback stops. A turned-off source keeps playing until stopped. The group part needs a hub change not yet made (FR-014, Assumptions).
- Q: What does "Test connection" say when something answers at the address but it isn't the hub? → A: The same as no answer, "Can't reach the hub at this address"; only a reply that is the hub's room list counts as found (FR-007).
- Q: Should turning off a playing room or group ask first, now that the hub stops its playback? → A: Yes, only while it plays: a centred dialog "Turn off <name>?" names what will stop, with "Keep playing" and "Turn off"; idle ones and turning on never ask. Playing rooms and groups show "Playing · <source>" in amber so the user sees which switches will ask, as in the updated design (FR-009, FR-010, FR-014).
- Q: The hub's contract (re-fetched from `/api-docs` on 2026-10-04, still 0.1.21) says turning things off leaves existing playback running. How does the app handle a hub without the change? → A: It is written for the changed hub only: no version check and no fallback wording. The hub release is a precondition: `api/openapi.json` is refreshed from `/api-docs` once it ships, and before on-device verification (FR-014, Assumptions).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Turn rooms, groups and sources on and off (Priority: P1)

The user opens Settings, picks the Rooms, Groups or Sources tab and flips a switch to turn a room,
group or configured source off (so nothing new can start there or from it) or back on.

**Why this priority**: Turning a room off (a guest sleeping in it, a speaker being moved) or hiding
a broken stream is the main reason to open Settings, and Start Playback already relies on these
states.

**Independent Test**: Turn one room, one group and one source off in the app; check the hub
reports them turned off, that Rooms shows the room as "Off" and Start Playback no longer offers
them; turn them back on and check again.

**Acceptance Scenarios**:

1. **Given** the bottom bar, **When** the user taps Settings, **Then** Settings opens with the Hub
   row, the tab bar "Rooms / Groups / Sources / Extensions", and the Rooms tab selected.
2. **Given** the Rooms tab, **When** it is shown, **Then** it reads "A room that's off can't start
   new playback." and lists every room the hub has, on or off, each with its name, a status line
   and a switch that is on when the room is on; a room that plays (on its own or in a group) reads
   "Playing · <source>" in amber.
3. **Given** a room that is on, **When** the user turns its switch off, **Then** the switch moves at
   once, the hub turns the room off, the row's status line reads "Off" and its name is dimmed;
   Rooms shows the room as "Off" at its next refresh.
4. **Given** a room that is off, **When** the user turns its switch on, **Then** the hub turns it on
   and the row shows its usual status line again.
5. **Given** the Groups tab, **When** it is shown, **Then** it reads "Groups play to several rooms
   in sync." and lists every group with its name, its member rooms and a switch; switching works
   as for rooms.
6. **Given** the Sources tab, **When** it is shown, **Then** a "From configuration" section lists
   every configured source, on or off, with its kind tile, its name, a "<kind> · <detail>" line and
   a switch; switching works as for rooms.
7. **Given** the bottom bar, **When** the user taps Sources, **Then** Settings opens with the
   Sources tab selected and the bottom bar highlights Settings.
8. **Given** a room that is playing, **When** the user turns its switch off, **Then** a dialog asks
   "Turn off Living Room?" with "Radio Paradise is playing in Living Room. Turning the room off
   stops playback there." and the buttons "Keep playing" and "Turn off"; the switch stays on.
   "Keep playing" closes the dialog and changes nothing. "Turn off" turns the room off: its
   playback stops (it also leaves any group playback, which plays on in the group's other rooms),
   and Rooms shows the room "Off" at its next refresh.
9. **Given** a group whose playback is playing, **When** the user turns it off, **Then** the dialog
   reads "Radio Paradise is playing on Living Room, Kitchen. Turning the group off stops playback
   in all of these rooms."; "Turn off" turns the group off and its playback stops.
10. **Given** an idle room or group, or one that is off, **When** the user flips its switch, **Then**
   no dialog appears.
11. **Given** a switch whose request fails, **When** the hub answers with an error or cannot be
   reached, **Then** the switch returns to the hub's state and a short message says why.

---

### User Story 2 - Remove sources added at runtime (Priority: P2)

Links the user played and sources that apps (e.g. DLNA) added stay on the hub. The user sees them
under "Added from apps" on the Sources tab and removes the ones no longer needed.

**Why this priority**: Runtime sources accumulate and clutter Start Playback's source list; removing
them is the only cleanup available from the app. Less frequent than turning things on and off.

**Independent Test**: Play two links from Start Playback, stop one; on Sources › Added from apps
remove the stopped one (gone at once) and the playing one (confirmation, then its playback stops);
compare with the hub.

**Acceptance Scenarios**:

1. **Given** runtime sources on the hub, **When** the Sources tab is shown, **Then** an "Added from
   apps" section lists them newest first, each with the link tile, its name, an "Added <when>" line
   (plus " · removed when it stops" when the hub removes it by itself) and a trash button labelled
   "Remove <name>".
2. **Given** no runtime sources, **When** the Sources tab is shown, **Then** the section reads
   "Nothing added. Links you play, and sources apps like DLNA add, show up here."
3. **Given** a runtime source that nothing plays, **When** the user taps its trash button, **Then**
   the hub removes it and the row disappears, without a confirmation.
4. **Given** a runtime source that plays somewhere, **When** the user taps its trash button,
   **Then** a dialog in the same style asks "Remove <name>?" with "<name> is playing in <where>.
   Removing it stops playback there." and the buttons "Keep playing" and "Remove"; "Remove"
   removes it, its playback stops, and Rooms shows the room idle at its next refresh; "Keep
   playing" changes nothing.
5. **Given** a removal fails, **When** the hub answers, **Then** the row stays and a short message
   says why; a source the hub no longer has simply disappears.

---

### User Story 3 - Change and test the hub address (Priority: P2)

The user sees at a glance whether the app is connected to the hub and at which address, and can
change the address and test it before saving.

**Why this priority**: Already possible since feature 001; this story moves it into the new layout
and adds the connection test the design asks for.

**Independent Test**: Open the sheet, test a wrong address (fails), test the right one (finds the
hub with its room count), save, and check Rooms loads; restart the app and check the address
stays.

**Acceptance Scenarios**:

1. **Given** a saved address and a reachable hub, **When** Settings is shown, **Then** the Hub row
   reads "Hub", a "Connected" status, and the address (e.g. "http://multiroom.lan:8080").
2. **Given** the hub cannot be reached, **When** Settings is shown, **Then** the status reads "Not
   connected".
3. **Given** Settings, **When** the user taps the Hub row, **Then** a "Hub address" sheet opens
   with a "URL" field holding the saved address, the hint "Your phone and the hub need to be on the
   same network.", and the buttons "Test connection" and "Save".
4. **Given** the sheet, **When** the user taps "Test connection", **Then** a status reads
   "Checking…", then "Hub found · <N> rooms" or "Can't reach the hub at this address"; nothing is
   saved.
5. **Given** the sheet, **When** the user taps "Save", **Then** the address is normalised and saved
   as in feature 001, the sheet closes, and every screen uses the new address from its next refresh.
6. **Given** the sheet, **When** the user closes it, **Then** the draft is discarded.

---

### User Story 4 - See which extensions the hub runs (Priority: P3)

The user checks the Extensions tab to see whether, for example, the Home Assistant or Chromecast
extension is active and connected.

**Why this priority**: Read-only diagnostics; useful when an automation stops working, but rarely
needed.

**Independent Test**: Open the Extensions tab and compare every row with the hub's extension list.

**Acceptance Scenarios**:

1. **Given** the Extensions tab, **When** it is shown, **Then** it reads "Extensions are set in the
   server's configuration. This list is read-only." and lists every extension the hub found, each
   with its name, a connection line and a status badge.
2. **Given** an extension that connects to something (e.g. an MQTT broker), **When** its connection
   changes on the hub, **Then** the row's connection line follows at the next refresh.
3. **Given** the hub has extension loading turned off, **When** the tab is shown, **Then** it reads
   "Extensions are turned off in the hub's configuration."

---

### Edge Cases

- **No hub address saved**: the Hub row's status reads "Not set" and its address line "Set the
  hub address to start"; each tab shows "Set your hub address" instead of its list. Rooms' "Set your
  hub address" link opens Settings with the sheet already open.
- **Hub unreachable**: the last known lists stay, marked stale as on Rooms (feature 001 FR-004);
  every switch and trash button is disabled and re-enables on the first successful refresh. "Test
  connection" still works.
- **Turning off something that is playing**: for a room or group the app asks first and the hub
  then stops the playback (FR-014); for a source the playback continues and the app says so,
  without asking.
- **The playback ends while the dialog is open**: the dialog stays; "Turn off" still turns the room
  or group off, which is what the user asked for. When the playback changes source, the dialog
  text follows at the next refresh.
- **Room playing only through a group**: it reads "Playing · <source>" and asks before turning off,
  because turning it off takes it out of that group playback.
- **Room turned back on after leaving a group playback**: the hub does not add it back; the group
  keeps playing in its other rooms only. The app cannot see this (Hub gap below), so Rooms may show
  the room as playing in the group until that playback ends.
- **Turned off from elsewhere** (another app, Home Assistant): the switch follows at the next
  refresh, unless the user is changing it right now (the user's change wins until its request
  completes).
- **Room without hardware**: still listed with a switch; its status line reads "Not connected"
  ("Off" wins when it is also turned off).
- **Room in several groups**: the status line lists them all, "In Downstairs, Everywhere",
  truncated with an ellipsis.
- **Group whose members are unknown to the app or empty**: members the hub does not list are
  skipped; a group with none reads "No rooms".
- **Runtime source that is turned off** (e.g. by another client): its line starts with "Off · ".
  It has no switch, as in the design.
- **Runtime source removed by the hub** (it auto-removes when its playback ends) while the user
  looks at it: the row disappears at the next refresh; a trash tap on it then is treated as
  success.
- **A hub restart**: the hub keeps on/off changes only until it restarts; afterwards every room,
  group and source is back to its configured state, and the switches show that. The app does not
  warn about this (see Assumptions).
- **Several taps on one switch**: a switch cannot be tapped again until its request completes.
- **Leaving the screen while a request is in flight**: the request continues; its outcome shows on
  the next visit. A failure is reported as a short message on Settings while it is shown, otherwise
  on Rooms (the only screen reached from Settings); if the user has moved on from Rooms to another
  screen by then, it shows when Rooms is next visible.
- **Newer hub with unknown values**: an unknown extension status reads "Unknown" with the neutral
  badge; an unknown connection state reads "Connection unknown"; an unknown source origin is
  treated as configured (no trash button).

## Requirements *(mandatory)*

### Functional Requirements

**Layout and navigation**

- **FR-001**: Settings MUST replace the feature 001 stub: title "Settings", the Hub row, a four-way
  tab bar "Rooms / Groups / Sources / Extensions", the selected tab's content, and the version
  footer of feature 001 FR-021a at the end of the content.
- **FR-002**: Settings MUST open on the Rooms tab the first time; afterwards it reopens on the
  last tab the user chose, for as long as the app runs.
- **FR-003**: The bottom bar's Sources tab MUST open Settings with the Sources tab selected and
  highlight Settings in the bottom bar. The Sources placeholder screen is removed.
- **FR-004**: Settings MUST refresh on the same schedule and with the same timeouts as Rooms
  (feature 001 FR-005) while it is visible, and stop in the background. The lists show what the hub
  last reported; unreachable and no-address states behave as in Edge Cases.

**Hub row and address sheet**

- **FR-005**: The Hub row MUST show "Hub", a status ("Connected" while refreshes succeed, "Not
  connected" when the hub cannot be reached, "Connecting…" before the first answer, "Not set"
  without an address) and the saved address in normalised form, truncated with an ellipsis when it
  does not fit. The whole row is one control that opens the address sheet.
- **FR-006**: The "Hub address" sheet MUST offer a "URL" field (placeholder
  "http://192.168.1.10:8080"), the hint "Your phone and the hub need to be on the same network.", a
  Close control, "Test connection" and "Save". Validation and normalisation are those of feature
  001 FR-001–FR-002; an invalid address shows feature 001's message and is neither tested nor saved.
- **FR-007**: "Test connection" MUST check the drafted address without saving it: "Checking…" while
  in flight, then "Hub found · <N> rooms" (N = every room the hub lists, on or off; "1 room" in the
  singular) or "Can't reach the hub at this address". The hub counts as found only when the
  address answers with the hub's list of rooms; no answer, a timeout (the usual 3 seconds), an
  error, or a reply from some other server all read "Can't reach the hub at this address". Editing
  the field clears the result.
- **FR-008**: "Save" MUST save the address (no test required), close the sheet, and make every
  screen use it from its next refresh. Close and Back discard the draft.

**Rooms, Groups and configured Sources**

- **FR-009**: The Rooms tab MUST list every room the hub has, turned on or off, alphabetically
  (case-insensitive). Each row shows the room tile, the name (dimmed when off), a status line and
  a switch. The status line, decided in one place: "Off" when turned off; else "Not connected"
  without hardware; else "Playing · <source>" in amber (`accent`) when any playback covers the room,
  its own or a group's, in any state, sources joined with " + " when there are several; else "In
  <groups>" for the groups that contain it, alphabetical and joined with ", "; else "Speaker".
- **FR-010**: The Groups tab MUST list every group, turned on or off, alphabetically. Each row
  shows the group tile, the name (dimmed when off), its member rooms in the hub's order joined with
  ", " (truncated with an ellipsis; "No rooms" when none are known) and a switch. A group that is
  on and has its own playback adds a second line "Playing · <source>" in amber.
- **FR-011**: The Sources tab MUST show a "From configuration" section with every configured
  source, turned on or off, alphabetically. Each row shows the kind tile and colours, the name
  (dimmed when off), a line "<kind> · <detail>" and a switch. The kind comes from the existing
  single function (feature 001 FR-009); the detail, decided in one place, is the host name for a
  stream ("Stream · stream.radioparadise.com"), the file name for a file ("File · morning.flac")
  and the address without its scheme for a line-in. An empty section reads "No sources in the hub's
  configuration."
- **FR-012**: Flipping a switch MUST move it at once, ask the hub to turn that room, group or
  source on or off, and keep the user's value until the request completes and the next refresh
  confirms it; from then on the hub's state decides. While in flight the switch cannot be flipped
  again.
- **FR-013**: When a switch request fails, the switch MUST return to the hub's state and a short
  message says why: "<name> is no longer on the hub" (followed by an immediate refresh), "Couldn't
  reach the hub", or otherwise "Couldn't turn <name> off" / "Couldn't turn <name> on".
- **FR-014**: Turning off a room that plays (FR-009) or a group with its own playback (FR-010) MUST
  first open a centred dialog, decided in one place: title "Turn off <name>?"; for a room the text
  "<source> is playing in <room>. Turning the room off stops playback there."; for a group
  "<source> is playing on <member rooms>. Turning the group off stops playback in all of these
  rooms." (sources joined with " and " when there are several); buttons "Keep playing" and "Turn
  off". The switch stays on until "Turn off" is chosen; "Keep playing", Back or a tap outside close
  the dialog without a change. Turning on, and turning off an idle room or group, never ask. The
  hub then stops the playback: a room's own playback stops and the room leaves any group playback,
  which plays on in the group's other rooms (and stops when none are left); a group's playback
  stops. The app stops nothing itself, and Rooms and Now Playing follow at their next refresh. When
  a source is turned off while playbacks use it, nothing is asked: the app MUST show the short
  message "<name> is off. What's playing from it keeps playing.", because the hub leaves them
  running.

**Runtime sources**

- **FR-015**: The Sources tab MUST show an "Added from apps" section with every source added at
  runtime, newest first. Each row shows the link tile and colours, the name, the line "Added
  <when>" ("today 14:30", "yesterday 09:12", otherwise "2 Oct 14:30", in the phone's time zone),
  followed by " · removed when it stops" when the hub removes it by itself, and preceded by "Off · "
  when it is turned off; and a trash button. These rows have no switch. An empty section reads
  "Nothing added. Links you play, and sources apps like DLNA add, show up here."
- **FR-016**: Configured sources MUST NOT offer removal; only runtime sources have a trash button.
- **FR-017**: Tapping the trash button of a runtime source that no playback uses MUST remove it at
  once. When playbacks use it (in any state), the app MUST first ask in the FR-014 dialog style:
  title "Remove <name>?", text "<name> is playing in <where>. Removing it stops playback there."
  (<where> = each playback's room or group, joined with " and "), buttons "Keep playing" and
  "Remove". The check uses the hub's state at the time of the tap.
- **FR-018**: While a removal is in flight the row MUST show it is being removed and its trash
  button cannot be tapped. On success the row disappears. On failure the row stays and a short
  message says why: "<name> comes from the hub's configuration and can't be removed", "Couldn't
  reach the hub", otherwise "Couldn't remove <name>". A source the hub no longer has counts as
  removed.

**Extensions**

- **FR-019**: The Extensions tab MUST show the read-only note and list every extension the hub
  reports, alphabetically by name. Each row shows the name, a connection line and a status badge,
  decided in one place:
  - Badge: "Active" (teal), "Disabled" (neutral), "Rejected" (red), "Inactive" for an extension
    that loaded but provides nothing (neutral), "Unknown" for a value the app does not know
    (neutral).
  - Connection line: "Couldn't be loaded" for a rejected extension, "Turned off in configuration"
    for a disabled one, "Not in use" for an inactive one; otherwise "Connected", "Disconnected",
    "No connection needed" or "Connection unknown".
  - The hub's rejection reason is not shown as raw text.
- **FR-020**: When the hub reports extension loading as turned off, the tab MUST read "Extensions
  are turned off in the hub's configuration." instead of the list; an empty list reads "No
  extensions installed on the hub."

**Quality**

- **FR-021**: The following MUST be covered by automated tests: room status lines (off, not
  connected, both, playing on its own, playing through a group, several playbacks, off while
  playing, one group, several groups, no group); the group "Playing" line (own playback, idle,
  off); whether turning off asks (playing room, room playing through a group, playing group, idle
  room, idle group, turning on) and the dialog text for a room, a group and several sources; group
  member lines (hub order, unknown members skipped, none); source detail lines for each kind; the split into configured and runtime
  sources and their ordering (including unknown origin); the "Added <when>" wording across today,
  yesterday and older, with and without auto-removal and "Off · "; the switch flow (optimistic
  value, hub answer wins after completion, refresh does not override an in-flight change, every
  failure message); the "keeps playing" message (shown for a source in use, not for an idle
  source, never for a room or group); removal
  with and without a confirmation, with playbacks addressed to rooms and groups, and every failure
  mapping; extension badges and connection lines for every value including unknown ones and the
  loading-off and empty states; the Hub row status; the connection test result (hub found,
  "1 room", no answer, timeout, an error status, a reply that is not the hub's room list).
- **FR-022**: The screen MUST use the design's colours, typography and layout
  (`design/screens/Settings.dc.html`, updated 2026-10-04 with the Hub row and sheet, the "Playing"
  lines and the turn-off dialog); departures are listed under Assumptions.
- **FR-023**: Every touch target MUST be at least 44 dp and text MUST meet 4.5:1 contrast; dimmed
  names of turned-off items are exempt (Constitution VI). Each switch is announced as a switch
  named after its item, with its on/off state; the tabs are announced as tabs with the selected
  one; the Hub row reads "Hub connection: <status>, <address>. Change address"; trash buttons read
  "Remove <name>"; the sheet's Close control reads "Close"; the turn-off and removal dialogs are
  announced as alert dialogs with their title and text.

### Key Entities

- **Room (output)**, **Group**, **Source (input)**: as in features 001–003; here their on/off
  state is changed. A source is either configured (from the hub's configuration, can be turned on
  and off, never removed) or added at runtime (a played link, a DLNA source; can be removed).
- **Extension**: a hub plug-in, with a name, a status (active, disabled, rejected, inactive) and a
  connection state (connected, disconnected, not applicable). Read-only.
- **Hub connection**: the saved address and whether the hub currently answers.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: From Rooms, the user turns a room off in two taps (Settings, the room's switch).
- **SC-002**: A switch change is reported by the hub, and shown on Rooms, within 3 seconds of the
  tap.
- **SC-003**: In testing against the real hub, every switch and every status line on the Rooms,
  Groups, Sources and Extensions tabs matches what the hub reports.
- **SC-004**: No playback is stopped from Settings without the user confirming it: every turn-off
  of a playing room or group and every removal of a runtime source in use asks first, and every
  idle one takes a single tap.
- **SC-005**: The user can tell whether the hub is reachable, and test a new address, without
  leaving Settings.
- **SC-006**: The installable Android app builds and all automated checks pass, including the
  FR-021 tests.

## Assumptions

- **Hub behaviour (checked in the hub source on 2026-10-04)**: turning anything off refuses new
  playback. Turning a room off also stops its playback and drops it from group playback (hub branch
  `output-enabled-state`, not yet released); turning it back on does not add it back. Turning a
  source off leaves current playback running. The on/off change is kept
  in the hub's memory, so a hub restart restores every configured state. Removing a runtime source
  stops every playback that uses it, and configured sources cannot be removed (the hub refuses).
  The extension list is captured when the hub starts; only the connection states change later.
- **Dependency (hub change)**: stopping a group's playback when the group is turned off is not yet
  in the hub (its branch keeps the old rule for groups) and is to be added there, together with the
  room change above, before this feature is verified on the device. The app does not depend on it
  to build: it only reflects what the hub reports. The app targets the changed hub only: it does
  not check the hub version and has no fallback wording for older hubs (the contract on 2026-10-04,
  0.1.21, still says "Existing active routes are unaffected"). Once the hub change ships,
  `api/openapi.json` MUST be refreshed from the hub's `/api-docs` and reconciled (Constitution I)
  before on-device verification; against an older hub the turn-off dialog is knowingly inaccurate.
- **Hub gap**: a playback reports the group it was addressed to, not the rooms it actually plays
  in, so after a room leaves a group playback the app still counts it as part of it (Edge Cases).
  Recorded in AGENTS.md "Hub gaps"; the fix (rooms in the playback's answer) belongs in the hub's
  backlog.
- Configured sources come first in the Sources tab because they are the ones the user manages;
  runtime sources are shown for cleanup only and get no switch, as in the design.
- The app does not warn that on/off changes are lost on a hub restart; this is hub behaviour
  outside the app's control. It may be raised in the hub's backlog.
- Removing a playing runtime source is the hub's way to stop it for good; Stop on Rooms or Now
  Playing remains the way to stop without removing.
- Departures from the design (called out per the constitution):
  - Rows are sorted alphabetically (the design's sample order is arbitrary), matching Rooms and
    Start Playback.
  - The design's footer sample ("Sonora 0.3.0 Alpha - Build 23 (abcdef)", in `#8A8D96`) is drawn
    from the app's existing footer; the app keeps its current wording and `textMuted` colour
    ("Sonora 0.4.0 · Alpha · Build 23 (abcdef)", feature 001 FR-021a).
  - States the design does not draw reuse existing styles: "Not set", "Connecting…", the no-address
    and stale states, the removal confirmation (the turn-off dialog's style), the
    removing-in-progress row, empty sections and short messages.
  - Extension connection lines use the hub's states ("Connected"), not the design's sample wording
    ("Connected to broker"), which the hub does not provide; "Inactive" and "Unknown" badges, not in
    the design, use the neutral "Disabled" colours.
  - Colours the updated design introduces without a token (the red "Not connected"/"Rejected" text
    `#FF8A7A` on `#3A1A16`, the tile icon grey `#C9CBD1`, the chevron `#6E717A`, the "Turn off"
    button text `#2A0D08`) become theme tokens. The sheet's scrim uses the existing `scrim` token
    (`rgba(5, 6, 8, 0.72)`).
  - The turn-off and removal dialogs dim the screen with the platform's own dialog scrim, so the
    dim behind them may differ slightly from the design's `rgba(5, 6, 8, 0.72)`. Matching it would
    need Android-only code in shared code.
  - Several runtime sources share one card with dividers between rows, like the other lists; the
    design draws its single sample row as a card of its own.
  - The footer sits at the end of the scrolling content (FR-001), not fixed above the bottom bar as
    drawn, so long lists do not squeeze it.
  - With no snapshot and the hub unreachable, each tab shows Rooms' "Can't reach the hub" message
    with the action "Change address", which opens the address sheet.
- Following the project workflow, the first implementation commit sets the app version to
  `0.4.0-alpha` (version code 4).
- The backlog item [Rooms with several playbacks](../../docs/backlog/rooms-with-several-playbacks.md),
  previously planned as feature 004, moves to a later feature number.
