# Feature Specification: Rooms a Playback Is Really On, and Plain Refusal Messages

**Feature Branch**: `006-route-outputs-refusals`

**Created**: 2026-10-17

**Status**: Draft

**Input**: User description: "@docs/backlog/refusal-reasons-and-route-outputs.md" — use the hub's
newer route data: the list of rooms a playback really plays on (API 0.1.22), so a room that was
turned off stops being counted in its group's playback; and plain messages for the refusals the hub
added for things that are turned off, plus one quiet retry for the brief "rooms are changing"
refusal.

## Background

A playback addressed to a group used to be shown on every room of that group, because the hub only
said which group was addressed. Since hub API 0.1.22 every playback also lists the rooms it plays on
right now. When a member room is turned off, the hub takes it out of that playback (the rest of the
group plays on); since API 0.1.25, turning the room back on puts it back into the same playback.

The app still counts every member. Seen on the Pixel 5 on 2026-10-17 (`0.5.0-alpha+33`, hub
0.1.25), with KissFM playing on the Living Room group (Office Speaker and Kitchen Speakers) and
Office Speaker turned off, while the hub reported the playback on Kitchen Speakers only:

- Rooms: the card reads "Office Speaker + Kitchen Speakers", "2 of 4 rooms in use", and Office
  Speaker is not listed under "Off".
- Now Playing: "on Living Room · Office Speaker + Kitchen Speakers", with a volume pill for Office
  Speaker.
- Move to room…: "Kitchen Speakers only — Office Speaker stops", though Office Speaker is not
  playing.

Start Playback already shows the group correctly ("Won't play in Office Speaker (turned off)").

Separately, since API 0.1.22 the hub refuses to start or move a playback onto a room, group or
source that is turned off, naming why (the room is off, the group is off, the source is off, or every
room of the group is off), and since API 0.1.25 it can briefly refuse a move while a room that was
just turned back on rejoins the playback. The app shows only "Couldn't start playback" or "Couldn't move <source> to
<place>" for all of these.

## Clarifications

### Session 2026-10-17

- Q: A group card whose playback runs on only some of the group's rooms: does it say so beyond
  listing the rooms? → A: No. The rooms line lists only the rooms playing, and the rooms left out
  appear under "Off" like any room that is turned off; no design change (FR-004).

### Session 2026-10-18

- Q: In Move to room…, a "<room> only" row of a group playback that already plays on that room
  alone (the other members were turned off) has no room left that would stop. What does its note
  say? → A: "Leaves <group>" (e.g. "Leaves Living Room"), in the normal note style, not as a
  warning: the move turns the group playback into a room playback, so the rooms turned off will
  not rejoin it. This is the one copy addition to the Move sheet's design.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - A room that was turned off is no longer shown as playing (Priority: P1)

The user has music playing on a group and turns one of the group's rooms off (in Settings, or from
another app). The hub takes that room out of the playback; the rest of the group plays on. Every
screen of the app now shows the playback only in the rooms where it is really heard, and the room
that was turned off appears as "Off". When the user turns the room back on, it rejoins the playback
and the screens show it there again.

**Why this priority**: The app tells the user music plays in a room where it does not, offers a
volume control for a room that is silent, and warns that moving the playback will stop a room that
is not playing. That breaks the rule that the app shows only what the hub knows.

**Independent Test**: Start a source on a two-room group, turn one room off, and check Rooms, Now
Playing and Move to room…: the playback shows on the remaining room only, the off room is under
"Off", and nothing names it as playing or stopping. Turn the room back on: within a refresh it is
shown in the playback again.

**Acceptance Scenarios**:

1. **Given** a playback on a group of Office Speaker and Kitchen Speakers, with Office Speaker turned
   off and the hub reporting the playback on Kitchen Speakers only, **When** the Rooms screen is
   shown, **Then** the group card lists only "Kitchen Speakers", the "rooms in use" count counts only
   Kitchen Speakers for it, and Office Speaker is listed under "Off".
2. **Given** the same state, **When** the user opens Now Playing for that playback, **Then** the
   heading names the group and "Kitchen Speakers" only, and there is no volume pill for Office
   Speaker.
3. **Given** the same state, **When** the user opens Move to room…, **Then** the "Kitchen Speakers
   only" row does not say that Office Speaker stops (it reads "Leaves Living Room"), and the "Office
   Speaker only" row reads "Turned off" as today.
4. **Given** the same state, **When** the user drags the group's volume pill, **Then** only Kitchen
   Speakers' volume changes, and the pill shows the loudest of the rooms that play.
5. **Given** the same state, **When** the user turns Office Speaker back on and the hub reports the
   playback on both rooms again, **Then** at the next refresh every screen shows the playback on
   both rooms, with Office Speaker's pill back on Now Playing.
6. **Given** a playback whose list of rooms the hub does not report, **When** any screen shows it,
   **Then** it is shown as today: a group playback on every known member room, a room playback on
   its room.
