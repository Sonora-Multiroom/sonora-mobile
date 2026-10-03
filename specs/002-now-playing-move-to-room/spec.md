# Feature Specification: Now Playing and "Move to room…"

**Feature Branch**: `002-now-playing-move-to-room`

**Created**: 2026-10-03

**Status**: Draft

**Input**: User description: "002: Now Playing + 'Move to room…'" — the Now Playing screen
(`design/screens/NowPlaying.dc.html`) and the Move to room bottom sheet
(`design/screens/Transfer.dc.html`), replacing the Now Playing placeholder from feature 001.

## Clarifications

### Session 2026-10-03

- Q: Should other groups also be offered as "Move to room…" destinations, given that the hub can move playback to a group but the design lists rooms only? → A: Yes. The sheet lists rooms and, in their own "Groups" section, groups; the design is updated first (FR-020, FR-020a, FR-026).
- Q: When playback is moved onto a room that is already playing something else, what does the hub do with that room's current playback? → A: It stops it and the moved playback takes over; if that playback was on a group, the whole group's playback stops (FR-021, FR-020a, Assumptions).
- Q: If a group has one room whose speaker isn't connected, should playback still be movable to that group? → A: Yes; the group stays selectable and its note adds which rooms are not connected (e.g. "· Patio not connected"); the hub plays on the connected rooms (FR-020a).
- Q: While master mute is on, what happens if a single room or group is unmuted? → A: The room stays silent until master mute is turned off, so the mute button stays disabled with "All rooms are muted" (FR-018, Assumptions).
- Q: If a paused playback is moved, what does the hub do? → A: Unknown; offer "Move to room…" only while the playback is Playing and hide it while Paused, until checked on the hub (FR-012, Assumptions).
- Q: For a group route, how does a "<Room> only" option appear when that member room is turned off or not connected? → A: It keeps its "<Room> only" label, reads "Turned off" or "Not connected" instead of "… stop", and cannot be picked, like any other turned-off or disconnected room (FR-021, FR-022).
- Q: How does a group appear when the hub knows none of its member rooms? → A: It is listed with the note "No rooms" and cannot be picked (FR-020a).
- Q: How does an enabled group read when one of its member rooms is turned off? → A: Like a not-connected member: it stays selectable and its note adds "· <Room> turned off"; the hub plays on the remaining rooms (FR-020a).
- Q: Is a group selectable when none of its known member rooms is connected? → A: No. When no known member can play (each is turned off or not connected), the group reads "Not connected" ("Turned off" when every member is turned off) and cannot be picked (FR-020a).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - See and control one playback in detail (Priority: P1)

From Rooms the user taps a now-playing card and gets a full screen for that playback: what is
playing, where, its state, and every control that applies to it, including Stop for sources that
can be paused (which Rooms cards cannot stop).

**Why this priority**: It closes the gap left by feature 001 (a pauseable source can be paused but
not stopped from the app) and is the screen the "Move to room…" action lives on.

**Independent Test**: With one pauseable route, one live stream and one line-in route on the hub,
open each from Rooms, compare the screen with the hub's state, pause/resume and stop each, and
check that changes made from another client appear within a few seconds.

**Acceptance Scenarios**:

1. **Given** Rooms shows a now-playing card, **When** the user taps the card, **Then** Now Playing
   opens for that playback, with a "Rooms" back control at the top.
