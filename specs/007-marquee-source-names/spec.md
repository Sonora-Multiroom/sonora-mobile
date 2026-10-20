# Feature Specification: Scroll Long Source Names

**Feature Branch**: `007-marquee-source-names`

**Created**: 2026-10-20

**Status**: Draft

**Input**: User description: "@docs/backlog/scrolling-long-source-names.md" — since hub API 0.1.28
a played link's source is named after the title it resolved to (for example "Flickermood —
Forss"). Such names are often longer than the line they sit on, and the "…" cuts off the part that
tells tracks apart. A name that does not fit should scroll by itself, as a marquee.

## Background

The Rooms card shows the source name under the room title on one line, cut off with "…". Now
Playing shows it as the screen's title over at most two lines, then cuts it off too. Before hub API
0.1.28 source names were short configured names ("Radio Paradise", "Line in") and fitted. Since
0.1.28 a link is named after the title it resolves to, and these titles are long: the artist,
which tells tracks apart, usually comes last and is the part the "…" hides.

The app already shows the hub's name for each source; no new hub data is needed. This feature
changes only how a name that does not fit is drawn.

## Clarifications

### Session 2026-10-20

- Q: Now Playing: one scrolling line like the Rooms card, or keep the two-line wrap and scroll only
  when two lines are not enough? → A: Keep the two-line wrap; a name that needs more than two
  lines is shown as one scrolling line (User Story 2, FR-001).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Reading a long source name on a Rooms card (Priority: P1)

The user plays a SoundCloud link in the Kitchen. Its source is named "Flickermood — Forss (Original
Mix, 2003 remaster)", longer than the card's source line. On Rooms, the name starts still, then,
after a short pause, slides slowly from right to left until its end has passed, pauses, and
repeats, so the user can read the whole name without touching it. A short name on another card
("Radio Paradise") stays still and shows no "…".

**Why this priority**: Rooms is the screen the user sees most, and since hub 0.1.28 every link
played shows there with a name that is cut off.

**Independent Test**: Play a link with a long resolved title in one room and a configured stream
with a short name in another; on Rooms the long name scrolls on its own and the short one stays
still, and the whole long name can be read by waiting.

**Acceptance Scenarios**:

1. **Given** a Rooms card whose source name is wider than its line, **When** Rooms is shown,
   **Then** the name shows its beginning, still, for one to three seconds, then scrolls slowly from right
   to left until its end has been shown, pauses, and repeats for as long as the card is shown.
2. **Given** a Rooms card whose source name fits its line, **When** Rooms is shown, **Then** the
   name stays still and has no "…".
3. **Given** a scrolling name, **When** the app refreshes from the hub (every ~2.5 s) and the name
   is unchanged, **Then** the scrolling continues smoothly and does not jump back to the start.
4. **Given** a scrolling name, **When** the name changes (another source starts in that room),
   **Then** the new name starts again from its beginning, with the same initial pause, if it does
   not fit, or stands still if it fits.
5. **Given** a scrolling name, **When** it is cut at the right edge of its line, **Then** the edge
   fades out softly instead of ending in a hard cut, the first letter of a name waiting at its
   start is not dimmed, and the text never overlaps the card's buttons.

---

### User Story 2 - Reading a long source name on Now Playing (Priority: P1)

The user opens Now Playing for the Kitchen. The title shows the source name. If the name does not
fit, it scrolls the same way as on the Rooms card, so the full title can be read.

**Why this priority**: Now Playing is where the user goes to look at one playback closely; a cut
title there is as bad as on Rooms.

**Independent Test**: Open Now Playing for a room playing a link whose resolved title is too long
for the title space: the whole title can be read without touching it.

**Acceptance Scenarios**:

1. **Given** a playback whose source name needs more than two lines of the Now Playing title,
   **When** Now Playing is shown, **Then** the name is shown as one line that scrolls as in User
   Story 1, scenario 1.
2. **Given** a source name that fits on one or two lines, **When** Now Playing is shown, **Then**
   it wraps as today, stays still and has no "…".

---

### User Story 3 - Scrolling stays calm and truthful (Priority: P2)

Movement on screen should not suggest something the app does not know, and should not get busy.
When the app cannot reach the hub its cards are dimmed as stale, and a moving name would make a
frozen state look alive, so it stands still. When the app is not in the foreground nothing moves.
When the phone's "Remove animations" setting is on, nothing scrolls. When several long names are
on Rooms at once, they do not all move in step.

**Why this priority**: The scrolling itself (Stories 1 and 2) delivers the value; this story keeps
it honest (Constitution II) and comfortable to look at.

**Independent Test**: Cut the phone's network while a long name scrolls: the card dims and the name
stops at its beginning; restore the network: it starts scrolling again. Turn on "Remove
animations": long names stand still. Play long links in two rooms: their names do not move in
lockstep.

**Acceptance Scenarios**:

1. **Given** a scrolling name on Rooms or Now Playing, **When** the app marks the hub state as
   stale (the card or screen is dimmed), **Then** the name goes back to its beginning and stands
   still, ending in "…" if it does not fit, until the state is fresh again.
2. **Given** a scrolling name, **When** the app leaves the foreground or another window takes the
   focus, **Then** the scrolling stops, and when the app is in front again the name starts again
   from its beginning, with the initial pause.
3. **Given** the phone's "Remove animations" setting is on, **When** a name does not fit, **Then**
   it stands still at its beginning and ends in "…", as before this feature.
4. **Given** two or more Rooms cards whose names do not fit, **When** Rooms is shown, **Then** their
   names start scrolling at different moments, so they do not move in step.

