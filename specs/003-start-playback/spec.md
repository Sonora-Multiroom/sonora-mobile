# Feature Specification: Start Playback

**Feature Branch**: `003-start-playback`

**Created**: 2026-10-04

**Status**: Draft

**Input**: User description: "003: Start Playback. Pick a configured source or paste a link, pick a
target, and see the 'X will stop in Bedroom' warning. This is the biggest gain in usefulness,
because today the app can't start anything." — the Start Playback screen
(`design/screens/StartPlayback.dc.html`), replacing the placeholder from feature 001.

## Clarifications

### Session 2026-10-04

- Q: The hub (0.1.21) lets a start replace what plays, mix with it, or lower it like an announcement, and a source may declare its own default. Which does the app use and announce? → A: The source's default: the app names no mode, so the hub uses the source's declared default, else replace; the consequence line follows that mode (FR-010). A link has no default and always replaces.
- Q: After a successful start, where does the app go? → A: Now Playing for the new playback; Back from there returns to Rooms (FR-015).
- Q: When does the link field show "Enter a web address (https://…)"? → A: On paste, when the field loses focus, or on Done/Enter; while typing only Play stays disabled (FR-006).
- Q: A start request times out, but the hub may have started it. What does the app do? → A: Refresh once; if a matching playback now runs on the target, open Now Playing for it, otherwise fail as "Couldn't reach the hub" (FR-016a).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Play a configured source in a room or group (Priority: P1)

The user taps "Play something" on Rooms (or the play action of an idle room), picks one of the
hub's sources, picks a room or group, and taps Play. The source starts there.

**Why this priority**: Today the app can only control playback someone else started. Starting the
radio in a room is the most common thing a remote control is for.

**Independent Test**: With the hub idle, start a stream in one room, then a different source on a
group; check after each that the hub plays it on the chosen target and that the app shows it.

**Acceptance Scenarios**:

1. **Given** Rooms, **When** the user taps "Play something", **Then** Start Playback opens titled
   "Play something", with a Close control, a "Paste a link" field, an "Or pick a source" list, a
   "Play in" grid of groups and rooms, and a Play button; nothing is selected.
2. **Given** Rooms with an idle room, **When** the user taps that room's "Play something in <room>"
   action, **Then** Start Playback opens with that room already selected under "Play in".
3. **Given** Start Playback, **When** the source list is shown, **Then** it lists every source the
   hub has turned on, alphabetically, each with its name, its kind ("Stream", "Line-in", "File")
   and the kind's tile colours.
4. **Given** a source and a target are selected, **When** the user looks at the Play button,
   **Then** it reads "Play <source> in <target>" (e.g. "Play Jazz24 in Bedroom").
5. **Given** a source and a target are selected, **When** the user taps Play, **Then** the hub
   starts that source on the target and Now Playing opens for the new playback; Back from Now
   Playing returns to Rooms.
6. **Given** Start Playback, **When** the user taps Close or uses Back, **Then** it closes without
   changing anything.

---

### User Story 2 - Know what will stop before starting (Priority: P1)

Before confirming, the user sees what each room and group is doing now and, once a target is
picked, exactly which playback will stop ("Radio Paradise will stop in Bedroom").

**Why this priority**: Starting on a busy room silently ends someone else's music. The warning is
an agreed design detail and what makes starting playback safe in a shared house.

**Independent Test**: With one source playing in a room, another on a group, and one room turned
off, open Start Playback, read every target's status line, select each target in turn and compare
the consequence line with what the hub does after Play.

**Acceptance Scenarios**:

1. **Given** the "Play in" grid, **When** it is shown, **Then** each target shows a status line:
   an idle room reads "Idle"; a room playing its own playback reads "<state> · <source>"
   (e.g. "Paused · Morning playlist"); a room played by a group reads "In <group>"; a group reads
   "Group · <source>" when it plays, otherwise "Group · <member rooms>"; a turned-off room or
   group reads "Turned off" and a room without hardware "Not connected", and neither can be picked.
