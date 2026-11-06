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
- Start Playback can preselect a room but not a group.
- When the chosen source already plays on the chosen target, Start Playback says so ("<source> is
  already playing in <room>") but still lets the user tap Play. The hub then refuses the start
  ("already playing"), so the user sees an error for something the screen already told them.

Decisions taken on 2026-11-06 while writing the backlog item are carried over: a group playback
preselects its group; picking the playing source starts nothing; the old Now Playing does not stay
on the back stack; a failed start gets no special warning or undo; a paused playback is replaced
like a playing one.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Switch the station in the room I'm listening to (Priority: P1)

The user is on Now Playing for the Kitchen, where KissFM plays. They tap "Change source…". Start
Playback opens with the Kitchen already selected and the line "KissFM will stop in Kitchen". KissFM
keeps playing while they look. They pick Radio NV and tap Play. Radio NV starts in the Kitchen and
the screen becomes Now Playing for Radio NV. Back leads to Rooms, not to the old KissFM screen.

**Why this priority**: This is the whole wish; the other stories refine it.

**Independent Test**: With a stream playing in one room, open its Now Playing, tap the new action,
pick another configured station, tap Play: Now Playing shows the new station in the same room, and
Back goes to Rooms.

**Acceptance Scenarios**:

1. **Given** Now Playing for a playback in a single room, **When** the user taps "Change source…",
   **Then** Start Playback opens with that room selected and no source selected, and the playback
   goes on unchanged.
2. **Given** Start Playback opened this way and the user picks another source, **When** the
   selection is shown, **Then** the line "<current source> will stop in <room>" is shown above Play,
   as for any busy target.
3. **Given** Start Playback opened this way, **When** the user taps Close or Back without starting,
   **Then** they return to the same Now Playing, and nothing was started or stopped.
4. **Given** the user picked another source and tapped Play, **When** the hub confirms the start,
   **Then** Now Playing for the new playback is shown, and Back from it leads to the screen below
   the old Now Playing (Rooms), not to the old Now Playing.
5. **Given** Start Playback opened this way, **When** the user pastes a link instead of picking a
   source and taps Play, **Then** the link starts on the preselected room, as it would from Rooms.
6. **Given** Start Playback opened this way, **When** the user selects another room, **Then** that
   room becomes the target; the preselection does not lock the choice.

---

### User Story 2 - Change what a group plays (Priority: P1)

The user is on Now Playing for the "Downstairs" group (Kitchen and Living room), where Kraina FM
plays. They tap "Change source…". Start Playback opens with the Downstairs group selected, not one
of its rooms, and the "will stop" line names the group playback. They pick another station and it
starts on the group.

**Why this priority**: Groups are a main way the app is used; preselecting one of the group's
rooms would silently turn a group playback into a single-room one.

**Independent Test**: Start a station on a group, open its Now Playing, tap the action: the group is
selected; start another station: it plays on the whole group.

**Acceptance Scenarios**:

1. **Given** Now Playing for a playback addressed to a group, **When** the user taps "Change
   source…", **Then** Start Playback opens with that group selected.
2. **Given** a group playback that currently plays on fewer rooms than the group has (a member
   turned off), **When** the action is used, **Then** the group is still the one selected, not the
   rooms it plays on right now.
3. **Given** the group selected this way and another source picked, **When** the consequence line
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
2. **Given** that state, **When** the user selects another source, another target, or pastes a link,
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
Playback opens with the Bedroom selected; the "will stop" line reads as for a playing one. Starting
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
- **The old playback ends while Start Playback is open** (stopped from another client, a track
  ends): the target stays selected; the "will stop" line disappears on the next refresh; Play
  starts on the now idle target. After the start the old Now Playing is still not kept below.
- **The preselected target can no longer be chosen** when Start Playback opens (turned off or
  removed in between): nothing is preselected, as when "Play in room" opens for a room that turned
  off, and the user picks a target.
- **The user picks another room** in Start Playback and starts there: the old playback keeps
  playing in its room. After the start the stack is still Rooms → Now Playing (new), the same as
  for a replaced playback; the old one stays reachable from Rooms.
- **The start fails** (the station does not answer): the hub stops the old playback before it
  tries the new one, so the room falls silent. The app shows Start Playback's existing failure
  message; it neither warns beforehand nor offers to restore the old playback. Closing Start
  Playback then returns to the old Now Playing, which finds its playback gone and leaves as it does
  today.
- **A start is still in flight when the user leaves Start Playback**: handled as today (the outcome
  is reported on Rooms).
- **A link that points to what already plays**: links always start a new source, so there is no
  "already playing" check for them; the start replaces the old playback.
- **Mix and announcement sources**: when the chosen source does not replace (its default is to mix
  in or to announce), Start Playback shows "plays alongside" or "will be lowered" as today; the old
  playback goes on after the start, and the old Now Playing is still replaced on the stack.
- **The app is restored after being killed** while Start Playback, opened from Now Playing, was
  showing: the room or group is still the preselected target.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Now Playing MUST offer an action, labelled "Change source…" unless the design pass
  settles on another label, that opens Start Playback. The action itself MUST NOT stop, pause or
  change anything.
- **FR-002**: The action MUST be offered for every live playback Now Playing shows, playing or
  paused, whether or not it can be moved or paused. It MUST be disabled while Now Playing's other
  controls are (hub unreachable).
- **FR-003**: Start Playback opened from the action MUST preselect what the playback was addressed
  to: its room for a single-room playback, its group for a group playback. It MUST NOT preselect
  the rooms the playback happens to play on right now. No source and no link is preselected.
- **FR-004**: Start Playback MUST be able to preselect a group, as it already can a room. Opening
  for a room from Rooms behaves as before.
- **FR-005**: The preselected target MUST remain changeable, and if it cannot be chosen when the
  screen opens (turned off, removed), nothing is preselected.
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
- **FR-011**: The preselected target MUST survive the app being restored by the system, as the
  room preselection does today.
- **FR-012**: The action's place and look on Now Playing MUST come from the design canvas, updated
  before the screen is built (Constitution VI): a third button in the action row, an overflow
  entry or a row under the title. Its touch target is at least 44 dp and, if icon-only, it carries
  a content description.

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
- **SC-002**: In every check on the real hub, the target preselected after the action is the
  playback's own room or group; a group playback is never narrowed to one of its rooms.
- **SC-003**: Selecting the source already playing on the selected target never sends a start: no
  "already playing" refusal from the hub is ever shown after this feature.
- **SC-004**: After a change of source from Now Playing, one Back press reaches the screen below the
  old Now Playing; the old playback's screen is never shown again.
- **SC-005**: The playback keeps playing, without a gap, from the tap on the action until the user
  taps Play.
- **SC-006**: The installable Android app builds and all automated checks pass.

## Assumptions

- The hub replaces what plays on a target when a source with the default join mode starts there,
  and does so for a paused playback as for a playing one. The latter is checked on device (the
  quickstart) before the feature is called done.
- The hub stops the old playback before trying the new one (AGENTS.md "Known hub behaviour"), so a
  failed start leaves the room silent. Changing that order is a hub-side item for the
  `multiroom-ai` backlog, not part of this feature.
- Now Playing knows each playback's target (room or group) from the hub data it already reads; no
  new hub data is needed.
- The label "Change source…" matches "Move to room…" and the app's "Sources" wording; the design
  pass may change it without reopening this spec.
- Rooms still shows one playback per room (see
  `docs/backlog/rooms-with-several-playbacks.md`); once a room can show several, the action will
  have to say which playback it replaces.
- Out of scope: one-tap shortcuts that start a fixed source (`docs/backlog/playback-shortcuts.md`),
  restoring the old playback after a failed start, and any change to "Move to room…".
