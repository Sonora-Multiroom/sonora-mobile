# Feature Specification: Find and Play Radio Stations

**Feature Branch**: `009-radio-stations`

**Created**: 2026-11-16

**Status**: Draft

**Input**: User description: "@docs/backlog/radio-stations.md": the hub's radio extension can search
the radio-browser.info catalogue and play a result, and the app has nothing that uses it. Let the
user find a station in the app and play it in a room or group, as the backlog item's accepted UX
proposal and its eight decisions describe.

## Background

Today the app plays an internet station only when it is a source configured on the hub or when the
user pastes its stream address into Start Playback (MOB-003). Finding a station means leaving the
app, looking up a stream address somewhere and pasting it.

The hub now has a radio extension (`sonora-radio` RADIO-001 and RADIO-002, version 0.2.0, active on
the home hub since 2026-11-15). It searches the community catalogue radio-browser.info by name,
country, tag and language, and it plays a station from a short station address that the hub turns
into the current stream by itself. The extension is optional: a hub may not have it.

The constitution (1.6.0, Principle I) allows the app to use the extension's read-only requests and
requires radio to be optional in the app. The backlog item
[radio-stations.md](../../docs/backlog/radio-stations.md) records what the hub offers, checked
against the live hub on 2026-11-16, and the decisions this spec starts from:

- Radio lives inside Start Playback: a station is one more kind of source. There is no Radio tab.
- The app offers radio only while the hub reports the radio extension as active.
- The first time, the station list is narrowed to the country of the phone's region; afterwards to
  the filters last used.
- The last ten stations started from this phone, and listened to for a while, are kept on the
  phone as "Recent".
- A station that plays is shown as kind "Radio", whichever client started it.
- The catalogue's labels are called "Tag" in the app.
- Station rows carry no mark for the stream technology (HLS).

Facts about the hub that shape the behaviour below:

- A station that plays cannot be paused, and the hub names the source after the station.
- Stopping a station removes its source from the hub. Tomorrow the station is not in the source
  list any more.
- When a station cannot be started the hub says only that it failed, not why.
- A search never returns stations the catalogue marks as broken.
- Station names and tags are text edited by the catalogue's community. A station may lack a name,
  a country, a codec or a bitrate.
- A list of stations comes in pages, and the catalogue serves at most the first 10,000 stations of
  any one search.

## Clarifications

### Session 2026-11-16

- Q: Should the most recent stations also be offered directly in Start Playback, so that replaying
  one does not require opening the radio screen first? → A: Yes. The three most recent stations
  appear in Start Playback under the "Find a radio station" row, selectable like sources; all ten
  stay on the radio screen.
- Q: In which language should the country names in the Country filter be shown? → A: English, like
  the rest of the app; the names come from the catalogue's English names.
- Q: On the radio screen, should the "Recent" section stay visible when a country, tag or language
  filter is chosen but nothing is typed in the search field? → A: Yes. "Recent" shows whenever the
  search field is empty, whatever filters are chosen, and is not itself filtered.
- Q: Should the user be able to remove a single station from "Recent"? → A: Yes, on the radio
  screen only: each "Recent" row there has a remove action, and a removed station also disappears
  from Start Playback.
- Q: Where does the order choice sit, given that four chips do not fit the width of a phone? → A:
  Not among the chips. Three filter chips (country, tag, language) stay in the row; the order is a
  control beside the list's heading. A chosen filter's value is cut with "…" when long, and the
  row scrolls sideways if it still does not fit.
- Q: How is a station name shown that is too long for its line? → A: In lists (the radio screen,
  "Recent", the filter sheets, unselected rows in Start Playback) on one line ending in "…"; two
  lines were tried on the canvas and rejected. The station selected in Start Playback scrolls its
  name as a marquee. The Play button cuts only the station's name, so the target always shows.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Find a station by name and play it in a room (Priority: P1)

