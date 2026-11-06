# Feature Specification: Change What Plays from Now Playing

**Feature Branch**: `008-change-source-now-playing`

**Created**: 2026-11-06

**Status**: Draft

**Input**: User description: "@docs/backlog/change-source-from-now-playing.md": on Now Playing the
only actions are Stop and "Move to room…". To swap the station or source in the room they are
listening in, the user has to stop it, go back to Rooms and start something new, or find the
room's "Play" entry there. They want a button on Now Playing that opens Start Playback with this
playback's room (or group) already chosen. The music keeps playing while they browse; picking
another source and confirming replaces it.

## Background

Start Playback (MOB-003) already opens for one preselected room ("Play in room" on Rooms), shows
what a start will stop ("Radio Paradise will stop in Bedroom") and, after a successful start, swaps
itself for Now Playing of the new playback. The hub replaces what plays on the target when a new
source starts there, so the app never has to stop the old playback itself. No new hub call and no
hub change is needed.

Three gaps stand between that and the wish:

- Now Playing has no way to open Start Playback.
- Start Playback can preselect a room but not a group, and always lets the user pick another
  target.
- When the chosen source already plays on the chosen target, Start Playback says so ("<source> is
  already playing in <room>") but still lets the user tap Play. The hub then either refuses the
  start ("already playing") or answers with the playback that is already there; the full app test
  (S6) has seen the latter. Either way Play promises a start that does not happen.

Decisions taken on 2026-11-06 while writing the backlog item are carried over: a group playback
preselects its group; picking the playing source starts nothing; the old Now Playing does not stay
on the back stack; a failed start gets no special warning or undo; a paused playback is replaced
like a playing one.

## Clarifications

### Session 2026-11-06

- Q: Start Playback opened from Now Playing preselects the playback's room or group; may the user
  still pick another target there? → A: No. Opened from Now Playing, Start Playback shows the
  target as a fixed line ("In Kitchen", "On Downstairs") instead of the room and group picker, so
  "Change source…" always replaces what plays where the user came from. To start elsewhere the
  user goes through Rooms; to move the playback, "Move to room…". Start Playback opened from Rooms
  keeps its picker (User Story 1, FR-003, FR-005).
- Q: Where does the action go on Now Playing? → A: A round button in the action row, like "Move
  to room…". A playing pauseable playback then has four buttons, drawn as four equal columns of
  64 dp buttons (FR-012).
- Q: How does Start Playback show the fixed target? → A: Title "Change source", and the "Play in"
  section shows one card for the target in place of the picker (FR-012).
- Q: When "Change source…" replaces a playback, should the app stay silent about the old playback
  ending? → A: Yes. While Start Playback opened from a Now Playing is showing, that playback's end
  is not announced, whatever the cause; if the user closes Start Playback and the playback is gone,
  the usual notice appears then (FR-013).
- Q: Should "Change source…" be hidden when the hub reports a playback addressed to a kind of
  target the app doesn't recognise? → A: Yes, hidden, as "Move to room…" already is (FR-002).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Switch the station in the room I'm listening to (Priority: P1)

The user is on Now Playing for the Kitchen, where KissFM plays. They tap "Change source…". Start
Playback opens for the Kitchen: instead of the list of rooms it shows the fixed line "In Kitchen".
Picking a source shows the line "KissFM will stop in Kitchen". KissFM
keeps playing while they look. They pick Radio NV and tap Play. Radio NV starts in the Kitchen and
the screen becomes Now Playing for Radio NV. Back leads to Rooms, not to the old KissFM screen.

**Why this priority**: This is the whole wish; the other stories refine it.

**Independent Test**: With a stream playing in one room, open its Now Playing, tap the new action,
pick another configured station, tap Play: Now Playing shows the new station in the same room, and
Back goes to Rooms.

**Acceptance Scenarios**:

1. **Given** Now Playing for a playback in a single room, **When** the user taps "Change source…",
   **Then** Start Playback opens for that room, shown as a fixed target line in place of the room
   and group picker, with no source selected, and the playback goes on unchanged.
2. **Given** Start Playback opened this way and the user picks another source, **When** the
   selection is shown, **Then** the line "<current source> will stop in <room>" is shown above Play,
   as for any busy target.
3. **Given** Start Playback opened this way, **When** the user taps Close or Back without starting,
   **Then** they return to the same Now Playing, and nothing was started or stopped.
4. **Given** the user picked another source and tapped Play, **When** the hub confirms the start,
   **Then** Now Playing for the new playback is shown, and Back from it leads to the screen below
   the old Now Playing (Rooms), not to the old Now Playing.
5. **Given** Start Playback opened this way, **When** the user pastes a link instead of picking a
   source and taps Play, **Then** the link starts on that room, as it would from Rooms.
6. **Given** Start Playback opened this way, **When** the screen is shown, **Then** no other room
   or group can be chosen on it; Start Playback opened from Rooms ("Play in room", "Play
   something") still shows the picker.

---

### User Story 2 - Change what a group plays (Priority: P1)

The user is on Now Playing for the "Downstairs" group (Kitchen and Living room), where Kraina FM
plays. They tap "Change source…". Start Playback opens for the Downstairs group ("On Downstairs"), not
one of its rooms, and the "will stop" line names the group playback. They pick another station and it
starts on the group.

**Why this priority**: Groups are a main way the app is used; preselecting one of the group's
rooms would silently turn a group playback into a single-room one.

**Independent Test**: Start a station on a group, open its Now Playing, tap the action: the group is
the fixed target; start another station: it plays on the whole group.

**Acceptance Scenarios**:

1. **Given** Now Playing for a playback addressed to a group, **When** the user taps "Change
   source…", **Then** Start Playback opens with that group as its fixed target.
2. **Given** a group playback that currently plays on fewer rooms than the group has (a member
   turned off), **When** the action is used, **Then** the group is still the target, not the
   rooms it plays on right now, and once a source is picked Start Playback's existing note on
   rooms that won't play is shown.
3. **Given** the group as target and another source picked, **When** the consequence line
   is shown, **Then** it names every playback the start would stop, as Start Playback already does
   for a group target.

---

### User Story 3 - Picking what already plays does nothing (Priority: P2)

On Start Playback, the user picks the source that already plays on the selected target (for
example KissFM again for the Kitchen). The screen shows "KissFM is already playing in Kitchen" and
Play cannot be tapped, so no request is sent and the playback goes on. This holds wherever Start
Playback was opened from, Rooms included.

**Why this priority**: Without it the user can provoke a hub refusal and an error message; with the
new entry from Now Playing, the playing source is the first one a user is likely to touch.

**Independent Test**: With KissFM playing in the Kitchen, open Start Playback for the Kitchen (from
Now Playing or from Rooms), pick KissFM: the "already playing" line shows and Play is disabled;
pick another source: Play is enabled again.

**Acceptance Scenarios**:

1. **Given** a source that plays on the selected target, **When** the user selects that source,
   **Then** the line "<source> is already playing in <target>" is shown and Play is disabled.
2. **Given** that state, **When** the user selects another source, another target (where the
   picker is shown), or pastes a link,
   **Then** Play becomes enabled again (if nothing else prevents it) and the line changes to what
   the new choice would do.
3. **Given** Play disabled for this reason, **When** a screen reader reaches Play, **Then** it is
   announced as disabled, and the "already playing" line is readable next to it.
4. **Given** the selected source plays on a group that contains the selected room, but not on the
   room itself, **When** the selection is shown, **Then** this is not "already playing": the "will
   stop" line is shown and Play stays enabled, as today.

---

### User Story 4 - Change a paused playback (Priority: P3)

The user paused a file playing in the Bedroom. On its Now Playing they tap "Change source…". Start
Playback opens for the Bedroom; the "will stop" line reads as for a playing one. Starting
another source replaces the paused playback, which can no longer be resumed.

**Why this priority**: Paused playbacks are less common (only pauseable sources can be paused), but
the action must not disappear or behave differently for them.

**Independent Test**: Pause a pauseable playback, open its Now Playing, use the action, start
another source: the new one plays in that room and the paused one is gone.

**Acceptance Scenarios**:

1. **Given** Now Playing for a paused playback, **When** the screen is shown, **Then** the action is
   offered, as for a playing one.
2. **Given** another source picked for the paused playback's target, **When** the consequence line
   is shown, **Then** it reads "<source> will stop in <target>", with no special wording for a
   paused playback.

---

### Edge Cases

- **The hub is unreachable** (Now Playing is dimmed as stale): the action is disabled, like Stop and
  the other controls.
- **A target type the app does not know** (added by a newer hub): the action is hidden; Stop still
  works.
- **The old playback ends while Start Playback is open** (stopped from another client, a track
  ends): the target stays; the "will stop" line disappears on the next refresh; Play starts on the
  now idle target. No "ended" notice appears over Start Playback (FR-013). After the start the old
  Now Playing is still not kept below.
- **The target can no longer be used** (turned off or removed, when Start Playback opens or while
  it is open): the target line stays, a short note says why ("Kitchen is turned off", "Downstairs
  no longer exists"), and Play is disabled. The user closes the screen; there is no picker to fall
  back to. If the target comes back (turned on again), Play is enabled again.
- **A group with every member turned off or not connected, or with no rooms**: treated like a
  target that can no longer be used. The note reuses the wording Start Playback already has where
  one exists ("All rooms in Downstairs are turned off") and follows the same pattern otherwise
  ("No room in Downstairs is connected", "Downstairs has no rooms"; research R5).
- **The start fails** (the station does not answer): the hub stops the old playback before it
  tries the new one, so the room falls silent. The app shows Start Playback's existing failure
  message; it neither warns beforehand nor offers to restore the old playback. Closing Start
  Playback then returns to the old Now Playing, which finds its playback gone and leaves as it does
  today, with the usual "ended" notice on Rooms.
- **A start is still in flight when the user leaves Start Playback**: handled as today (the outcome
  is reported on Rooms).
- **A link that points to what already plays**: links always start a new source, so there is no
  "already playing" check for them; the start replaces the old playback.
- **Mix and announcement sources**: when the chosen source does not replace (its default is to mix
  in or to announce), Start Playback shows "plays alongside" or "will be lowered" as today; the old
  playback goes on after the start, and the old Now Playing is still replaced on the stack.
- **The app is restored after being killed** while Start Playback, opened from Now Playing, was
  showing: it still shows the same fixed target, without the picker.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Now Playing MUST offer an action labelled "Change source…" that opens Start
  Playback. The action itself MUST NOT stop, pause or change anything.
- **FR-002**: The action MUST be offered for every live playback Now Playing shows, playing or
  paused, whether or not it can be moved or paused, except one addressed to a kind of target the
  app does not recognise (a newer hub's): there it is hidden, as "Move to room…" is. It MUST be
  disabled while Now Playing's other controls are (hub unreachable).
- **FR-003**: Start Playback opened from the action MUST target what the playback was addressed
  to: its room for a single-room playback, its group for a group playback. It MUST NOT target the
  rooms the playback happens to play on right now. No source and no link is preselected.
- **FR-004**: Opened from the action, Start Playback MUST show the target as a fixed line naming
  the room or group, in place of the room and group picker; the user cannot choose another
  target there. Opened from Rooms, Start Playback keeps its picker and its room preselection as
  before.
- **FR-005**: When the fixed target cannot be used (turned off, removed, a group with no member
  that can play), Start Playback MUST keep the target line, say in plain words why it cannot be
  used, and disable Play until it can be used again.
- **FR-006**: The app MUST send only the start; it MUST NOT stop the old playback itself. The
  "will stop", "plays alongside" and "will be lowered" lines MUST read as they do today, with no
  special wording for a paused playback.
- **FR-007**: When the selected source already plays on the selected target, Start Playback MUST
  show the "already playing" line and MUST NOT let Play send a start: Play is disabled and
  announced as disabled. This applies to Start Playback wherever it was opened from.
- **FR-008**: After a successful start from Start Playback that was opened from Now Playing, the
  app MUST show Now Playing for the new playback in place of both Start Playback and the old Now
  Playing, so that Back leads to the screen that was below the old Now Playing. When Start Playback
  was opened from anywhere else, the back stack behaves as today.
- **FR-009**: Closing Start Playback without a start MUST return to the old Now Playing unchanged.
- **FR-010**: A failed start MUST show Start Playback's existing failure message; no new warning,
  confirmation or restore offer is added.
- **FR-011**: The fixed target, and that Start Playback was opened from Now Playing, MUST survive
  the app being restored by the system, as the room preselection does today.
- **FR-012**: The screens MUST follow the design canvas, updated for this feature on 2026-11-06
  (Constitution VI; offline copies `design/screens/NowPlaying.dc.html`, `NowPlayingFile.dc.html`,
  `StartPlaybackFixed.dc.html`):
  - "Change source…" is a round button in Now Playing's action row, drawn like "Move to room…"
    (surface colour, an icon of two opposite arrows, the caption below), placed after Stop and
    Pause/Resume and before "Move to room…".
  - With three buttons or fewer the row keeps the 76 dp buttons. With four (a pauseable playback
    that is playing and can be moved: Stop, Pause, Change source…, Move to room…) the row spans
    the full content width as four equal columns with 64 dp buttons, and a caption may wrap onto
    a second line, so the row fits a 360 dp wide phone.
  - Opened from the action, Start Playback is titled "Change source". Its "Play in" section keeps
    its heading and shows one full-width card (no radio button) with the target's name and, below
    it, what plays there. A group's line starts with "Group ·" ("Group · Playing Radio Paradise");
    a room's reads as its picker tile does ("Playing · KissFM"), with no "Room ·". A target that
    cannot be used is drawn dimmed, with the note of FR-005 above Play ("Downstairs is turned
    off").
  - The "already playing" line keeps its muted colour, and a disabled Play keeps the dimmed
    accent Start Playback already uses.
  - The button's spoken label is "Change source"; its touch target is far above 44 dp.
- **FR-013**: While Start Playback opened from a Now Playing is showing, the end of that Now
  Playing's playback MUST NOT be announced ("Playback on <target> ended"), whether this start
  replaced it or it ended for another reason. After a successful start no such notice appears at
  all. If the user closes Start Playback and the playback is gone, the old Now Playing leaves and
  the usual notice appears, as today, on the refresh it asks for when it is shown again.

### Key Entities

- **Playback target**: what a playback was addressed to, a single room or a group. Distinct from
  the rooms it plays on right now, which can be fewer for a group. This feature hands the target
  from Now Playing to Start Playback.
- **Start consequence**: what a start on the chosen target will do (will stop, plays alongside,
  will be lowered, already playing), already computed by Start Playback. "Already playing" now
  also decides whether Play can be tapped.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: From Now Playing, the user can replace what plays in that room or group with another
  configured source in three taps (the action, a source, Play), without passing through Rooms and
  without stopping the playback first.
- **SC-002**: In every check on the real hub, the target after the action is the playback's own
  room or group and cannot be changed on that screen; a group playback is never narrowed to one of
  its rooms.
- **SC-003**: Selecting the source already playing on the selected target never sends a start, so
  the hub's "already playing" refusal (or its answer with the existing playback) can no longer be
  provoked from that selection.
- **SC-004**: After a change of source from Now Playing, one Back press reaches the screen below the
  old Now Playing; the old playback's screen is never shown again.
- **SC-005**: The playback keeps playing, without a gap, from the tap on the action until the user
  taps Play.
- **SC-006**: The installable Android app builds and all automated checks pass.

## Assumptions

- The hub replaces what plays on a target when a source with the default join mode starts there,
  and does so for a paused playback as for a playing one. The latter is checked on device (the
  quickstart) before the feature is called done; if the hub does not replace a paused playback,
  implementation stops and the user decides how to proceed.
- The hub stops the old playback before trying the new one (AGENTS.md "Known hub behaviour"), so a
  failed start leaves the room silent. Changing that order is a hub-side item for the
  `multiroom-ai` backlog, not part of this feature.
- Now Playing knows each playback's target (room or group) from the hub data it already reads; no
  new hub data is needed.
- The label "Change source…" matches "Move to room…" and the app's "Sources" wording.
- Rooms still shows one playback per room (see
  `docs/backlog/rooms-with-several-playbacks.md`); once a room can show several, the action will
  have to say which playback it replaces.
- Out of scope: one-tap shortcuts that start a fixed source (`docs/backlog/playback-shortcuts.md`),
  restoring the old playback after a failed start, choosing another target from the "Change
  source…" entry (Rooms covers that), and any change to "Move to room…".
