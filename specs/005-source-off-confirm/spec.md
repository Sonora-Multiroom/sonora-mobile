# Feature Specification: Confirm Before Turning Off a Playing Source

**Feature Branch**: `005-source-off-confirm`

**Created**: 2026-10-16

**Status**: Draft

**Input**: User description: "@docs/backlog/source-off-stops-playback.md" — since hub API 0.1.25
(hub feature 027) turning a source off stops every playback of it, yet the app turns it off without
asking and then says *"<name> is off. What's playing from it keeps playing."* Ask first, as for
rooms and groups, and drop the false message.

## Background

Feature 004 (Settings and Sources) was written for hub API 0.1.21, on which a source that was
turned off kept playing until someone stopped it. 004 therefore turns a source off at once and tells
the user its playback continues (004 FR-014). Hub API 0.1.25 changed this: turning a source off
now stops every playback of it, in every room and group and in any state, paused included. The
app's behaviour is now the opposite of what happens: playback stops without a question, and the
app then says it keeps playing.

Rooms and groups already ask before they stop playback (004 FR-014). This feature gives configured
sources the same treatment.

Only configured sources ("From configuration" on the Sources tab) have an on/off switch; runtime
sources ("Added from apps") have only a trash button (004 FR-015), which already asks before
stopping playback (004 FR-017). This feature changes only the configured sources' switch.

## Clarifications

### Session 2026-10-16

- Q: Should configured source rows get an amber "Playing in <where>" line like the "Playing ·
  <source>" line of rooms and groups? → A: Yes, in this feature, with a design canvas update
  first (User Story 3, FR-009, FR-010).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Turning off a source that plays asks first (Priority: P1)

The user opens Settings → Sources and flips off a configured source that is playing somewhere (for
example "Radio Paradise", playing in Kitchen and on the Downstairs group). Before anything changes,
the app asks "Turn off Radio Paradise?" and names where it will stop. The user either keeps it
playing or turns it off; only then does the hub stop it.

**Why this priority**: Today the app stops music in the user's home without warning and then says
the opposite of what happened. That breaks 004's promise that nothing is stopped from Settings
without the user confirming it (004 SC-004).

**Independent Test**: With a source playing in one room and on one group, flip its switch off:
the dialog appears and names both; "Keep playing" leaves the switch on and the music playing;
flipping it off again and choosing "Turn off" turns the source off, and both playbacks end.

**Acceptance Scenarios**:

1. **Given** a configured source that is on and plays in Kitchen, **When** the user flips its
   switch off, **Then** a dialog "Turn off <source>?" appears with the text "<source> is playing in
   Kitchen. Turning it off stops playback there." and the buttons "Keep playing" and "Turn off",
   and the switch stays on.
2. **Given** that dialog, **When** the user chooses "Keep playing", presses Back or taps outside it,
   **Then** the dialog closes, nothing is sent to the hub and the switch stays on.
3. **Given** that dialog, **When** the user chooses "Turn off", **Then** the app asks the hub to
   turn the source off, the switch moves to off, and Rooms and Now Playing show the playback gone
   at their next refresh.
4. **Given** a source playing in Kitchen and on the Downstairs group, **When** the user flips it
   off, **Then** the text names both places: "<source> is playing in Kitchen and Downstairs. …".
5. **Given** a source whose only playback is paused, **When** the user flips it off, **Then** the
   dialog appears, because the hub stops paused playback too.

---

### User Story 2 - Turning off an idle source stays one tap, with no false message (Priority: P1)

The user turns off a configured source that nothing plays. It turns off at once, as today. In no
case does the app say that playback keeps going.

**Why this priority**: The false "keeps playing" message must go in the same change that adds the
dialog; on its own it is a user-visible fault.

**Independent Test**: Flip off an idle source: no dialog, no message, the switch turns off. Flip
off a playing source and confirm: no "keeps playing" message appears.

**Acceptance Scenarios**:

1. **Given** a configured source that is on and that no playback uses, **When** the user flips it
   off, **Then** it is turned off at once, without a dialog and without any message.
2. **Given** any configured source, **When** it is turned off by any path, **Then** the message
   "<name> is off. What's playing from it keeps playing." never appears.
3. **Given** a configured source that is off, **When** the user flips it on, **Then** it is turned
   on at once without a dialog, as today.

---

### User Story 3 - Seeing which sources will ask (Priority: P2)

On the Rooms and Groups tabs, playing rows read "Playing · <source>" in amber, so the user can see
which switches will ask before turning off (004 FR-009, FR-010). Configured source rows get the
same cue: while a source plays, its row reads "Playing in <where>" in amber instead of its kind
line, so the user knows before tapping that its switch will ask.

**Why this priority**: The dialog already prevents unconfirmed stops; the status line makes the
Sources tab consistent with the other tabs and shows what is in use at a glance.

**Independent Test**: With one configured source playing in Kitchen and another idle, the playing
one reads "Playing in Kitchen" in amber and the idle one keeps its kind line ("Stream · …").

**Acceptance Scenarios**:

1. **Given** a configured source that plays in Kitchen, **When** the Sources tab is shown, **Then**
   its row reads "Playing in Kitchen" in amber in place of its kind line.
2. **Given** a configured source that plays in Kitchen and on Downstairs, **When** the Sources tab
   is shown, **Then** its row reads "Playing in Kitchen and Downstairs", truncated with an
   ellipsis when it does not fit.
3. **Given** a configured source whose only playback is paused, **When** the Sources tab is shown,
   **Then** its row reads "Playing in <where>" too, because its switch will ask.