I want to listen to a station I know by name. I open Start Playback, tap "Find a radio station",
type part of the name, see matching stations, choose one and come back to Start Playback with it
selected. I choose a room as always, see what will stop there, and press Play. The station plays
and I land on its Now Playing.

**Why this priority**: This is the feature. Without it the user still leaves the app to find a
stream address.

**Independent Test**: With the radio extension active on the hub and an idle room, open "Play
something", open the radio screen, type "mango", choose MANGORADIO, pick the room and press Play:
the station is audible and Now Playing shows it.

**Acceptance Scenarios**:

1. **Given** the hub reports the radio extension as active, **When** the user opens Start Playback
   by any of its three ways in ("Play something", "Play in <room>", "Change source…"), **Then** a
   row "Find a radio station" is offered between the link field and the source list.
2. **Given** the radio screen is open, **When** the user types part of a station name and stops
   typing, **Then** the list shows the stations whose name contains that text, without the user
   pressing a search button.
3. **Given** a list of stations, **When** the user looks at a row, **Then** it shows the station's
   name and, on a second line, the parts the catalogue knows of country, codec and bitrate (for
   example "DE · MP3 · 128 kbps"); a part the catalogue does not know is left out.
4. **Given** a list of stations, **When** the user taps a row, **Then** the radio screen closes
   and Start Playback shows that station as the selected source, with nothing playing yet.
5. **Given** a station is selected in Start Playback and a room is chosen, **When** the room is
   busy, **Then** the usual note says what will stop there, and the Play button names the station
   and the room ("Play MANGORADIO in Bedroom").
6. **Given** a station and a target are chosen, **When** the user presses Play and the hub starts
   it, **Then** the app shows that playback's Now Playing, exactly as after starting a pasted link.
7. **Given** Start Playback was opened by "Change source…" for a room or group, **When** the user
   chooses a station and presses Play, **Then** the station replaces what played on that room or
   group, with no target to pick.
8. **Given** a station is selected in Start Playback, **When** the user picks a configured source
   or types into the link field, **Then** the station is no longer selected; **and When** the user
   taps the row of a selected station that is not one of the recent stations listed there, **Then**
   the radio screen opens again to choose another. (A selected recent row stays selected when
   tapped; the "Find a radio station" row above it opens the radio screen.)
9. **Given** more stations match than one page holds, **When** the user scrolls towards the end of
   the list, **Then** further stations are added below, and no station appears twice.

---

### User Story 2 - A playing station looks like a station (Priority: P2)

When a station plays, in a room of mine or started by someone else in the house from another
client, the app shows it for what it is: a live radio stream. I see "Live stream" on its Rooms
card, I am told it cannot be paused, and nowhere do I see a technical address in place of a
description.

**Why this priority**: Without it the app shows a station as a "Link" with a raw technical address
and does not explain why there is no Pause, which is untruthful and confusing. It already affects
stations started from other clients, so it has value even on its own.

**Independent Test**: Start a station on the hub from another client. In the app, the Rooms card
shows "Live stream", Now Playing shows the station's name, the kind "Radio", Stop without Pause,
and the line "Live streams can't be paused".

**Acceptance Scenarios**:

1. **Given** a station plays in a room, **When** the user looks at Rooms, **Then** the card shows
   the station's name and the status "Live stream".
2. **Given** a station plays, **When** the user opens its Now Playing, **Then** it shows Stop and
   no Pause, the line "Live streams can't be paused", and the description "Radio" with no address
   after it.
3. **Given** a station plays, **When** it appears in a list of sources, **Then** its tile uses the
   stream colours, and where the list names a source's kind (Start Playback's source list) the
   kind reads "Radio". Settings' Sources tab lists it among the sources added at run time, whose
   rows say when they were added and name no kind.
4. **Given** a station plays in the Kitchen, **When** the user selects the same station for the
   Kitchen in Start Playback, **Then** Play is disabled with "<station> is already playing in
   Kitchen", as for any source that already plays on the chosen target.
