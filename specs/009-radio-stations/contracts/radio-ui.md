# UI Contract: Find and Play Radio Stations

**Feature**: [spec.md](spec.md) · Design: `design/screens/Radio.dc.html`,
`RadioFilterSheet.dc.html`, `StartPlaybackRadio.dc.html` (sizes and colours there are binding;
this file states behaviour and wording) · Model: [data-model.md](../data-model.md)

Colours and type come from theme tokens. The stream colours are `kindStream` (tile `#3A2A12`,
icon `#F2A541`). New icons in `SonoraIcons`: `Search`, `ChevronDown`, `Sort`; the row chevron,
close, back, info and stream icons exist.

## A station row (every list)

| Part | Contract |
|---|---|
| Size | min height 54 dp, radius 14, tile 38 dp (radius 10) in the stream colours with the stream icon |
| Name | 15 sp semibold, **one line ending in "…"**; "Unnamed station" when the station has no name |
| Second line | 12 sp `textMuted`: country code · codec · "<n> kbps", only the known parts; no line when none is known. In Start Playback the line starts with "Radio" ("Radio · DE · MP3 · 128 kbps", or "Radio" alone) |
| Never shown | votes, play counts, images, an HLS mark, the station's address |
| Spoken | "<name>, <second line>" as one node |

## Start Playback

Unchanged unless listed. The section exists only while radio is offered; while the app does not
know yet, and on a hub without radio, the screen is exactly as before this feature.

| Part | Contract |
|---|---|
| Section | Heading "Or a radio station" between the link field and "Or pick a source" |
| "Find a radio station" row | `surface` background, a `surfaceRaised` tile with the search icon, title "Find a radio station", second line "Search by name, country, tag or language", a chevron. Opens the radio screen. Role button |
| Recent rows | Up to three, most recent first, directly under that row; transparent background, a radio dot at the end; selectable like sources (one radio group with the sources). No remove action here |
| Selected station that is one of them | That row is selected (`selected` background and outline, filled dot). The "Find a radio station" row stays. Tapping the selected row changes nothing |
| Selected station that is not one of them | The "Find a radio station" row is replaced by the station's row in the selected style, with "Change" (13 sp semibold, `accent`) at the end in place of a dot. Tapping it opens the radio screen. Spoken: "<name>, selected. Choose another station" |
| Selected station's name | Scrolls as a marquee when it does not fit (`MarqueeText`: one line, still when it fits, when the hub is unreachable, the window is not focused or animations are off) |
| Exclusivity | Choosing a source or typing a link deselects the station; choosing a station deselects the source and empties the link field |
| "Will stop", muted and "won't play" notes | As for any source |
| Already playing | "<station> is already playing in <target>", Play disabled, when that station plays on the chosen target, whichever client started it |
| Play button | "Play <station> in <target>" in three parts: "Play " and " in <target>" never shrink, the name shrinks and ends in "…". The same for source names. "Play link in <target>" and "Starting…" unchanged |
| Starting | "Starting…", selections locked, as for a link; up to 30 s |
| Station could not be started | The screen stays, the station and the target stay selected, and the snackbar says "Couldn't start <station>", as link failures are shown. No cause is given. The canvas's `startFailed` state (a line above Play) is not built: see [research R5](../research.md#r5-starting-a-station-fr-015-to-fr-020) |
| Other failures of a station start | The existing snackbar and wording ("<room> is turned off", "<room> can't play more at once", "<target> is no longer on the hub", "Couldn't reach the hub") |
| Radio went away (reported by the radio screen) | Snackbar "Radio is no longer available on this hub"; the section disappears; a selected station is deselected |
| The screen is restored with a station selected | Until the hub has said whether it offers radio, the screen is as with no station selected: no radio section, Play reads "Play" and is disabled (unless a source or link is chosen meanwhile, which deselects the station), no "will stop" or "already playing" line. Then the station shows selected again, or is deselected if radio is not offered |
| Radio stops being offered for any other reason (a readable answer of the hub without an active radio extension, another hub address; never a single failed request while radio was offered) | The section disappears and a selected station is deselected, without a message |
| A station of the section that also plays | It is left out of "Or pick a source", so it shows once. A source that is selected is never left out |
| Restored by the system | The same station selected |

## The radio screen

A pushed screen over Start Playback, no bottom bar.