4. **Given** a configured source that nothing plays, **When** the Sources tab is shown, **Then** its
   row shows its kind line as today.
5. **Given** a playing source, **When** its playback ends (or starts elsewhere), **Then** the row
   follows at the next refresh.

---

### Edge Cases

- **The playback ends while the dialog is open**: the dialog stays; "Turn off" still turns the
  source off, which is what the user asked for (as for rooms and groups in 004). When the places
  change, the dialog text follows at the next refresh.
- **The playback starts after the switch was flipped off without a dialog** (another client starts
  it in the moment between the tap and the request): the hub stops it when the source is turned
  off. The app does not try to detect this race; it shows the outcome at the next refresh.
- **Several playbacks in the same place** (e.g. the source mixed in twice in one room): the place
  is named once.
- **A playback addressed to a group**: it is named by the group's name, as in the removal dialog
  (004 FR-017), not by its member rooms.
- **A failed or still-starting playback**: it counts as playing, as everywhere else in the app (a
  failed playback still occupies its room until stopped); the hub stops it too.
- **The source is no longer on the hub, or the hub is unreachable, when "Turn off" is chosen**: the
  existing failure handling applies unchanged (004 FR-013).
- **A runtime source with "removed when it stops"** turned off from another client: the hub stops
  its playback and then removes it; the row disappears at the next refresh, as 004 already handles
  for runtime sources the hub removes.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Turning off a configured source that is on and that any playback uses (in any state
  other than stopped: playing, paused, starting or failed) MUST first open the centred
  confirmation dialog in the style of rooms and groups (004 FR-014): title "Turn off <source>?";
  text "<source> is playing in <where>. Turning it off stops playback there.", where <where> names
  each playback's room or group once, joined with " and " (as the removal dialog, 004 FR-017);
  buttons "Keep playing" and "Turn off".
- **FR-002**: While the dialog is open the switch MUST stay on. "Keep playing", Back or a tap
  outside close it without sending anything. "Turn off" sends the request and the switch then
  behaves as any other switch change (004 FR-012, FR-013).
- **FR-003**: The check MUST use the hub's state as last refreshed at the moment of the tap, the
  same rule as rooms, groups and removal.
- **FR-004**: Turning off a configured source that no playback uses, and turning any source on,
  MUST NOT ask and MUST NOT show any message on success.
- **FR-005**: The app MUST NOT show the message "<name> is off. What's playing from it keeps
  playing." in any case; it MUST be removed.
- **FR-006**: The app MUST stop nothing itself: the hub stops the playbacks, and Rooms and Now
  Playing follow at their next refresh.
- **FR-007**: The rule that decides whether to ask, and the dialog text, MUST each live in one
  place, alongside the existing room, group and removal rules, and be covered by tests: idle
  source (no dialog), source playing in one room, in a group, in several places, paused only,
  playing twice in one place (named once), source already off, source unknown to the hub (no
  dialog), and that the "keeps playing" message no longer exists.
- **FR-008**: Feature 004's spec MUST NOT be edited: this spec supersedes its FR-014 sentence on
  sources and the related Clarification and Edge Case, and 004's text stays as the record of what
  it shipped.
- **FR-009**: While a configured source is used by any playback (the same rule as FR-001), its
  row on the Sources tab MUST read "Playing in <where>" in the amber of the rooms' and groups'
  "Playing · <source>" line, in place of its kind line; <where> as in FR-001, on one line,
  truncated with an ellipsis. Otherwise the row keeps its kind line. The rule MUST be the one that
  decides FR-001, so a row reads "Playing in …" exactly when its switch will ask; FR-007's tests
  cover the line for the same cases.
- **FR-010**: The design canvas's Sources tab MUST be updated before implementation to show the
  "Playing in <where>" line and the source turn-off dialog (Constitution VI); the offline copy
  `design/screens/Settings.dc.html` follows it.

### Key Entities

- **Configured source**: a source from the hub's configuration, with an on/off switch on the
  Sources tab. Turning it off now stops every playback of it.
- **Turn-off confirmation for a source**: the source's name and the places (rooms or groups) whose
  playback of it would stop.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: In testing against the real hub, every turn-off of a configured source in use asks
  first and names every room or group where it plays; none stops playback without the user
  choosing "Turn off".
- **SC-002**: Turning off an idle source still takes a single tap.
- **SC-003**: The "keeps playing" message appears in none of the checks.
- **SC-004**: With this feature, 004's promise holds again for every switch on Settings: no
  playback is stopped from Settings without the user confirming it.
- **SC-005**: On the Sources tab, every configured source whose switch would ask reads "Playing in
  …", and no other does, in every check against the real hub.
- **SC-006**: The installable Android app builds and all automated checks pass, including the
  FR-007 and FR-009 tests.

## Assumptions

- The hub runs API 0.1.25 or later (deployed as of 2026-10-16), and `api/openapi.json` is that
  version. There is no version check and no fallback wording for an older hub, as decided for 004.
- The dialog reuses the existing confirmation dialog's layout and colours, and the status line reuses
  the rooms' and groups' amber "Playing" style; only words and the Sources tab layout are new. The
  design update (FR-010) is a precondition of implementation, made locally like the spec.
- Runtime sources keep having no switch, so a source "removed when it stops" cannot be turned off
  from this app; the backlog item's open question about its wording does not arise here.
- The hub stops every playback of the source, paused ones included (hub feature 027); the app
  learns the result only through its regular refresh, as for rooms and groups.
- The app version is bumped to `0.5.0-alpha` / version code 5 at the start of implementation, as
  AGENTS.md requires.