5. **Given** a station was started by another client, **When** the user views it in the app,
   **Then** scenarios 1 to 4 hold in the same way.

---

### User Story 3 - Play a recent station again (Priority: P2)

Most days I listen to the same few stations. When I open the radio screen I see the stations I
listened to recently from this phone, above the list, and one tap chooses one of them. I do not
search again. A station I only tried for a moment, or one that fell silent right after starting,
is not among them.

**Why this priority**: The hub forgets a station once it stops, so without this every replay costs
a search. Replaying a known station is the most frequent use of radio.

**Independent Test**: Start two different stations from the phone, let each play for more than 30
seconds, stop them, and open the radio screen: both are listed under "Recent", the later one first;
choosing one and pressing Play plays it. Start a third and stop it after a few seconds: it is not
listed.

**Acceptance Scenarios**:

1. **Given** the user started a station from this phone and it has played for at least 30
   seconds, **When** they next open the radio screen with an empty search field, **Then** that
   station is first under "Recent", shown like any station row.
2. **Given** the user started a station, **When** it stops within 30 seconds of starting, for any
   reason (the user stopped it, started something else in its place, another client stopped it, or
   the stream ended by itself), **Then** it is not added to "Recent".
3. **Given** a station start failed, **When** the radio screen is opened again, **Then** that
   attempt did not add the station to "Recent".
4. **Given** the user started a station and left the app before 30 seconds had passed, **When**
   they return to the app and the station still plays, **Then** it is added to "Recent"; **and
   When** it no longer plays on their return, **Then** it is not added.
5. **Given** a station was moved to another room within its first 30 seconds, **When** it goes on
   playing there, **Then** the move does not restart the count.
6. **Given** ten stations are under "Recent", **When** an eleventh qualifies, **Then** it is
   first and the oldest is gone.
7. **Given** a station under "Recent" is started again and plays for at least 30 seconds, **When**
   the list is shown next, **Then** it appears once, in first place; started again and stopped
   sooner, it keeps its place.
8. **Given** the user has typed into the search field, **When** results are shown, **Then**
   "Recent" is not shown; it returns when the field is emptied. **Given** the field is empty and a
   country, tag or language is chosen, **Then** "Recent" is shown in full, including stations
   that do not match those filters.
9. **Given** recent stations exist, **When** the app is closed and opened again, or the phone
   restarts, **Then** they are still listed.
10. **Given** a station under "Recent" no longer exists in the catalogue, **When** the user plays
    it, **Then** the start fails with the usual message and the station stays under "Recent".
11. **Given** radio is offered and recent stations exist, **When** the user opens Start Playback,
    **Then** the three most recent are listed directly under the "Find a radio station" row, most
    recent first, each shown like a station row; with fewer than three, those there are; with
    none, only the row.
12. **Given** recent stations are listed in Start Playback, **When** the user taps one, **Then**
    it becomes the selected source without the radio screen opening, and Play starts it like any
    chosen station.
13. **Given** the user chose, on the radio screen, a station that is one of the three listed in
    Start Playback, **When** Start Playback shows the selection, **Then** that listed row is the
    selected one and the station is not shown twice.
14. **Given** the hub does not offer radio, **When** the user opens Start Playback, **Then** no
    recent station is listed there, whatever the phone remembers.
15. **Given** the radio screen shows "Recent", **When** the user uses a row's remove action,
    **Then** that station leaves "Recent" at once, there and in Start Playback, without a
    confirmation, and the next most recent station takes a freed place among the three in Start
    Playback.
16. **Given** a station was removed from "Recent", **When** the user later starts it again and it
    plays for at least 30 seconds, **Then** it is in "Recent" again, in first place.
17. **Given** the removed station is the one currently selected in Start Playback or currently
    playing, **When** it is removed, **Then** the selection and the playback are unaffected.

---

### User Story 4 - Narrow the list by country, tag and language (Priority: P2)

I do not always know a station's name. I want to browse: the popular stations of my country, or
jazz stations, or stations in a language I speak. Chips above the list let me choose a country, a
tag and a language, alone or together; a control beside the list's heading sets the order; and the
list follows.