2. **Given** a source and a busy target are selected, **When** the screen is shown, **Then** a
   warning line above the Play button names the playback that will stop and where, e.g. "Radio
   Paradise will stop in Bedroom"; when the busy playback is a group's, it names the group, which
   stops as a whole ("Radio Paradise will stop in Downstairs").
3. **Given** a target where several playbacks would stop (a group whose rooms play different
   things), **When** it is selected, **Then** the line names each one with where it stops, e.g.
   "Jazz24 will stop in Office and Morning playlist in Kitchen".
4. **Given** an idle target, **When** it is selected, **Then** no warning is shown.
5. **Given** the chosen source already plays on exactly the chosen target, **When** both are
   selected, **Then** the line reads "<source> is already playing in <target>" in muted text and
   nothing is announced as stopping.
6. **Given** a source whose hub default is to play alongside (mix) or to lower the others
   (announcement), **When** it and a busy target are selected, **Then** the line says so instead of
   "will stop": "Plays alongside Jazz24 in Bedroom", or "Jazz24 will be lowered in Bedroom while it
   plays".

---

### User Story 3 - Paste a link and play it (Priority: P2)

The user pastes a SoundCloud, YouTube or stream address, picks a target and taps Play. The hub
fetches and plays it.

**Why this priority**: Links are how new content reaches the hub without editing its
configuration, but configured sources (US1) cover the daily case.

**Independent Test**: Paste a SoundCloud link and a direct stream address in turn, play each in a
room, then try an address the hub cannot reach and a malformed one; compare with the hub.

**Acceptance Scenarios**:

1. **Given** Start Playback, **When** the user types or pastes into the link field, **Then** any
   selected source is deselected; picking a source clears the field. One of the two is the
   selection, never both.
2. **Given** a valid link and a target, **When** the Play button is shown, **Then** it reads
   "Play link in <target>", and the consequence line works as in US2.
3. **Given** the user taps Play with a link, **When** the hub needs time to resolve it, **Then** the
   button reads "Starting…" and cannot be tapped again until the hub answers.
4. **Given** a link without a scheme (e.g. "soundcloud.com/artist/track"), **When** it is played,
   **Then** it is sent as "https://soundcloud.com/artist/track".
5. **Given** text that is not a web address, **When** it is pasted, or the user leaves the field or
   presses Done, **Then** the field shows "Enter a web address (https://…)"; while typing, no
   message is shown, and Play stays disabled throughout.
6. **Given** the hub cannot play the link (unreachable, unsupported, or its service is down),
   **When** it answers, **Then** the screen stays open with the link and target kept, and a short
   plain-language message says why.

---

### Edge Cases

- **Hub unreachable while the screen is open**: the last known lists stay, marked stale as on
  Rooms (feature 001 FR-004); selections are kept, Play is disabled, and it re-enables on the first
  successful refresh.
- **No hub address set**: the screen cannot be reached usefully; it shows the Rooms "Set your hub
  address" state linking to Settings.
- **The hub lists no turned-on sources**: the list shows "No sources on the hub. Paste a link
  above." Links still work.
- **Selected source or target disappears, is turned off or loses its hardware** while the screen is
  open: it is deselected at the next refresh; the warning and the Play button follow.
- **Target state changes while selected** (another client starts or stops something there): the
  status line and the warning follow at the next refresh, so what the user confirms is what the hub
  last reported.
- **Group with some rooms turned off or not connected**: stays selectable, because the hub plays on
  the remaining rooms; when selected, a muted line says which rooms won't play, e.g. "Won't play in
  Patio (not connected)". A group whose known rooms are all turned off reads "Turned off", one whose
  rooms are otherwise all unplayable reads "Not connected", and one with no known rooms reads "No
  rooms"; none of these can be picked (precedence as in feature 002 FR-020a).
- **Turned-off room or group that is still playing**: it reads "Turned off" and cannot be picked,
  because turned off means no new playback can start there.
- **The chosen source already plays on a different target that overlaps the chosen one** (e.g. on a
  group containing the chosen room): the warning names it like any other playback that will stop,
  because the hub stops it and starts it again on the new target.
- **Announcements**: a playback that the hub runs as an announcement (it lowers the others instead
  of replacing them) keeps playing when new playback starts, so it is never named as stopping, and
  it does not appear in the status lines. It is named only when a new announcement will lower it
  (FR-010).