2. **Given** Now Playing for any route, **When** it is shown, **Then** it shows the source name as
   the title, a line with the source kind and its address detail (e.g. "Stream ·
   stream.radioparadise.com"), a status chip (Playing, Paused, Starting…, Stopping…, Couldn't play
   or Unknown) and where it plays ("on Bedroom", or for a group "on Downstairs · Living Room +
   Kitchen").
3. **Given** a pauseable route that is playing, **When** the user taps Pause, **Then** the hub
   pauses it and the screen shows "Paused" and a Resume control; Resume reverses it.
4. **Given** any route, **When** the user taps Stop, **Then** the hub ends the route and the app
   returns to Rooms, where the room (or each member room of a group) is idle after the next
   refresh.
5. **Given** a live stream (not pauseable, internet address), **When** Now Playing is shown,
   **Then** it shows Stop and no Pause, a "Live stream" badge, and the line "Live streams can't be
   paused". A non-pauseable source that is not a live stream (e.g. line-in) shows Stop only, with
   neither the badge nor the line.
6. **Given** Now Playing is open, **When** the route is stopped by another client, **Then** the app
   returns to Rooms and shows a short message that playback on that target ended.
7. **Given** Now Playing is open, **When** the hub state changes, **Then** the screen reflects it
   within about 3 seconds, as on Rooms.

---

### User Story 2 - Set volume and mute per room on Now Playing (Priority: P2)

On Now Playing the user sets the volume of the playing room, or of a group and each of its rooms
separately, and mutes or unmutes the room or group.

**Why this priority**: Balancing rooms of a group and muting one target are the most common
adjustments after starting playback; Rooms cards only offer one pill and no mute.

**Independent Test**: With a group route on two rooms at different volumes, drag the group pill,
then each room pill, then mute and unmute the group; compare every value with the hub.

**Acceptance Scenarios**:

1. **Given** a single-room route, **When** Now Playing is shown, **Then** it shows one volume pill
   labelled with the room name and its percentage, and a mute button for that room.
2. **Given** a group route, **When** Now Playing is shown, **Then** it shows the group pill (the
   loudest member's volume, as on Rooms) with a mute button for the group, followed by one pill per
   member room labelled with the room name.
3. **Given** a group pill, **When** the user drags it, **Then** every member room is scaled
   proportionally, exactly as the group pill on Rooms (feature 001, FR-013b), and the member pills
   follow.
4. **Given** a member room pill, **When** the user drags it, **Then** only that room's volume
   changes, and the group pill shows the new loudest value.
5. **Given** the mute button, **When** the user taps it, **Then** the room (or every room of the
   group) is muted on the hub, the button shows the muted state, and the affected pills show the
   muted icon and cannot be dragged; tapping again unmutes.

---

### User Story 3 - Move playback to another room (Priority: P2)

From Now Playing the user taps "Move to room…", picks a room or a group in a bottom sheet that
says what each choice will do, and the playback continues there without restarting.

**Why this priority**: Following the listener around the house is the main reason to have a
remote; it depends on Now Playing (US1) as its entry point.

**Independent Test**: With a stream playing on a group and another source playing in one room,
open Move to room, check every option's note, move to an idle room, then to the busy room, then
to a group, then from the group to one of its members; compare the result with the hub each time.

**Acceptance Scenarios**:

1. **Given** a route the hub marks transferable and that is Playing, **When** Now Playing is shown,
   **Then** it offers "Move to room…"; otherwise (including while Paused) the action is not shown.
2. **Given** the user taps "Move to room…", **When** the sheet opens, **Then** it is titled "Move
   playback" with the line "<source> · now on <target>", lists the destinations under "Move to",
   shows the note "Keeps playing while it moves, no restart", and offers a primary button and
   Cancel.
3. **Given** the sheet, **When** it lists destinations, **Then** each room shows what moving there
   means: an idle room reads "Idle"; a room busy with another playback reads "<other source> will
   stop" in the warning colour (or "<other source> will stop on <group>" when that playback is on a
   group, which then stops as a whole); for a group route, each member room appears as "<Room> only" with
   "<other member rooms> stop"; a room that is turned off reads "Turned off" and one whose hardware
   is not connected reads "Not connected", and neither can be picked.
4. **Given** the sheet, **When** it lists groups in its "Groups" section, **Then** each group shows
   its member rooms (e.g. "Living Room + Kitchen"), or "<other source> will stop" in the warning
   colour when moving there would stop other playback, followed by "· <Room> turned off" and
   "· <Room> not connected" for member rooms that are turned off or without hardware; a turned-off
   group, one with no known rooms, and one none of whose rooms can play cannot be picked.
5. **Given** the user picks a destination, **When** the primary button reads "Move to <name>" and
   is tapped, **Then** the hub moves the playback, the sheet closes, and Now Playing shows the same
   source playing on the new target.
6. **Given** the move fails, **When** the hub rejects it or cannot be reached, **Then** the sheet
   closes, a short plain-language message explains it, and Now Playing shows the hub's state.
7. **Given** the sheet is open, **When** the user taps Cancel, swipes it down or uses Back,
   **Then** it closes without changing anything.

---

### Edge Cases

- **Hub unreachable while Now Playing is open**: the screen keeps the last known state, marked as
  stale, with every control disabled (as Rooms, feature 001 FR-004); "Move to room…" cannot be
  opened, and an open sheet's primary button is disabled. Controls return on the first successful
  refresh.
- **Route state Starting…, Stopping… or Unknown**: Pause/Resume is shown disabled (pauseable routes)
  and "Move to room…" is hidden; Stop stays available.
- **Route failed ("Couldn't play")**: the screen shows the status and Stop only.
- **Stop finds the playback already gone** (the hub answers "not found"): it ended elsewhere, so
  the app returns to Rooms with the "Playback on <target> ended" message (FR-002), not an error.
- **Route replaced by a move made elsewhere**: the original playback no longer exists, so Now
  Playing returns to Rooms with the "ended" message (scenario US1-6); the moved playback appears
  on Rooms as usual.
- **Target room or group no longer listed by the hub**: the target is shown by its identifier, no
  volume pill or mute button is shown for it, and missing member rooms are skipped.
- **Master mute active**: every pill shows the muted icon and cannot be dragged, the mute button
  shows the muted state and is disabled, and the screen says "All rooms are muted" so the user
  knows to turn master mute off on Rooms.
- **A member room muted individually** (from another client): its pill shows the muted icon and
  cannot be dragged; the group mute button shows "unmuted" until every member is muted (the hub
  reports a group as muted only when all members are).
- **Room playing but hardware not connected**: the "on …" line is followed by "· Not connected", as
  on Rooms cards.
- **Destination becomes busy, turned off or disconnected while the sheet is open**: the option's
  note and availability update with the next refresh; a selected option that becomes unavailable
  is deselected.
- **Nothing to move to** (every other room is turned off or not connected, and every other group
  is turned off or there is none): the sheet still opens,
  lists them as unavailable, and the primary button stays disabled.
- **Two moves at once** (another client moves the same playback first): the hub rejects the
  second; the app shows the plain message and follows the hub's state.
- **Playback disappears from a refresh while this screen's Stop or move is still waiting for the
  hub**: it is not treated as "ended elsewhere"; the app waits for the answer (Stop succeeded →
  back to Rooms without a message; move succeeded → follow the moved playback).
- **Newer hub with unknown values**: shown as "Unknown", never breaking the screen. A playback
  whose target type is unknown offers no "Move to room…".

## Requirements *(mandatory)*

### Functional Requirements

**Opening Now Playing**

- **FR-001**: Tapping a now-playing card on Rooms MUST open Now Playing for that card's playback,
  replacing the placeholder from feature 001. Start Playback and Sources stay placeholders.
- **FR-002**: Now Playing MUST follow one playback. When the hub no longer lists it as active
  (stopped here, stopped elsewhere, or replaced), the app MUST return to Rooms; when it ended
  without the user tapping Stop on this screen, Rooms MUST show the short message "Playback on
  <target> ended". After a move made from this screen, Now Playing MUST follow the moved playback
  (the hub gives it a new identity).
- **FR-003**: Now Playing MUST refresh on the same schedule and with the same timeouts as Rooms
  (feature 001 FR-005), stop in the background, and show stale state with disabled controls when
  the hub cannot be reached (feature 001 FR-004).

**Content**

- **FR-004**: The screen MUST show, top to bottom as in the design: a "Rooms" back control; a
  decorative panel in the source kind's colours with the kind icon, carrying a "Live stream" badge
  for live streams; the source name as title; "<Kind> · <address detail>"; a status chip and the
  "on <target>" line; the actions; the volume section.
- **FR-005**: The address detail MUST be derived in one place: for stream and link sources the
  host name of the address (e.g. "stream.radioparadise.com"); for file sources the file name; for
  line-in nothing (the line reads only "Line-in"). An address that cannot be parsed is shown as
  typed.
- **FR-006**: The status chip MUST use the same states and wording as Rooms (feature 001 FR-008):
  Playing, Paused, Starting…, Stopping…, Couldn't play, Unknown.
- **FR-007**: The "on <target>" line MUST name the room, or the group followed by its member room
  names joined with " + " (truncated with an ellipsis when they do not fit), and MUST append
  " · Not connected" when the room, or any listed member room, is not connected.
- **FR-008**: The screen MUST NOT show progress bars, playback position, track metadata or
  artwork. The decorative panel depends only on the source kind, never on the content.

**Actions**

- **FR-009**: Stop MUST be offered for every route, including pauseable ones, and MUST end the
  route on the hub; on success the app returns to Rooms (FR-002).
- **FR-010**: Pause/Resume MUST be offered only for routes the hub marks pauseable, active only
  while the route is Playing or Paused, and shown disabled in other states.
- **FR-011**: A live stream (not pauseable and an `http(s)` address, decided by the same function
  as Rooms) MUST show the line "Live streams can't be paused". Other sources MUST NOT show it.
- **FR-012**: "Move to room…" MUST be offered only when the hub marks the route transferable and
  the route is Playing. It MUST NOT be offered while Paused, because the hub's behaviour for moving
  a paused playback is unconfirmed. An open sheet closes as soon as the action would no longer be
  offered (the playback becomes paused or not Playing, or no longer transferable).
- **FR-013**: Every action MUST follow feature 001 FR-018 and FR-019: confirmed by the next refresh,
  a short plain-language message on failure, and no duplicate requests from repeated taps.

**Volume & mute**

- **FR-014**: A single-room route MUST show one pill for the room, labelled with the room name and
  percentage, and a mute button for that room.
- **FR-015**: A group route MUST show the group pill (loudest member, proportional scaling, member
  updates sent individually, never the hub's group-volume action — feature 001 FR-013a–c) with a
  mute button for the group, and below it one pill per member room the hub lists, in the group's
  member order, each setting only its own room.
- **FR-016**: The mute button MUST mute or unmute its room, or every room of its group, on the hub,
  show the hub's mute state, and have a spoken label "Mute <target>" / "Unmute <target>".
- **FR-017**: Pills MUST follow feature 001 FR-014 and FR-014a (local value wins while dragging,
  throttled sending, not draggable while the target is muted or master mute is on). A member pill
  is not draggable while that member room is muted.
- **FR-018**: While master mute is on, the mute button MUST be disabled and the volume section MUST
  show "All rooms are muted".

**Move to room sheet**

- **FR-019**: "Move to room…" MUST open a bottom sheet titled "Move playback" with "<source> · now
  on <target>", a "Move to" list, the note "Keeps playing while it moves, no restart", a primary
  button and Cancel.
- **FR-020**: The "Rooms" part of the list MUST contain every room except the one currently playing
  this route (for a single-room route). For a group route it MUST contain every room outside the
  group, and each member room as "<Room> only".
- **FR-020a**: Below the rooms, a "Groups" section MUST list every group the hub lists except the
  one currently playing this route. Each group's note is decided in the same place as the room
  notes: "<other source> will stop" (warning colour) when any member room is occupied by another
  playback, naming every such source ("Jazz24 and Morning playlist will stop"). The group note
  names only the sources, not the group they play on, even though a source playing on another
  group stops on that whole group (as for rooms, FR-021); otherwise the note is its
  member room names joined with " + ". Member rooms that are turned off are appended as
  " · <rooms> turned off", then member rooms that are not connected as " · <rooms> not connected"
  (names joined with ", " and " and "), and the group stays selectable, because the hub plays on
  the remaining rooms. When no known member room can play (each is turned off or not connected),
  the group reads "Turned off" if every known member is turned off, otherwise "Not connected", and
  cannot be selected. A disabled group reads "Turned off" and cannot be
  selected. A group none of whose member rooms the hub lists reads "No rooms" and cannot be
  selected. Precedence: disabled group, then no known members, then no playable member. Groups
  are ordered like rooms: selectable alphabetically, then unselectable
  alphabetically. When the hub lists no other group, the section is not shown.
- **FR-021**: Each room destination MUST show one note, decided in one place:
  "Idle" for an unoccupied, available, enabled room; "<other source> will stop" (warning colour)
  for a room occupied by another single-room playback; "<other source> will stop on <group>"
  (warning colour) for a room occupied by another group's playback, because the hub stops that
  whole group's playback; "<other member rooms> stop" for "<Room> only" options
  (names joined with ", " and " and "); "Turned off" for a disabled room; "Not connected" for a
  room without hardware. Turned-off and not-connected rooms MUST NOT be selectable and are shown
  dimmed; turned off takes precedence when both apply. This also applies to "<Room> only"
  options: a turned-off or not-connected member keeps its "<Room> only" label but reads "Turned
  off" or "Not connected" instead of "… stop" and cannot be selected.
- **FR-022**: Room destinations MUST be ordered: selectable rooms outside the current target
  (alphabetically, case-insensitive), then selectable "<Room> only" options (alphabetically), then
  unselectable rooms and "<Room> only" options (alphabetically).
- **FR-023**: No destination is preselected; rooms and groups form one single-choice list. The
  primary button MUST read "Move to <name>" for the selected room or group ("Move to Kitchen" for
  "Kitchen only") and be disabled until one is selected.
- **FR-024**: Confirming MUST ask the hub to move the playback to the chosen room or group. On success the
  sheet closes and Now Playing follows the moved playback (FR-002); on failure the sheet closes and
  FR-013's message is shown. While the request is in flight the button cannot be tapped again.
- **FR-025**: The destination notes and the list MUST be covered by automated tests: idle, busy
  with a single-room playback, busy with a group playback (naming the group), member "only" options with one and
  several other members, a turned-off or not-connected "only" member, turned off, not connected,
  and ordering; and for groups: free, busy with one and with several other playbacks, overlapping
  the current target without stopping anything else, turned off, no known member rooms, one and
  several members not connected (still selectable), a member turned off (still selectable), no
  playable member (unselectable), the current group excluded, and ordering.

**Look & accessibility**

- **FR-026**: Both screens MUST use the design's colours, typography and layout
  (`design/screens/NowPlaying.dc.html`, `design/screens/Transfer.dc.html`). The Move sheet's
  "Groups" section is not in the design yet: the canvas and `Transfer.dc.html` MUST be updated
  with it before the sheet is implemented, reusing the room rows' style with a group icon.
- **FR-027**: Every touch target MUST be at least 44 dp, and text MUST meet 4.5:1 contrast; text
  of disabled controls and unselectable rows, dimmed as in the design, is exempt (Constitution VI).
  Every icon control MUST have a spoken label; labels name the room or group where the control
  acts on one ("Pause Downstairs", "Mute Downstairs", "Downstairs volume", "Living Room volume"),
  while Stop reads "Stop", as its visible caption. The sheet's destinations MUST be announced as a
  single-choice list.

### Key Entities

- **Playback (route)**: a source playing on one room or one group; state, paused, pauseable,
  transferable. A move gives it a new identity on the hub.
- **Room, Group, Source**: as in feature 001; Now Playing shows one playback with its source, target
  and the volumes and mute states of the rooms it covers.
- **Destination**: a room or group the playback could move to, with its note (idle, member rooms,
  what will stop, turned off, not connected) and whether it can be picked.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: From Rooms, the user can stop any playback, including a pauseable one, in at most two
  taps.
- **SC-002**: From Rooms, the user can move a playback to an idle room or a group in at most four
  taps (card,
  "Move to room…", destination, confirm), and the moved playback is shown on its new target within
  3 seconds of confirming.
- **SC-003**: Every destination in the sheet states its consequence before the user confirms;
  in testing against the real hub, no move stops a playback the sheet did not announce.
- **SC-004**: Now Playing reflects a change made by another client within 3 seconds, and returns to
  Rooms within 3 seconds when its playback ends elsewhere.
- **SC-005**: Dragging a group pill on Now Playing keeps the volume ratio between member rooms, as
  on Rooms; the app never uses the hub's group-volume action.
- **SC-006**: The installable Android app builds and all automated checks pass, including the
  destination-note tests (FR-025).

## Assumptions

- Now Playing is reached only from a Rooms card in this feature; Start Playback, Sources and the
  full Settings tabs remain later features.
- Moving playback onto a room that is already playing stops that room's playback and the moved
  playback takes over; when the stopped playback was on a group, it stops on the whole group
  (confirmed by the user, 2026-10-03). The "will stop" notes rely on this.
- Moving to a group that shares rooms with the current target keeps those rooms playing without a
  restart; rooms of the current target outside the new group stop, which is the move itself and
  needs no note.
- Moving from a group to one of its member rooms stops the other member rooms and keeps the chosen
  one playing, which is what the "<Room> only" options announce.
- The hub keeps each room's own volume and mute when playback moves (volume is not carried over);
  the destination plays at whatever volume that room already has.
- Moving playback to a group with member rooms that are not connected plays on the connected
  rooms (confirmed by the user, 2026-10-03). A member room that is turned off is treated the same
  way: the hub plays on the remaining rooms (decided by the user, 2026-10-03; to be checked on the
  hub, like the paused-move behaviour).
- The hub reports a group as muted only when all its member rooms are muted.
- While master mute is on the hub reports every room as muted, and unmuting a single room or group
  does not make it audible until master mute is turned off (confirmed by the user, 2026-10-03);
  hence room and group mute are disabled during master mute (FR-018).
- Departure from the design (called out per the constitution): the design's sample state has
  Bedroom preselected; this spec preselects nothing (FR-023) so that a move always follows an
  explicit choice. With nothing selected the primary button reads "Move" and is disabled.
  The design's sheet otherwise applies as drawn.
- States the design does not draw (called out per the constitution) reuse existing styles rather
  than adding new emphasis:
  - The Pause/Resume button uses the round `surface` style of "Move to room…", placed between Stop
    and "Move to room…"; Stop keeps the accent fill as drawn.
  - Status chips other than Playing use the Rooms status colours from feature 001.
  - "All rooms are muted" is a muted-text line under the pills.
  - Loading, "Can't reach the hub" and the stale banner reuse the Rooms copy and styles
    (feature 001).
- Per-member mute buttons are not offered, matching the design; only the room or group being played
  has one.
- The decorative panel follows the source kind's design colours; the design shows only the stream
  variant, so other kinds use their kind tile colours from the design tokens.
- How the hub moves a paused playback (stays paused, resumes, or refuses) is not known; until it
  is checked on the hub, "Move to room…" is hidden while Paused (FR-012). Allowing it later is a
  small follow-up.
- Following the project workflow, the first implementation commit sets the app version to
  `0.2.0-alpha` (version code 2).