7. **Given** Settings → Rooms with Office Speaker turned off while the group plays without it,
   **When** the tab is shown, **Then** Office Speaker's row does not read "Playing · KissFM".

---

### User Story 2 - A refusal says what is turned off (Priority: P2)

The user starts or moves a playback onto a room, group or source that someone has just turned off
from another app (the app's own lists are a refresh behind). Instead of "Couldn't start playback",
the app says what is off, for example "Office Speaker is turned off", and refreshes so the list
shows it as off.

**Why this priority**: The app hides things that are off, so these refusals are rare; when they
happen, a generic failure leaves the user guessing and retrying.

**Independent Test**: With the app open on Start Playback, turn the chosen room off with a direct
request to the hub, then start on it from the app: the message names the room as turned off, and
the room shows as off right after.

**Acceptance Scenarios**:

1. **Given** the user starts a source or link on a room that was turned off since the last refresh,
   **When** the hub refuses because that room is off, **Then** the app shows "<room> is turned off"
   and refreshes.
2. **Given** the user starts on a group that was turned off, **When** the hub refuses because the
   group is off, **Then** the app shows "<group> is turned off" and refreshes.
3. **Given** the user starts on a group whose rooms were all turned off, **When** the hub refuses
   for that reason, **Then** the app shows "All rooms in <group> are turned off" and refreshes.
4. **Given** the user starts a configured source that was turned off, **When** the hub refuses
   because the source is off, **Then** the app shows "<source> is turned off" and refreshes.
5. **Given** the user moves a playback to a room or group that was turned off, **When** the hub
   refuses for any of these reasons, **Then** the app shows the same message for it and refreshes.
6. **Given** the hub refuses with a reason the app does not know, **When** the message is chosen,
   **Then** the generic message is shown as today.

---

### User Story 3 - Moving right after turning a room back on just works (Priority: P2)

The user turns a room of a playing group back on in Settings and, a moment later, moves that
playback. For a moment the hub is still putting the room back into the playback and refuses the
move as "changing rooms". The app waits briefly and tries once more, so the move succeeds without
the user seeing an error.

**Why this priority**: This is the refusal a user can reach with the app alone, by a natural
sequence of taps.

**Independent Test**: Turn a member of a playing group back on and immediately move the playback:
the move succeeds without a message. With a hub that keeps refusing (simulated in tests), the user
sees one message, after a single retry.

**Acceptance Scenarios**:

1. **Given** a move the hub refuses as "changing rooms", **When** the app retries once after a
   short delay and the hub accepts, **Then** the move completes as any other move, with no message.
2. **Given** a move the hub refuses as "changing rooms" twice, **When** the retry is also refused,
   **Then** the app shows "Couldn't move <source> to <place>. Try again in a moment." and does not
   retry again.
3. **Given** a retry is pending, **When** the user closes Now Playing, **Then** the retry still
   runs or is dropped without effect on another screen, and no message about it appears on a screen
   that did not ask for the move.

---

### Edge Cases

- **The hub lists a room the app does not know**: it is left out of names and volumes, as unknown
  group members are today.
- **The hub lists no rooms at all for a live playback** (for example while it starts): the playback
  is shown on its addressed room or group, as when the list is missing, so a starting playback does
  not vanish from Rooms.
- **A room playback whose list names its room**: shown as today; the list changes nothing.
- **A playback listed on a room that is outside the group it was addressed to**: the app trusts the
  list and shows it there.
- **Every room of a group turned off**: what the hub then does with the playback is its own
  decision; the app shows whatever the hub reports at the next refresh.
- **Start Playback's "will stop" notice**: a playback that has left a room that was turned off is not
  named as stopping there (those rooms cannot be chosen anyway).
- **A refusal that names a room by id**: the room's name comes from the latest refresh; an unknown id
  falls back to the chosen room or group's name, as for the existing "can't play more at once"
  refusal.
- **A source that is turned off while it plays**: since API 0.1.25 the hub stops its playback, so a
  later move of it is answered as "no longer on the hub" (existing handling), not as "turned off".
- **The hub is unreachable during the retry**: the existing "Can't reach the hub" handling applies.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The app MUST take, for each playback, the rooms the hub reports it plays on right now,
  and use them wherever it decides which rooms a playback occupies: the Rooms cards and their room
  lists, the "rooms in use" count, the Idle and Off lists, Now Playing's heading and volume pills,
  the Move to room… notes, Start Playback's "will stop" notice, and the Settings Rooms tab's
  "Playing · <source>" line and turn-off confirmation.
- **FR-002**: When the hub reports no rooms for a playback, or reports none it can match, the app
  MUST fall back to today's rule: a room playback occupies its room, a group playback every known
  member of its group.
- **FR-003**: The rule that decides which rooms a playback occupies MUST live in one place and be
  shared by every screen in FR-001, so no two screens can disagree.
- **FR-004**: A group card whose playback runs on fewer rooms than its group has MUST list only the
  rooms it plays on and add nothing further (no "N of M rooms" status, no names of the rooms left
  out); the rooms left out appear under "Off" by FR-001.
- **FR-005**: A group's volume pill and its drag MUST be based only on the rooms the playback plays
  on: the pill shows the loudest of them, and a drag scales only them, by the existing scaling rule.
  Rooms left out keep their volume.
- **FR-006**: When starting playback (a configured source or a link) is refused because a room, a
  group, a source or every room of a group is turned off, the app MUST show, respectively, "<room>
  is turned off", "<group> is turned off", "<source> is turned off" or "All rooms in <group> are
  turned off", and MUST refresh the hub's state at once.
- **FR-007**: When a move is refused for one of the FR-006 reasons, the app MUST show the same
  message and refresh at once.
- **FR-008**: When a move is refused because the playback's rooms are changing, the app MUST retry
  the same move once after a short delay (about one second). If the retry succeeds, the move
  completes with no message; if it is refused again for the same reason, the app MUST show
  "Couldn't move <source> to <place>. Try again in a moment."; any other answer to the retry is
  handled as an answer to a first attempt.
- **FR-009**: The room named in a refusal MUST be the one the hub names when it names one and the app
  knows it, otherwise the chosen room or group.
- **FR-010**: Refusals with reasons the app does not recognise MUST keep today's messages. No message
  MUST contain the hub's own wording.
- **FR-011**: The mapping of refusals to messages MUST stay in the one place where error copy is
  written, and be covered by tests for every reason in FR-006 and FR-008, for starts of both kinds
  and for moves, for a known and an unknown named room, and for an unknown reason.
- **FR-012**: The occupancy rule MUST be covered by tests: a group playback on all members, on
  some members (one turned off), with the list missing, with an empty list, with an unknown room in
  the list; a room playback; and the screens' results for the 2026-10-17 case (card rooms, count,
  Off list, Now Playing pills, move notes, Start Playback notice, Settings line).
