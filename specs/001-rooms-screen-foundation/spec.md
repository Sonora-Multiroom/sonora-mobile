# Feature Specification: Rooms Screen Foundation

**Feature Branch**: `001-rooms-screen-foundation`

**Created**: 2026-10-01

**Status**: Draft

**Input**: User description: "Scope of the first feature" — the section of that name in CLAUDE.md:
scaffold the app with the bundled fonts and design tokens, use a client generated from the hub's
API description, a Settings stub with a persisted "Hub address", the Rooms screen against the real
hub with polling (now-playing cards with volume pill, Stop, Pause/Resume when pauseable; Idle
section; Off rooms; master mute; bottom navigation; other screens as placeholders), and unit tests
for the logic that turns hub data into Rooms cards.

## Clarifications

### Session 2026-10-02

- Q: In what order should the now-playing cards and the Idle rows appear on the Rooms screen? → A: Both sorted alphabetically by displayed name, case-insensitive (FR-007, FR-010).
- Q: When the hub can't be reached and the last known state is shown as stale, should controls stay usable? → A: No; card controls and master mute are disabled (dimmed, not tappable) until a refresh succeeds; navigation stays available (FR-004).
- Q: What single action does a pauseable card show while Starting…, Stopping… or Unknown? → A: The Pause button, disabled; it becomes active once the route is Playing or Paused (FR-016).
- Q: How long should the app wait for a hub response before treating a refresh or action as failed? → A: 3 seconds per request (FR-005, Edge Cases).
- Q: When a room or group is muted, should dragging its volume pill also unmute it? → A: No; the pill cannot be dragged while its target is muted (individually or via master mute) (FR-014a, Edge Cases).
- Q: Is an internet radio added at runtime (ephemeral, `http(s)` address, not pauseable) shown as "Live stream"? → A: Yes. "Live stream" depends only on the route being non-pauseable and the input address being `http(s)`, regardless of where the input came from; the tile kind stays "link" for runtime inputs (FR-008, FR-009).
- Q: When a group is playing and one or more of its rooms has no speaker hardware connected, what should the group's card show? → A: The group's status line gets " · Not connected" appended if any listed member room is not connected (e.g. "Living Room + Kitchen · Live stream · Not connected") (Edge Cases, FR-008).
- Q: If someone types a hub address that starts with `https://` and has no port, which port should the app use? → A: 8443; an explicitly typed port is always kept (FR-002).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Connect the app to my hub (Priority: P1)

On first launch the app does not know where the hub is. The user opens Settings, types the hub
address, and from then on the app talks to that hub, including after it is closed and reopened.

**Why this priority**: Nothing else works without a hub address; there is no discovery and no
default.

**Independent Test**: Install the app, enter the address of a running hub, close and reopen the
app; the Rooms screen loads data from that hub without asking again.

**Acceptance Scenarios**:

1. **Given** a fresh install, **When** the user opens the app, **Then** the Rooms screen explains
   that no hub is set and offers a way to go to Settings; no request is attempted.
2. **Given** the Settings screen, **When** the user enters a hub address and saves it, **Then**
   the address is stored on the device and the Rooms screen starts loading from it.
3. **Given** a saved address, **When** the app is restarted, **Then** the address field shows the
   saved value and Rooms loads from it without user action.
4. **Given** the user enters an address that is not a valid host or URL (e.g. empty or containing
   spaces), **When** they try to save, **Then** saving is refused with a plain explanation.

---

### User Story 2 - See what is playing in every room (Priority: P1)

The user opens the app and sees, at a glance, which rooms are playing what, which are idle, and
which are turned off, and the view keeps itself up to date while the app is open.

**Why this priority**: This is the core value of the remote: knowing the state of the house.

**Independent Test**: With a hub that has one group route, one single-room route, one idle room
and one disabled room, open Rooms and compare every card and row with the hub's state; change the
state from another client and see the screen follow within a few seconds.

**Acceptance Scenarios**:

1. **Given** a route playing on a single room, **When** Rooms is shown, **Then** a "Now playing"
   card shows the room name, the source name, a kind tile (stream, line-in, file or link), a status
   line (kind and Playing/Paused, or "Live stream"), and a volume pill with the room's volume.
2. **Given** a route playing on a group, **When** Rooms is shown, **Then** one card shows the group
   name with a "Group" badge, the source name and the member room names (e.g. "Living Room +
   Kitchen · Live stream"), and none of the member rooms appear in the Idle section.
3. **Given** rooms with no route, **When** Rooms is shown, **Then** each enabled one appears in the
   "Idle" section as "Nothing playing" with a "Play something in <room>" action.
4. **Given** an idle room that is disabled, **When** Rooms is shown, **Then** it appears dimmed in
   the Idle section as "Turned off" with an "Off" label and no play action.
5. **Given** an idle room whose hardware is not connected, **When** Rooms is shown, **Then** it
   appears as "Not connected", distinct from both "Nothing playing" and "Turned off", with no play
   action.
6. **Given** any hub state, **When** Rooms is shown, **Then** the header reads "N of M rooms in
   use", where M is the number of rooms (outputs) and N the number of rooms occupied by a route.
7. **Given** Rooms is open, **When** the hub state changes, **Then** the screen reflects it within
   about 3 seconds without user action.
8. **Given** the app goes to the background, **When** it stays there, **Then** it stops contacting
   the hub; on return it refreshes immediately.

---

### User Story 3 - Control playback from the Rooms screen (Priority: P2)

From a now-playing card the user changes volume, pauses or resumes a pauseable source, and stops
playback; from the header they mute or unmute the whole house.

**Why this priority**: Quick control is the second reason to open the app, after seeing state.

**Independent Test**: For each control on a card and the master mute button, perform the action
and confirm on the hub (and on screen after the next refresh) that it took effect.

**Acceptance Scenarios**:

1. **Given** a now-playing card, **When** the user drags the volume pill, **Then** the pill follows
   the finger and shows the percentage, the hub receives the new volume while dragging at a limited
   rate, and the final value is sent when the drag ends.
2. **Given** the user is dragging a volume pill, **When** a refresh arrives, **Then** the pill keeps
   the user's value; after the drag ends and the request completes, the next refresh decides the
   shown value.
3. **Given** a group card whose member rooms are at 70% and 35%, **When** the user drags the
   pill from 70% to 35%, **Then** the members become 35% and 18% (balance kept), and the pill
   shows the loudest member.
4. **Given** a group card whose member rooms are all at 0%, **When** the user drags the pill to
   40%, **Then** every member is set to 40%.
5. **Given** a card for a pauseable route that is playing, **When** the user taps Pause, **Then**
   the hub pauses it and the card shows Resume and "Paused"; tapping Resume reverses it.
6. **Given** a card for a non-pauseable route, **When** Rooms is shown, **Then** the card offers
   Stop and no Pause control.
7. **Given** a card with a Stop control, **When** the user taps Stop, **Then** the route is stopped
   and the room (or each member room of a group) moves to the Idle section after the next refresh.
8. **Given** a pauseable route, **When** Rooms is shown, **Then** the card offers only
   Pause/Resume and no Stop; stopping a pauseable source arrives with the Now Playing screen
   (next feature).
9. **Given** the master mute button, **When** the user taps it, **Then** the hub's master mute
   toggles and the button shows the muted state as reported by the hub.
10. **Given** any control, **When** the hub rejects the action or cannot be reached, **Then** the
   user sees a short plain-language message and the card returns to the hub's state on the next
   refresh.

---

### User Story 4 - Navigate the app shell (Priority: P3)

A bottom navigation bar with Rooms, Sources and Settings lets the user move between areas. Areas
not built yet (Sources, Now Playing, Start Playback) show a clear "coming soon" placeholder.

**Why this priority**: Provides the frame later features plug into; little value on its own.

**Independent Test**: Tap each navigation item and each card/row action leading to an unbuilt
screen; each shows the correct destination or placeholder, and Back returns to Rooms.

**Acceptance Scenarios**:

1. **Given** any top-level screen, **When** the user taps a navigation item, **Then** that area is
   shown and the item is highlighted.
2. **Given** Rooms, **When** the user taps a now-playing card, "Play something", or a room's play
   action, **Then** a placeholder for Now Playing or Start Playback is shown.

---

### Edge Cases

- **Hub unreachable, restarting or address wrong**: Rooms shows a clear "Can't reach the hub"
  state with the address being used, keeps retrying on the polling schedule, and recovers by
  itself when the hub answers. The app never crashes or freezes. The last known state, if any, is
  shown as stale rather than discarded, with its controls disabled (FR-004).
- **Hub responds slowly**: a request that takes longer than 3 seconds is treated as failed for
  that cycle; polls never pile up.
- **Route starting or stopping**: the card shows "Starting…" or "Stopping…" instead of Playing.
- **Route failed**: the card shows "Couldn't play" with a Stop control to clear it.
- **Disabled room or group that is still playing**: keeps its now-playing card; "Off" applies only
  to idle rooms, because disabled means "cannot start new playback".
- **Room playing but hardware not connected**: the card stays, marked "Not connected". A group
  card is marked the same way when any of its listed member rooms is not connected.
- **Muted room or group**: the volume pill shows a muted speaker icon; the percentage stays, and
  the pill cannot be dragged until the target is unmuted (FR-014a).
- **Master mute active**: the hub's master mute marks every room as muted, so the header button
  shows the active state and every card's pill shows the muted icon and cannot be dragged. Turning master mute off
  unmutes every room, including rooms that were muted individually before (hub behaviour).
- **Stopped routes**: the hub also lists routes whose status is stopped; they are ignored entirely
  (no card, the room is not occupied, not counted in "N of M rooms in use").
- **A route names an input, room or group the hub no longer lists**: the card still shows, using
  the identifier as the name, and a missing member room is skipped.
- **A room belongs to several groups**: it is occupied only by the group that actually has a route.
- **No rooms at all**: Rooms shows an empty state saying the hub has no rooms configured.
- **A route addresses a target type the app doesn't recognise** (newer hub): the route still gets
  a card titled with the target id, with its status and action but no volume pill; it occupies no
  room, because the app can't tell which rooms it covers.
- **Newer hub with unknown fields or values**: they are ignored or shown as "Unknown" and never
  break the screen.

## Requirements *(mandatory)*

### Functional Requirements

**Hub address & connection**

- **FR-001**: The app MUST let the user enter, edit and save a hub address in Settings and keep it
  on the device across restarts. There is no default address.
- **FR-002**: The app MUST accept an address with or without the `http://` prefix, and also with
  `https://`. When no port is given, the app MUST use port 8080 (the hub's usual port) for plain
  addresses and `http://`, and port 8443 for `https://` (e.g. "multiroom.lan" →
  `http://multiroom.lan:8080`, "https://multiroom.lan" → `https://multiroom.lan:8443`). Any other
  port, including 80 and 443, is used only when typed explicitly.
- **FR-003**: Until an address is saved, Rooms MUST show a "Set your hub address" state linking to
  Settings and MUST NOT attempt any network request.
- **FR-004**: The app MUST show a distinct connection state when the hub cannot be reached and MUST
  recover automatically when it becomes reachable again. While the hub is unreachable and the last
  known state is shown as stale, every card control (volume pill, Pause/Resume, Stop) and the
  master mute button MUST be disabled (dimmed, not tappable); navigation and play/placeholder
  actions stay available. Controls re-enable on the first successful refresh.
- **FR-005**: The app MUST refresh hub state about every 2.5 seconds while it is in the foreground,
  MUST stop refreshing in the background, and MUST refresh immediately on returning. Every hub
  request (refresh or action) MUST time out after 3 seconds and count as failed; a new refresh
  MUST NOT start while the previous one is still running.

**Rooms screen content**

- **FR-006**: The app MUST treat each output as a room and determine, for every room, whether it is
  occupied by a route addressed to that room directly or to a group containing it. Routes with
  status STOPPED MUST be ignored: they produce no card and occupy no room.
- **FR-007**: The app MUST show one now-playing card per active route, for single rooms and groups
  alike, with: target name, "Group" badge for groups, source name, kind tile, status line and
  volume pill. Group cards MUST list member room names on one line, truncated with an ellipsis
  when they do not fit. Cards MUST be sorted alphabetically by target name, case-insensitive,
  so the order does not change between refreshes.
- **FR-008**: The status line MUST reflect the hub's route state: Playing, Paused, Starting…,
  Stopping…, Couldn't play, or Unknown. A playing route that is not pauseable and whose input
  address starts with `http://` or `https://` reads "Live stream", whether the input is configured
  or added at runtime. Other non-pauseable sources (e.g. line-in) read "<kind> · Playing".
- **FR-009**: The app MUST derive a source's kind in one place: added at runtime (ephemeral) →
  link; address starting with `http://` or `https://` → stream; `file:` or a file path → file;
  anything else → line-in. Each kind uses its own tile colours from the design. Kind (origin
  first) decides the tile; whether a route is a live stream is decided separately from the
  address (FR-008), in the same place.
- **FR-010**: The app MUST list unoccupied rooms in an "Idle" section with one of three states:
  available and enabled → "Nothing playing" with a play action; disabled → "Turned off" with an
  "Off" label; hardware not connected → "Not connected". Disabled takes precedence when both apply.
  Idle rows MUST be sorted alphabetically by room name, case-insensitive, whatever their state.
- **FR-011**: The header MUST show "N of M rooms in use" and a master mute button reflecting the
  hub's master mute state.
- **FR-012**: The app MUST NOT show progress bars, playback position, track metadata or artwork.
  The volume pill MUST NOT look like a progress bar.

**Controls**

- **FR-013**: The volume pill on a single-room card MUST show and set that room's volume.
- **FR-013a**: A group card's pill MUST show the volume of the loudest member room (the hub
  reports no group volume). Member rooms the hub no longer lists are ignored; with no known
  members the pill shows 0%.
- **FR-013b**: Dragging a group pill MUST keep the balance between member rooms: each member's new
  volume is its volume × (new value ÷ loudest volume), rounded to the nearest whole percent with
  halves rounded up (17.5 → 18), and kept within 0–100, computed from the member volumes as they
  were when the drag started (so
  dragging down and back up within one drag restores the balance). If every member is at 0%, each
  member is set to the new value.
- **FR-013c**: Group volume MUST be applied by setting each member room's volume individually,
  throttled as in FR-014. The hub's single "set group volume" action MUST NOT be used, because it
  sets every member to the same value and destroys the balance.
- **FR-013d**: The scaling rule in FR-013b MUST be covered by automated tests, including the
  all-members-at-0% case, rounding, and clamping to 0–100.
- **FR-014**: While the user drags a volume pill, the local value MUST win over refreshes; volume
  MUST be sent at a limited rate during the drag (no more than about 4 times per second) and once
  more when the drag ends; after the request completes, the next refresh reconciles the value.
- **FR-014a**: A volume pill MUST NOT be draggable while its target is muted: a single-room pill
  when that room is muted, a group pill when the group is muted, and every pill while master mute
  is on. It still shows the percentage with a muted speaker icon. Dragging never unmutes; unmuting
  is done with master mute (per-room mute controls are out of scope).
- **FR-015**: Pause/Resume MUST be offered only for routes the hub marks pauseable; it MUST NOT be
  offered for other routes.
- **FR-016**: Stop MUST be offered on every non-pauseable card and on cards in the "Couldn't play"
  state, and MUST end the route on the hub. A pauseable card that is playing or paused offers only
  Pause/Resume: each card has exactly one action, as in the design. A pauseable card in the
  Starting…, Stopping… or Unknown state shows the Pause button disabled; it becomes active once
  the route is Playing or Paused.
- **FR-017**: The master mute button MUST toggle the hub's master mute.
- **FR-018**: Every action's result MUST be confirmed by the next refresh. If an action fails, the
  app MUST show a short message in plain words (not raw error text) and return to the hub's state.
- **FR-019**: While an action is in flight, its control MUST prevent repeat taps from sending
  duplicate requests.

**Navigation & placeholders**

- **FR-020**: The app MUST provide bottom navigation with Rooms, Sources and Settings.
- **FR-021**: Now Playing, Start Playback and Sources MUST exist as reachable placeholder screens.
  Settings in this feature contains only the hub address.

**Look & accessibility**

- **FR-022**: Screens MUST use the design's colours, typography (Sora for titles, DM Sans for other
  text) and layout from the Rooms design.