- **Master mute on**: a muted line "All rooms are muted" is shown above the Play button; starting
  still works, and the new playback is silent until master mute is turned off on Rooms.
- **Target muted** (the room, or every room of the group): a muted line "<target> is muted" is shown;
  starting still works.
- **The hub refuses the start** because the room already carries too many playbacks at once, or
  because the source already plays there as an announcement: the screen stays open and the message
  names the room ("Office can't play more at once", "Jazz24 is already playing in Office").
  The app does not predict which of "returns the existing playback" (FR-010 "already playing") or
  "refuses" the hub chooses when the source already plays as an announcement on or overlapping the
  chosen target; it shows the preview line per FR-010 and then whatever the hub answers (FR-015 or
  FR-016).
- **Close while a start is in flight**: the screen closes and the request continues; its outcome
  shows on Rooms at the next refresh, and a failure is reported there as a short message.
- **Newer hub with unknown values**: unknown states read "Unknown"; a target of unknown type is not
  listed.

## Requirements *(mandatory)*

### Functional Requirements

**Opening the screen**

- **FR-001**: Start Playback MUST replace the feature 001 placeholder. It opens from Rooms' "Play
  something" button with nothing selected, and from an idle room's "Play something in <room>"
  action with that room selected. Close and Back return to where the user came from without
  changing anything.
- **FR-002**: The screen MUST refresh on the same schedule and with the same timeouts as Rooms
  (feature 001 FR-005), stop in the background, and show stale state with Play disabled when the
  hub cannot be reached (feature 001 FR-004).

**Choosing what to play**

- **FR-003**: The screen MUST offer a "Paste a link" field with the placeholder "SoundCloud, YouTube
  or stream URL", and below it an "Or pick a source" list.
- **FR-004**: The list MUST contain every source the hub reports as turned on, configured or added
  at runtime, sorted alphabetically by name (case-insensitive). Each row shows the name, the kind
  label and the kind tile, with the kind derived by the existing single function (feature 001
  FR-009). Turned-off sources are not listed.
- **FR-005**: There MUST be at most one selection: picking a source clears the link field, and a
  non-empty link field deselects the source. Nothing is preselected.
- **FR-006**: A link MUST be trimmed; when it has no scheme, `https://` is prepended. It is valid
  when it is an `http` or `https` address with a host. Play is disabled while the field holds
  invalid text. The message "Enter a web address (https://…)" is shown only after a paste, when
  the field loses focus, or on Done/Enter, and only if the text is invalid then; while the user
  types, no message is shown, and an existing message clears as soon as the text becomes valid.

**Choosing where to play**

- **FR-007**: The "Play in" section MUST show every group and room the hub lists, in a two-column
  grid, ordered: selectable groups, then selectable rooms, then unselectable groups and rooms, each
  part alphabetical (case-insensitive).