| Part | Contract |
|---|---|
| Header | Back button (spoken "Back"), title "Radio stations" (Sora 20 sp) |
| Search field | 52 dp, radius 16, `surface`; search icon; placeholder and spoken label "Station name"; at most 100 characters; a typed `%` does not appear. With text: a clear button (44 dp, spoken "Clear the name"). The keyboard's action is Search and only hides the keyboard: the list follows the text by itself, 0.5 s after the last change |
| Chips | One row, 8 dp apart, in this order: Country, Tag, Language. Not chosen: `surface` pill, 44 dp, the word and a down chevron; opens its sheet. Chosen: `selected` background and outline, the value (14 sp semibold, at most 120 dp wide, "…"), and a remove button (40 × 44 dp, `accent` ✕, spoken "Remove the country Ukraine" / "Remove the tag jazz" / "Remove the language german"); tapping the value opens the sheet. The row scrolls sideways when it does not fit |
| "Recent" | Heading "RECENT", then up to ten station rows, each with a remove button at the end (44 dp, `textMuted` ✕, spoken "Remove <name> from Recent"). Shown while the search field is empty or blank and there is at least one, whatever the filters, the list's state or the catalogue's. Not filtered. Removal is immediate, without confirmation |
| List heading | "STATIONS IN <COUNTRY>" with a country chosen, else "STATIONS"; one line, "…". At its right the order control: the current order's name and the sort icon, 44 dp high, spoken "Order: Most voted. Change the order"; opens the Order sheet. Always visible while the list is |
| List | Station rows; tapping one chooses it and closes the screen. Nothing starts playing |
| Loading the first page | Six placeholder rows in place of the list and its heading; spoken "Loading stations". The chips and "Recent" stay |
| Loading the next page | "Loading more stations…" under the last row; starts when the list is within 10 rows of its end |
| End at the limit | "Showing the first 10,000 stations. Narrow the search to see others." under the last row |
| Nothing matches | "No stations match" (Sora 17 sp), "Try another name or fewer filters.", and a "Clear filters" button when a country, tag or language is chosen. It removes those three and keeps the order and the name |
| Catalogue not answering | A `surface` card above the list (role alert): info icon, "The station catalogue isn't answering" in `warningText`, "Retry" in `accent`. Rows already listed stay under it |
| Hub unreachable | The app's stale banner; rows stay and can be chosen; no catalogue card. The failed request is repeated by itself when the hub answers again |
| Radio went away | The screen closes; Start Playback says so (above) |
| Back | Returns to Start Playback unchanged |
| Rotation, background and return | Name, filters, list and scroll position kept (the list's scroll state is saved with the screen) |

### Order of the parts in the scrolling area

Catalogue card (if any) → "Recent" (if shown) → list heading and rows, or the placeholders, or
"No stations match" → "Loading more stations…" or the limit note.

## Filter sheets

A modal bottom sheet in the style of the Move to room sheet (`surface`, radius 28 at the top, a
handle, the scrim).

| Part | Contract |
|---|---|
| Title | "Country", "Tag", "Language" or "Order" (Sora 22 sp) |
| Field (not for Order) | 48 dp, radius 14, `surfaceRaised`, search icon; placeholder and spoken label "Search countries" / "Search tags" / "Search languages"; at most 100 characters |
| Rows | Min height 52 dp, radius 14: the name (15 sp semibold, one line, "…") and the station count at the right (13 sp, tabular digits, thousands grouped with a comma), which never moves or shrinks. The chosen one has the `selected` background and outline and its count in `accent`. Role radio button |
| Country | Every country, largest first. The field narrows the list on the phone. Names as the catalogue gives them, in English |
| Language | Every language, largest first; narrowed on the phone; names as given |
| Tag | The most used tags first, 50 at a time; "Loading more tags…" while the next page loads. The field asks the hub 0.5 s after the last change; a typed `%` or `/` does not appear. A chosen tag is the first row. Paging ends at the last page or at offset 10,000, without a note |
| Order | No field, no counts, three rows: "Most voted" with "The catalogue's all-time favourites", "Trending" with "Most plays in the last 24 hours", "Name A–Z" |
| Opening with a value chosen | That row is in view |
| Choosing a row | Closes the sheet, applies the value, stores it and reloads the list from its first page |
| Cancel | "Cancel" at the bottom (44 dp), the scrim and Back close the sheet with nothing changed |
| Cannot load | The catalogue card with Retry inside the sheet; the list behind is untouched |
| Hub unreachable while a sheet is open | No catalogue card; the sheet keeps what it lists (or its loading rows) and loads again by itself when the hub answers |
| Loading | Placeholder rows as on the radio screen |

## Rooms, Now Playing, Settings (a station that plays)

| Screen | Contract |
|---|---|
| Rooms card | The station's name (marquee when long, as today), status "Radio · Live stream", the stream tile, Stop only |
| Now Playing | Title: the station's name; subtitle "Radio" with nothing after it; the stream panel; Stop and no Pause; "Live streams can't be paused"; "Change source…" and "Move to room…" by the existing rules |
| Start Playback's source list | A playing station is a source on the hub while it plays: it is listed with kind "Radio" and the stream tile, and is selectable as any source, unless the radio section above already shows that station |
| Settings → Sources | In "Added at run time": the stream tile and the added line, with the remove action as for any runtime source. The row names no kind in words |

All four hold for a station started by any client.

## Accessibility

- Every icon-only control has the spoken label given above and a 44 dp target (the chip's remove
  button is 40 × 44 dp inside a 44 dp chip, as drawn).
- The search fields are labelled; the placeholder colour (`#7C808A`) is placeholder text and is
  exempt from the contrast rule as inactive content; typed text is `text`.
- Recent rows in Start Playback and filter rows carry radio semantics and their selected state.
- Loading placeholders, "Loading more…" and the catalogue card are announced (status / alert).
- New text and background pairs are added to `ContrastTest`: `textSoft` on `surface` (chips, the
  order control), `accent` on `selectedBg` ("Change", the chip's ✕, a chosen row's count),
  `warningText` on `surface` (the catalogue card), `textMuted` on `surface` (sheet counts).