**Why this priority**: Browsing is how users discover stations they cannot name. Search by name
(story 1) already makes the feature usable, so this refines it.

**Independent Test**: Open the radio screen, choose the country Germany and the tag "jazz": the
list shows German jazz stations, most voted first; removing the tag shows all German stations
again.

**Acceptance Scenarios**:

1. **Given** the radio screen is opened for the first time on this phone, **When** the phone's
   region is a country the catalogue lists, **Then** that country is already chosen and the list
   shows its most voted stations; **and When** the region is unknown or not listed, **Then** no
   filter is chosen and the list shows the most voted stations of all.
2. **Given** the radio screen opens with an empty search field, **When** the list loads, **Then**
   it is never blank: it shows the stations for the current filters in the current order.
3. **Given** the Country chip, **When** the user taps it, **Then** a sheet lists every country
   with its number of stations, largest first, with a field that narrows the list as they type;
   choosing one closes the sheet and applies it. Country names are shown in English, as the
   catalogue names them.
4. **Given** the Language chip, **When** the user taps it, **Then** a sheet lists the catalogue's
   languages with their station counts, with a field that narrows the list; choosing one applies
   it.
5. **Given** the Tag chip, **When** the user taps it, **Then** a sheet lists the most used tags
   with their station counts and a field that finds tags by part of their name; more tags load as
   the user scrolls; choosing one applies it.
6. **Given** the order control beside the list's heading, which names the current order, **When**
   the user opens it and picks "Most voted", "Trending" (most plays in the
   last 24 hours) or "Name A–Z", **Then** the list is reloaded in that order; "Most voted" is the
   order before any choice is made.
7. **Given** a filter is chosen, **When** the user looks at its chip, **Then** it shows the chosen
   value and a way to remove it with one tap; removing it reloads the list.
8. **Given** several filters and a typed name, **When** the list loads, **Then** it shows only
   stations matching all of them.
9. **Given** the user chose filters and an order, **When** they open the radio screen later, also
   after restarting the app, **Then** the same filters and order are in place. The typed name is
   not kept.

---

### User Story 5 - Radio is missing, slow or failing, and the app says so plainly (Priority: P3)

My hub may not have the radio extension, the catalogue on the internet may be down, and a station
may refuse to start. In each case the app behaves sensibly: it does not offer what cannot work, it
tells me in plain words what happened, and it lets me try again.

**Why this priority**: The catalogue is a third party on the internet and the extension is
optional, so these cases are normal, though less frequent than the stories above.

**Independent Test**: On a hub without the radio extension, Start Playback has no "Find a radio
station" row and works as before. On a hub with it, cut the hub's internet access and open the
radio screen: a plain message and a Retry appear instead of stations.

**Acceptance Scenarios**:

1. **Given** the hub does not have the radio extension, or reports it as not active, **When** the
   user opens Start Playback, **Then** there is no "Find a radio station" row, no error, and
   everything else works as before this feature.
2. **Given** the app does not yet know whether radio is active, **When** Start Playback opens,
   **Then** the row is absent until the hub has answered; it is never shown disabled.
3. **Given** the radio screen is loading its first stations, **When** the user looks at it,
   **Then** it shows that it is loading.
4. **Given** no station matches the name and filters, **When** the list has loaded, **Then** the
   screen says "No stations match" and, when a country, tag or language is chosen, offers to
   clear the filters.
5. **Given** the catalogue does not answer or answers with something unreadable, **When** a list
   is requested, **Then** the screen says in plain words that the station catalogue isn't
   answering and offers Retry; stations already listed stay visible.
6. **Given** the hub itself cannot be reached, **When** the radio screen is open, **Then** the
   app's usual "hub unreachable" state applies, not a catalogue message.
7. **Given** the user has scrolled to the 10,000th station of one search, **When** the list ends
   there, **Then** it says that only the first 10,000 are shown and that narrowing the search
   shows others.