- **FR-008**: Each target MUST show one status line, decided in one place:
  - Room: "Idle"; "<state> · <source>" for its own playback, with the state words of feature 001
    FR-008 (Playing, Paused, Starting…, Stopping…, Couldn't play, Unknown), sources joined with
    " + " when the hub plays several there; "In <group>" when a group's playback covers it;
    "Turned off" when disabled; "Not connected" without hardware (turned off wins).
  - Group: "Group · <source>" when it has its own playback; otherwise "Group · <member rooms>"
    joined with " + ", truncated with an ellipsis; "Turned off", "Not connected" or "No rooms" as
    in feature 002 FR-020a.
  - Announcement playbacks (see Edge Cases) are not shown.
- **FR-009**: Turned-off and not-connected targets, and groups that cannot play (FR-008), MUST NOT
  be selectable and are shown dimmed. A selected target that becomes unselectable is deselected.

**Consequence line**

- **FR-010**: Once a source (or valid link) and a target are selected, the line above the Play
  button MUST state what starting will do, decided in one place. The app names no join mode when
  starting, so the hub uses the source's declared default mode, otherwise *replace*; a link always
  replaces. The line follows that mode. "Affected playbacks" are the playbacks covering any room of
  the chosen target; each is named with its own target as <where>: the room, or the group when it
  is a group's playback. Several are joined: "Jazz24 will stop in Office and Morning playlist in
  Kitchen".
  - *Replace*: "<source> will stop in <where>" in the warning colour for each affected playback
    that is not an announcement, including the chosen source itself when it plays on a different
    target. A group's playback stops as a whole, hence the group as <where>. With nothing to stop,
    no line is shown.
  - *Mix* (play alongside): "Plays alongside <source> in <where>" in muted text for the affected
    playbacks; nothing stops. With none, no line is shown.
  - *Announcement* (lower the others): "<source> will be lowered in <where> while it plays" in
    muted text for every affected playback, announcements included; nothing stops. With none, no
    line is shown.
  - In every mode, when the chosen source already plays on exactly the chosen target, the line
    reads "<source> is already playing in <target>" in muted text instead, and nothing changes
    (the hub keeps that playback).
  - An unknown default mode from a newer hub is treated as *replace* for the line, so the user is
    never told less than may stop.
- **FR-011**: Below the warning, muted lines MUST say "Won't play in <rooms> (not connected)" /
  "(turned off)" for unplayable rooms of a selected group, "<target> is muted" when the target is
  muted, and "All rooms are muted" while master mute is on (which replaces "<target> is muted").

**Starting**

- **FR-012**: The Play button MUST read "Play <source> in <target>" for a source, "Play link in
  <target>" for a link, and "Play" while either is missing. It is enabled only when a source or
  valid link and a target are selected and the hub is reachable.
- **FR-013**: Play MUST ask the hub to start the chosen source on the chosen room or group, or to
  play the link there (the hub adds it as a source added at runtime). The app sends no volume and
  no name: each room keeps its own volume, and the hub names the link.
- **FR-014**: While the request is in flight, the button MUST read "Starting…", cannot be tapped
  again, and the selections cannot change. A configured source uses the usual 3-second timeout; a
  link may take up to 30 seconds, because the hub resolves it first.
- **FR-015**: On success, Start Playback MUST be replaced by Now Playing for the playback the hub
  returns (feature 002), so Back from Now Playing returns to Rooms. When the hub returns an existing
  playback (same source, same target), Now Playing opens for that one. If the playback ends before
  Now Playing shows it (e.g. the link could not be played after all), Now Playing behaves as in
  feature 002 FR-002: it returns to Rooms with "Playback on <target> ended".
- **FR-016**: On failure, the screen MUST stay open with the selections kept and show a short
  plain-language message: the link cannot be used ("The hub couldn't play this link"), cannot be
  reached ("Couldn't reach that link"), its service is unavailable ("That service isn't available
  right now. Try again later."), the source or target no longer exists ("<name> is no longer on the
  hub", followed by an immediate refresh), the room carries too many playbacks ("<room> can't play
  more at once"), the source already plays there ("<source> is already playing in <room>"), the hub
  cannot be reached ("Couldn't reach the hub"), anything else ("Couldn't start playback").
- **FR-016a**: When the start request times out (FR-014), the app MUST NOT report failure straight
  away, because the hub may have started the playback. It refreshes once, with the button still
  reading "Starting…". If that refresh shows a playback addressed to exactly the chosen target
  whose source is the chosen source, or for a link a source added at runtime with the normalised
  address, the app proceeds as on success (FR-015) with that playback. Otherwise, or if the
  refresh fails too, it fails as "Couldn't reach the hub" (FR-016).

**Quality**

- **FR-017**: The following MUST be covered by automated tests: target status lines (idle, own
  playback in each state, several playbacks, in a group, turned off, not connected, turned off and
  not connected, group playing, group idle, group with unplayable members, group with no rooms,
  group with no playable room, announcements ignored); target ordering; the consequence line for
  replace (idle, busy room, room in a group playback, group over several playbacks, same source on
  the same target, same source on an overlapping target, announcement left out), for mix and for
  announcement defaults (busy, idle, announcements included for the latter), for a link (always
  replace) and for an unknown mode (treated as replace); the muted lines; link
  normalisation and validation; source list filtering and ordering; the mapping of every hub
  error in FR-016; and timeout recovery (FR-016a: playback found for a source, found for a link,
  not found, refresh failing).
- **FR-018**: The screen MUST use the design's colours, typography and layout
  (`design/screens/StartPlayback.dc.html`); departures are listed under Assumptions.
- **FR-019**: Every touch target MUST be at least 44 dp and text MUST meet 4.5:1 contrast; dimmed
  unselectable targets are exempt (Constitution VI). The Close control reads "Close"; the source
  list and the target grid are each announced as a single-choice list, each item with its name and
  status line; the link field is labelled "Paste a link".

### Key Entities

- **Source (input)**: as in feature 001; here only turned-on ones are offered. A played link becomes
  a source added at runtime.
- **Target**: a room or group with its status line and whether it can be picked.
- **Start request**: a source or link, a target, the join mode the hub will apply (the source's
  default, else replace), and the playbacks it will stop, play alongside or lower.
- **Playback (route)**: as in features 001–002; may now be an announcement that lowers the others
  instead of replacing them.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: From an idle room on Rooms, the user starts a configured source there in three taps
  (the room's play action, the source, Play).
- **SC-002**: From Rooms, the user starts a pasted link on any room or group in at most four
  actions (Play something, paste, target, Play).
- **SC-003**: In testing against the real hub, no start stops a playback that the consequence line
  did not name, and every named playback does stop.
- **SC-004**: A started configured source appears on Rooms within 3 seconds of tapping Play.
- **SC-005**: The installable Android app builds and all automated checks pass, including the
  FR-017 tests.

## Assumptions

- **Hub behaviour, from the hub's feature 023 (multi-route output mixing), deployed as API
  0.1.21**: a start with no join mode uses the source's declared default, otherwise *replace*;
  *replace* stops every non-announcement playback on the target's rooms, while announcements keep
  playing and the new playback starts lowered beneath them; the same source on the same target
  returns the existing playback unchanged; the same source on an overlapping target is stopped and
  started on the new one; refusals (too many playbacks on a room, source already there as an
  announcement) name the room. A played link declares no default, so it always replaces. No
  configured source on the production hub declares a default join mode today, so in practice every
  start replaces; the mix and announcement lines are covered by automated tests until such a source
  exists.
- The app does not predict the hub's refusals (its per-room limit is configured on the hub and not
  reported); it shows the hub's answer (FR-016).
- Starting on one room of a group's playback stops the whole group's playback, as moving does
  (confirmed for moves in feature 002); to be checked on the hub for starts.
- **Dependency**: the app's copy of the hub contract has been refreshed to 0.1.21 (join modes:
  `defaultJoinMode` on sources, `joinMode` on playbacks).
- Since 0.1.21 a room may carry several playbacks at once (mixed sources, announcements). Rooms and
  Now Playing still assume one per room; adapting them is a separate feature, recorded in the
  backlog as [Rooms with several playbacks](../../docs/backlog/rooms-with-several-playbacks.md). This feature only
  reads several playbacks per room to build status lines and warnings correctly.
- Turned-off sources are not offered; turning sources on and off belongs to Settings › Sources, a
  later feature.
- Only `http`/`https` links are accepted; other address types are configured on the hub.
- The app never sends a volume with a start: the room plays at the volume it already has, as with
  moves (feature 002).
- Departures from the design (called out per the constitution):
  - The design's sample has Jazz24 and Bedroom preselected; this spec preselects nothing, except
    the room whose play action opened the screen.
  - States the design does not draw reuse existing styles: muted lines (FR-011, "already playing")
    use the warning line's layout in `textMuted`; the "Starting…" button keeps the accent fill;
    the empty source list, stale banner and "Can't reach the hub" reuse the Rooms copy and styles;
    failure messages use the app's existing short-message style.
  - The design's "Living Room · In Downstairs" target implies "Radio Paradise will stop in Living
    Room"; this spec names the group instead ("… will stop in Downstairs"), because the whole
    group's playback stops.
- Following the project workflow, the first implementation commit sets the app version to
  `0.3.0-alpha` (version code 3).