- **FR-013**: Once shipped, the project's agent guide MUST record that the app uses the rooms list
  (the "no rooms in a playback's answer" hub gap is closed for the app) and the newly mapped
  refusal reasons; the backlog item MUST be deleted or reduced to what remains.

### Key Entities

- **Playback (route)**: what plays, where it was addressed (a room or a group), and now also the
  rooms it plays on right now, which can be fewer than the addressed group's rooms.
- **Occupied rooms of a playback**: the rooms the app treats as playing it, taken from the hub's
  list, or from the addressed room or group when the list is absent or empty.
- **Refusal**: the hub's answer that a start or move was not admitted, with a reason (room off,
  group off, source off, every room off, rooms changing, room full, already playing there) and
  optionally the room it concerns.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: In the 2026-10-17 case re-run against the real hub, no screen shows the turned-off
  room as playing, offers a volume pill for it, or names it as stopping; it is listed as "Off".
- **SC-002**: After a room is turned back on, every screen shows it in the playback again within one
  refresh of the hub reporting it.
- **SC-003**: Each of the four "turned off" refusals, when triggered against the real hub, shows a
  message that names what is off, never "Couldn't start playback" or "Couldn't move …".
- **SC-004**: Turning a group member back on and moving the group's playback straight away succeeds
  without an error message in every attempt on the device.
- **SC-005**: With a hub that does not report the rooms list (simulated in tests), every screen
  behaves exactly as before this feature.
- **SC-006**: The installable Android app builds and all automated checks pass, including the
  FR-011 and FR-012 tests.

## Assumptions

- The hub runs API 0.1.25 or later and `api/openapi.json` is that version; the rooms list is part of
  that contract. There is no version check: an older hub simply omits the list (FR-002).
- The refusal reasons (`OUTPUT_DISABLED`, `GROUP_DISABLED`, `INPUT_DISABLED`,
  `ALL_MEMBERS_DISABLED` from API 0.1.22, `ROUTE_CHANGING_OUTPUTS` from 0.1.25) are confirmed in the
  hub's source and documented in the project's agent guide; the contract's description of the reason
  field still names only the two older ones. That is a documentation gap on the hub side, recorded in
  the hub's backlog (`error-reason-schema-description`), not a reason to wait.
- The app keeps showing one playback per room (the separate backlog item on rooms with several
  playbacks is not part of this feature), and still never sends a join mode.
- Messages appear where start and move failures appear today; only their words change. The design
  needs no update: cards, lists and pills keep their look and only show different rooms (FR-004).
- "Rooms left out keep their volume" (FR-005) is intended: a room that rejoins plays at the volume it
  had, which is what the hub does.
- The app version is bumped to `0.6.0-alpha` / version code 6 at the start of implementation, as
  the agent guide requires.