8. **Given** the radio extension stops being available while the radio screen is open, **When**
   the next request shows it, **Then** the user is told radio is no longer available on this hub
   and the screen closes.
9. **Given** a station was chosen and Play pressed, **When** the hub cannot start it, **Then**
   Start Playback stays open with the station still selected and says "Couldn't start <station>",
   where link failures are shown today.
10. **Given** a station start takes several seconds, **When** the user waits, **Then** the Play
    button shows the same "starting" state as for a pasted link, for up to the same limit.

---

### Edge Cases

- **A station without a name**: the row reads "Unnamed station"; it can still be chosen and played.
- **A station without country, codec or bitrate**, or whose codec the catalogue labels as unknown:
  the second line shows only the known parts; with none known there is no second line.
- **A very long station name**: one line ending in "…" in every list (the radio screen, "Recent",
  unselected rows in Start Playback). The station selected in Start Playback scrolls its name as a
  marquee, under the rules long names already follow on the Rooms card and Now Playing (MOB-007):
  it stands still when the name fits, when the app is not in front and when animations are off.
  On the Rooms card and Now Playing a station's name scrolls as any long name does.
- **A long station name on the Play button**: only the name is cut with "…"; "Play" and the target
  ("in Bedroom") always show.
- **A long country, language or tag name in a filter sheet**: one line ending in "…"; its station
  count stays in place.
- **Opening a filter sheet with a value already chosen**: the sheet opens with that row in view.
- **Text the hub would refuse**: the search field accepts at most 100 characters, and a `%` typed
  into it is not sent. The user never sees an "invalid parameter" error for something they typed.
- **Only spaces typed**: counts as an empty search field.
- **Typing fast**: only the latest text decides what is listed; an answer for earlier text never
  replaces a later one.
- **The same station on two pages**: shown once.
- **Leaving the radio screen while a list is loading**: nothing happens later; no message appears
  on another screen.
- **Back from the radio screen without choosing**: Start Playback is as it was, including a
  station chosen earlier.
- **Rotation or the app going to the background** on the radio screen: the typed name, filters
  and loaded stations are still there on return.
- **A failed start on a busy room**: the hub has already stopped what played there (existing hub
  behaviour for any failed start); the app's existing handling of that applies unchanged.
- **A filter sheet cannot load its list** (countries, languages or tags) while stations load fine:
  the sheet shows the catalogue message with Retry; the station list is unaffected.
- **A remembered filter value the catalogue no longer lists**: it is still applied and shown on
  its chip; the user can remove it.
- **A room or group turned off, a route limit, or any other refusal on start**: the app's existing
  messages for those refusals apply to stations as to any source.
- **Radio becomes active while the app is open** (the extension was just installed): the row
  appears the next time Start Playback is opened.
- **A playing station and "Move to room…"**: works as for any playback the hub marks transferable.
- **Trying several stations in a row**: each replaces the one before within seconds, so none of
  the sampled ones enters "Recent"; only the one left playing does.
- **Two stations started from the phone in different rooms**: each is judged by its own playback.
- **Two phones**: each keeps its own "Recent" and its own filters.
- **Switching to another hub**: "Recent" and the filters stay, since stations belong to the
  catalogue and not to a hub; a hub without radio simply offers no radio row.

## Requirements *(mandatory)*

### Functional Requirements

**Availability**

- **FR-001**: The app MUST offer radio only while the hub reports its radio extension as active,
  and MUST work exactly as before this feature on a hub without it.
- **FR-002**: Start Playback MUST show a "Find a radio station" row, between the link field and
  the source list, whenever radio is offered, for each of its three ways in. The row MUST be absent,
  not disabled, when radio is not offered or not yet known to be.

**Finding stations**

- **FR-003**: The radio screen MUST list stations for the typed name, the chosen filters and the
  chosen order, and MUST show a list without the user typing anything.