---

### Edge Cases

- **A name that only just fits**: it stands still; it scrolls only when it is actually wider than
  its line (on Now Playing: longer than two lines).
- **The line gets narrower or wider** (a "Group" badge, a font size change in the phone's settings,
  rotation): whether a name scrolls is decided again from the space it has now.
- **The hub renames the source while it plays** (for example a generated placeholder replaced by the
  resolved title, or a rename from another client): treated as a name change, User Story 1
  scenario 4.
- **Rooms reorders or a card appears or disappears**: the other cards' names keep scrolling where
  they were; a newly shown card's name starts from its beginning.
- **Screen readers**: TalkBack reads the whole name once, whether it scrolls or not, and is not
  affected by the movement.
- **Very long names** (a hub name can be long; renames are capped at 64 characters, resolved
  titles are not): they scroll at the same speed, so they simply take longer to pass.
- **Names in right-to-left scripts**: they scroll in the direction the platform uses for them; no
  special handling is specified.
- **Other places a source name appears** (the Move to room sheet, Start Playback, "will stop"
  notes, Settings → Sources, room titles and status lines): unchanged, they keep their "…".

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The source name on a Rooms now-playing card MUST, when wider than its one line,
  scroll by itself from right to left as a marquee; the user does not drag or tap it to scroll.
  The source name in the Now Playing title MUST keep wrapping to at most two lines and, only when
  two lines are not enough, be shown as one line that scrolls the same way.
- **FR-002**: A scrolling name MUST first show its beginning still for about 1.2 s (on Rooms up to
  about 3 s, so that several names start at different moments, FR-009), then move at
  a slow, steady reading speed (about 30 dp per second), and after its end has been shown pause
  and repeat for as long as it is shown.
- **FR-003**: A name that fits its space MUST stand still and MUST NOT show "…". A name that scrolls
  MUST NOT show "…" either.
- **FR-004**: Where a scrolling name is cut at the right edge of its space, the edge MUST fade out
  softly instead of cutting hard; the left edge stays crisp so the first letter of a name at rest is
  not dimmed. The text MUST stay inside its space (never under the card's
  buttons, the status chip or other text).
- **FR-005**: A refresh from the hub that brings the same name MUST NOT restart or disturb a
  scrolling name. It starts again from its beginning, with the initial pause, when the name
  changes, when it appears anew (screen opened, card newly shown), after it was stopped (stale,
  window not focused, animations off) or when the width of its line changes.
- **FR-006**: A name MUST stand still at its beginning, ending in "…" when it does not fit, while
  the hub state it belongs to is shown as stale, and resume scrolling once it is fresh again.
- **FR-007**: Scrolling MUST stop while the app is not in the foreground or not focused.
- **FR-008**: When the phone's "Remove animations" (animator duration scale off) setting is on,
  names MUST NOT scroll; a name that does not fit then stands still and ends in "…".
- **FR-009**: When several Rooms cards have scrolling names, their scrolling MUST NOT start at the
  same moment, so that they do not move in step.
- **FR-010**: The scrolling-name behaviour MUST be defined once and used in both places, so that
  Rooms and Now Playing scroll alike.
- **FR-011**: The name's spoken description for screen readers MUST be the full name, unchanged by
  the scrolling.
- **FR-012**: All other text keeps its current single-line or two-line truncation with "…": room
  and group titles, status lines, the Now Playing subtitle and target line, the Move to room
  sheet, Start Playback and Settings.
- **FR-013**: The departure from the design canvas (which draws a static name ending in "…") is
  called out here, as Constitution VI allows: a name that does not fit scrolls, with soft fading
  edges, in place of the "…". The canvas is not changed, since it cannot show movement.

### Key Entities

- **Source name**: the hub's display name for the source a playback plays; for a played link, the
  title it resolved to, or a name given by the user or a generated placeholder. Already shown by
  the app; this feature changes only how it is drawn when it does not fit.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: On the real hub, for every played link whose name is longer than its line, the whole
  name can be read on Rooms and on Now Playing within one scroll cycle, without touching the
  screen.
- **SC-002**: No source name on Rooms or Now Playing ends in "…" while the app is fresh, in the
  foreground and animations are on.
- **SC-003**: Over one minute of watching Rooms with a scrolling name, the name never jumps back to
  its beginning except at the end of a cycle, although the app refreshes about 24 times.
- **SC-004**: With the network cut (stale state), and with "Remove animations" on, no name moves.
- **SC-005**: Names that fit look exactly as before this feature.
- **SC-006**: The installable Android app builds and all automated checks pass.

## Assumptions

- The hub runs API 0.1.28 or later, and `api/openapi.json` is that version. On an older hub the
  same behaviour applies to whatever names it gives; long names are simply rarer.
- The app already reads the source's display name from the hub; no new hub call, field or
  dependency is needed. The scrolling uses what the UI toolkit already provides.
- Settings → Sources rows are out of scope, although runtime sources there carry the same long
  names; the backlog proposal keeps their "…", and a later item can extend this if needed.
  Renaming a source (hub 0.1.28 `display-name`) is a separate backlog item, not this feature.
- Whether the scrolling looks too busy with many long names, and whether "Remove animations" is
  honoured, is confirmed on the POCO during on-device verification.
- The backlog item suggested the tinyspec path; the full workflow is used at the user's request.
  The app version is bumped to `0.7.0-alpha` / version code 7 at the start of implementation, as
  AGENTS.md requires, and the full app test gains rows for this behaviour before the pull request.