- **FR-023**: Every touch target MUST be at least 44 dp, text MUST meet 4.5:1 contrast, and every
  icon-only control MUST have a spoken label naming the room (e.g. "Stop Downstairs", "Pause
  Office", "Mute all rooms").

### Key Entities

- **Room (output)**: a speaker location. Name, volume (0–100), muted, enabled (can start new
  playback), available (hardware connected).
- **Group**: a named set of rooms addressed together. Name, member rooms, muted, enabled.
- **Source (input)**: something that can be played. Name, address, enabled, pauseable, origin
  (configured or added at runtime), and a derived kind (stream, line-in, file, link).
- **Route**: a source playing on one room or one group. Target, state (starting, active, stopping,
  stopped, failed), paused, pauseable, transferable.
- **Room card**: what the Rooms screen shows for one active route, or one idle row per unoccupied
  room; the result of joining the four entities above.
- **Hub address**: the user's saved location of the hub on the home network.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A first-time user can enter the hub address and see their rooms in under 1 minute.
- **SC-002**: A change made on the hub by another client appears on the Rooms screen within 3
  seconds while the app is open.
- **SC-003**: Every room and every active route on the hub is represented exactly once on the
  Rooms screen, verified against at least the scenarios in User Story 2 (single route, group route,
  idle, off, not connected).
- **SC-004**: Volume, Pause/Resume, Stop and master mute each take effect on the hub on the first
  attempt in 95% of tries on a healthy home network.
- **SC-005**: With the hub switched off, the app stays responsive, shows the unreachable state
  within 6 seconds, and shows live data again within 6 seconds of the hub coming back. (Worst
  case: a 2.5 s wait plus a 3 s timeout = 5.5 s, when the hub stops answering silently.)
- **SC-006**: While the app is in the background it makes no requests to the hub.
- **SC-007**: The installable Android app builds and all automated checks of the card-building
  logic pass before the feature is considered done.

## Assumptions

- The user is on the same home network as the hub; plain HTTP without login is intentional.
- "Room" means an output; groups appear only as now-playing cards when a group route exists, not
  in the Idle section.
- Per-room mute toggles on cards are out of scope; muted state is only displayed. The "Move to
  room…" action, Now Playing, Start Playback, Sources and the full Settings tabs are later features.
- Inputs added at runtime are shown with the "link" kind, matching the Settings design; this is
  the single place kind is decided (hub gap: no kind field).
- A played link (SoundCloud/YouTube) is assumed to be either pauseable or stored under a
  non-`http(s)` address; if the hub keeps its `https://` page URL and reports it non-pauseable, its
  card will read "Live stream". To be checked against the real hub.
- The hub has no push updates (hub gap), so refreshing on a timer is the only way to stay current.
- The hub has no group volume value and its group-volume action flattens member volumes (hub
  gap, see FR-013a–c); the loudest member is shown and changes scale members proportionally.
- While Now Playing is a placeholder, a pauseable source can be paused but not stopped from the
  app; another client or starting new playback on that room (a later feature) stops it.
- The design follows FR-013a–b: the Downstairs group shows 70% (members 70% and 55%) and its pill
  scales members proportionally (canvas and `design/screens/` updated 2026-10-01).
- The hub address is entered manually because the hub does not announce itself (hub gap).
- Android is the only platform delivered; the iOS version is kept buildable later but not built.
- The visual reference is the Rooms design (`design/screens/Main.dc.html` and the linked canvas);
  the "Not connected", "Starting…", "Stopping…", "Couldn't play" and "no hub set" states are not
  drawn there and follow the existing card and row styles.