- **FR-004**: Typing in the search field MUST update the list by itself after a short pause, and
  an answer for earlier text MUST NOT replace the list for later text.
- **FR-005**: The search field MUST accept at most 100 characters, treat blank text as empty, and
  MUST NOT send text the hub refuses (`%`).
- **FR-006**: The user MUST be able to narrow the list by one country, one tag and one language,
  each chosen from the catalogue's own list with its station count, in any combination with each
  other and with a typed name.
- **FR-007**: The country and language lists MUST be narrowable by typing; the tag list MUST be
  searchable by part of a tag's name and MUST load further tags on scrolling.
- **FR-008**: Country, language and tag names MUST be shown as the catalogue gives them, which for
  countries and languages is English, the language of the rest of the app. The phone's language
  MUST NOT change them.
- **FR-009**: The user MUST be able to order the list by most voted (the default), by most plays
  in the last 24 hours ("Trending") and by name.
- **FR-010**: Each chosen filter MUST be visible with its value and removable with one action. The
  three filters MUST fit the width of a phone without scrolling when their values are short; a
  long value MUST be cut with "…" rather than push another filter out of sight, and the order
  control MUST always be visible beside the list's heading.
- **FR-011**: On the first visit on a phone the country filter MUST be preset to the phone's
  region when the catalogue lists that country, and to nothing otherwise. Afterwards the screen
  MUST open with the filters and order last used on that phone; the typed name is not kept.
- **FR-012**: The list MUST load further stations as the user nears its end, MUST never show one
  station twice, and MUST stop at the catalogue's limit of 10,000 stations per search with a note
  saying so.
- **FR-013**: A station row MUST show the station's name ("Unnamed station" when it has none) on
  one line ending in "…" when it does not fit, and a second line made of the known parts of country, codec and bitrate. Unknown parts
  MUST be left out, never shown as placeholders.
- **FR-014**: Station rows MUST NOT show votes, play counts, images or a mark for the stream
  technology.

**Choosing and starting**

- **FR-015**: Choosing a station MUST return to Start Playback with the station as the selected
  source and MUST NOT start anything by itself.
- **FR-016**: In Start Playback a selected station MUST behave as any selected source: the target
  choice, the "will stop" note, the Play button's wording and the refusal messages apply to it
  unchanged. Selecting a configured source or typing a link MUST deselect it.
- **FR-016a**: A selected station whose name does not fit its line in Start Playback MUST scroll
  the name as a marquee, following the existing marquee rules (MOB-007); every other station row
  there MUST show one line ending in "…". The Play button MUST always show the target, cutting
  only the station's name when the text does not fit.
- **FR-017**: Play MUST be disabled with "<station> is already playing in <target>" when that
  station already plays on the chosen target, whichever client started it.
- **FR-018**: After a successful start the app MUST show the playback's Now Playing, as after
  starting a pasted link.
- **FR-019**: When a station cannot be started, Start Playback MUST stay open with the station
  selected and say "Couldn't start <station>". The app MUST NOT state a cause the hub did not give.
- **FR-020**: While a start is pending the Play button MUST show the same starting state, for up
  to the same time limit, as for a pasted link.

**Recent stations**

- **FR-021**: The app MUST keep, on the phone, the last ten stations that were started from this
  phone and then played for at least 30 seconds, most recent first, each once, with what a station
  row shows. A station that failed to start, or stopped playing for any reason before 30 seconds
  had passed, MUST NOT be added and MUST NOT change the order.
- **FR-021a**: The 30 seconds count from the moment the station started playing and are judged by
  what the hub reports: the station qualifies once the app sees that playback still going at least
  30 seconds after its start. Moving it to another room does not restart the count. When the app
  was not in front at that moment, it judges on its return, and only if the same playback still
  goes on.
- **FR-022**: The radio screen MUST show them under "Recent" above the list while the search
  field is empty, whatever filters are chosen and without applying those filters to them, and
  MUST NOT show them while a name is typed.
- **FR-022a**: Start Playback MUST list the three most recent of them directly under the "Find a
  radio station" row whenever radio is offered, selectable in place like sources, and MUST NOT
  list any when radio is not offered. A selected station that is among them MUST be shown once.
  A station shown in that section that also plays at the moment, and is therefore a source on the
  hub, MUST NOT be listed a second time in the source list; a playing station not shown in the
  section stays in the source list.
- **FR-022b**: When radio stops being offered while a station is selected in Start Playback, the
  station MUST be deselected, so that nothing is selected that the screen no longer shows.
- **FR-023**: A recent station MUST be selectable like any listed station, without the catalogue
  being asked about it first.
- **FR-024**: Recent stations MUST survive closing the app and restarting the phone.
- **FR-024a**: On the radio screen each "Recent" row MUST offer an action that removes that
  station from "Recent" at once, without a confirmation, in both places it is shown. Start
  Playback's own rows MUST NOT offer removal. Removal MUST NOT affect a selection or a playback,
  and MUST NOT prevent the station from qualifying again later. The feature offers no other
  editing of "Recent": no reordering, pinning or renaming.

**How a playing station is shown**

- **FR-025**: A playing station MUST be treated as a live stream on every screen: the status
  "Live stream" on its Rooms card, and on Now Playing Stop without Pause and the line "Live
  streams can't be paused".
- **FR-026**: Wherever a source's kind is shown, a station's kind MUST read "Radio", with the
  stream colours, and MUST NOT be followed by the station's technical address.
- **FR-027**: FR-025 and FR-026 MUST hold for stations started by any client, not only this app.
- **FR-028**: Whether a source is a station MUST be decided in one place, together with the
  existing rules for a source's kind and for a live stream.

**When things go wrong**

- **FR-029**: While stations are loading the screen MUST show that; when nothing matches it MUST
  say "No stations match" and, when a country, tag or language is chosen, offer to clear the
  filters.
- **FR-030**: When the catalogue does not answer or answers unreadably, the screen (or the filter
  sheet that asked) MUST say so in plain words and offer Retry, keeping what is already listed.
- **FR-031**: A slow catalogue MUST NOT be reported as an unreachable hub: the app MUST wait for
  the hub's own answer for at least as long as the hub may take to give it.
- **FR-032**: When the hub cannot be reached, the app's existing connection state MUST apply.
- **FR-033**: When radio stops being available while the radio screen is open, the app MUST tell
  the user and close the screen, and MUST stop offering radio until the hub reports it active
  again.
- **FR-034**: No radio message MUST appear after the user has left the screen that asked.

**Scope**

- **FR-035**: The feature MUST NOT add favourites, voting, station images, a Radio tab, filters by
  codec or bitrate, or a lookup of single stations.

### Key Entities

- **Station**: a radio station from the catalogue. Has a stable identity, the address the hub
  plays it by, and, where the catalogue knows them, a name, a country, languages, tags, a codec
  and a bitrate.
- **Station search**: what the user asked for: a typed name, at most one country, one tag and one
  language, and an order. Yields stations page by page, at most 10,000.
- **Country, language, tag**: the catalogue's values for narrowing a search, each with the number
  of stations it has. A country also has a two-letter code, which the station rows show and by
  which the phone's region is matched on the first visit.
- **Recent station**: a station started from this phone that then played for at least 30 seconds,
  kept on the phone with what its row shows. At most ten, most recent first.
- **Radio filters**: the country, tag, language and order last used on this phone, kept on the
  phone.
- **Radio availability**: whether the hub's radio extension is active, as the hub reports it.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A user who knows a station's name can have it playing in a room within 30 seconds of
  opening Start Playback, without leaving the app.
- **SC-002**: One of the three stations listened to most recently from the phone can be started
  again with two taps from Start Playback (choose the station, Play), and any of the ten with
  three (open radio, choose the station, Play), plus the room choice when none is preset.
- **SC-003**: After the user stops typing, matching stations are shown within 2 seconds in normal
  conditions (home network up, catalogue answering).
- **SC-004**: The radio screen is never blank on opening: it shows stations, a loading state or a
  plain message with a way forward.
- **SC-005**: On a hub without the radio extension, no screen shows any trace of radio and no
  behaviour of the app differs from the previous version.
- **SC-006**: Every playing station, however it was started, is shown as "Radio" and as a live
  stream; no screen shows a station's technical address.
- **SC-007**: In no case does the user see a raw error, a status code or an "invalid parameter"
  message caused by what they typed or chose.
- **SC-008**: When the catalogue is down, the user learns it within the time the hub takes to
  answer (about 5 seconds) and can retry with one tap.
- **SC-009**: Recent stations and the chosen filters are still in place after the app is closed
  and reopened.

## Assumptions

- **Design**: the screens were drawn on the canvas after this spec and before the plan
  (Constitution VI; offline copies `design/screens/Radio.dc.html`, `RadioFilterSheet.dc.html`,
  `StartPlaybackRadio.dc.html`), following the accepted UX proposal in the backlog item: a row in
  Start Playback, a search-first radio screen with filter chips that open bottom sheets, and
  station rows styled like source rows with the stream colours.
- **Departures from the canvas** (Constitution VI), both decided on 2026-11-16:
  - The canvas draws a failed station start as a line above Play (`StartPlaybackRadio.dc.html`,
    property `startFailed`). The app shows it where link failures are shown today (FR-019), so
    that a station and a link fail alike on one screen. The line is the backlog item
    [start-failure-above-play.md](../../docs/backlog/start-failure-above-play.md), for every
    start failure together.
  - The canvas shows "Clear filters" whenever no station matches. The app offers it only when a
    country, tag or language is chosen, since otherwise it would clear nothing.
- **Wording** given in quotes above ("Find a radio station", "Recent", "No stations match",
  "Couldn't start <station>", and so on) is the intended wording; the design pass may refine it.
- **The pause before a typed name is searched** is about half a second.
- **"Recent" and the remembered filters belong to the phone**, not to a hub address: stations are
  the catalogue's, the same on every hub.
- **The 30 seconds before a station counts as "Recent"** (owner's request, 2026-11-16: "only if
  the user continues to listen for some time, maybe 5 seconds, maybe more") is the value chosen
  here. It is long enough to leave out a stream that the hub drops shortly after starting and a
  station the user only sampled while browsing, and short enough that a station left playing
  always counts. Listening is judged by the playback going on, since the app cannot know whether
  anyone hears it.
- **A station whose first 30 seconds end while the app is closed, and which has stopped before the
  app is opened again**, is not added: the app only knows what the hub reports while it is open.
- **"Recent" holds what the phone knew** when the station was started (name and second line). It
  is not refreshed from the catalogue.
- **The first-visit country** comes from the phone's region setting. No location permission is
  asked for or used.
- **Names in the filter lists are not translated**: the app's own text is English only, so country
  names in the phone's language would mix two languages on one screen. Translating them belongs
  with translating the app.
- **A station's kind "Radio"** replaces "Link" for stations only. Other sources added at run time
  keep the kind they have today.
- **Station images** are left out of this feature: they would be the app's first requests to
  servers other than the hub.
- **The hub tells no reason for a failed station start** (hub backlog item
  `play-resolution-failure-reason.md`); if it later does, showing the reason is a separate change.
- **Whether the hub refuses a kind of stream (HLS)** is not known to the app, so such stations are
  listed like any other and fail like any other if refused.
- **Favourites** are out of scope; the radio extension's own backlog leans towards favourites kept
  by the hub and shared by every client.
- **Dependencies**: a hub at API 0.1.34 or later with the radio extension 0.2.0 or later for the
  radio stories; `api/openapi.json` already carries the extension's requests (refreshed
  2026-11-15). Story 2 needs the extension only to have a station playing.
- **The full app test** gains rows for this feature before its pull request, as the constitution
  requires.
