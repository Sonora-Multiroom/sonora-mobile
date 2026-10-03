# Research: Now Playing and "Move to room…"

Phase 0 of [plan.md](plan.md). Builds on feature 001 ([research](../001-rooms-screen-foundation/research.md));
versions, generator setup, HTTP client, error mapping and polling rules from 001 are unchanged
unless stated here.

## R1. One hub session shared by Rooms and Now Playing

**Decision**: Extract the address subscription, repository creation, poll loop and connection
state from `RoomsViewModel` into an app-wide `HubSession` (created once in `AppGraph`, its own
`CoroutineScope`). It exposes `state: StateFlow<SessionState>` (no address / connected with
`Connection`, last good `HubSnapshot`, `refreshSeq` of the last completed refresh), `repository`,
`requestRefresh()`, and reference-counted `acquire()/release()` polling: polling runs while at least
one visible screen holds it, an `acquire()` from 0 refreshes immediately, the release to 0 cancels
the loop and any refresh in flight. `RoomsViewModel` and `NowPlayingViewModel` both derive their
content from the session's snapshot.

**Rationale**:
- Now Playing opens with the last known snapshot already on screen (no "loading" flash) and the
  `acquire()` refresh confirms it within one round trip (SC-004).
- Only one poll loop exists, so refreshes can never overlap across screens (001 research R5
  carried over unchanged, now enforced in one place).
- Rooms after returning from Now Playing already has the newest snapshot (US1-4: the room is idle
  "after the next refresh").
- Each screen keeps the 001 lifecycle rule (poll only while `STARTED`, FR-003): the screen's
  `repeatOnLifecycle(STARTED)` block calls `acquire()` / `release()` instead of
  `startPolling()` / `stopPolling()`. Nav3 composes only the top entry, so at most one screen
  holds the session at a time. A brief 1 → 0 → 1 during navigation just restarts the loop with an
  immediate refresh.

**Alternatives considered**:
- *Second poll loop inside `NowPlayingViewModel`*: duplicates ~100 lines of subtle loop and
  connection code, and the two loops can overlap during navigation transitions.
- *Seeding Now Playing from the Rooms view model*: couples two screens' view models and still
  needs a second loop.

**Migration**: the existing `RoomsViewModel*Test` suites keep their scenarios. Loop and connection
scenarios move to `HubSessionTest`, and Rooms tests construct a `HubSession` on the test
scheduler. No scenario may be dropped.

## R2. A view model per Now Playing entry

**Decision**: Add `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-navigation3` **2.11.0**
(same version as the lifecycle libraries already in use, checked on Maven Central 2026-10-03) and
install `rememberViewModelStoreNavEntryDecorator()` in `NavDisplay`. `NowPlayingViewModel(routeId,
session, messages)` is then scoped to its back-stack entry: it survives rotation and is cleared
when the entry is popped.

**Rationale**: today `viewModel { }` in an entry uses the activity's store. All Now Playing
entries would share one instance, and none would ever be cleared. The decorator is the JetBrains
multiplatform counterpart of the AndroidX artifact (commonMain-safe, Constitution III/VII). It
comes from the same family and release train as `lifecycle-viewmodel-compose`.

**Alternatives considered**: `viewModel(key = routeId)` on the activity store leaks a view model
per opened playback and never clears it. A plain `remember`ed state holder loses in-flight
requests on rotation.

## R3. Following a playback across a move

**Decision**: `POST /api/v2/routes/{routeId}/transfer` returns `200 RouteResponse` for the **new**
route ("callers must update their references to the new routeId", `openapi.json` 0.1.20).
`HubRepository.transferRoute` returns the mapped `Route`, and `NowPlayingViewModel` switches its
`followedRouteId` to the new id (FR-002). While a move is in flight, and until a refresh that
**started after** the switch completes, a missing route is not treated as "ended". This is the
same refresh-sequence fence 001 uses for settled volumes (`finishedAtRefresh < seq`), so a
snapshot taken before the hub created the new route cannot send the user back to Rooms.

The back-stack destination keeps the original id. The followed id lives in the view model, which
survives rotation (R2). After process death the restored entry carries the old id, its first
refresh does not find it, and the app returns to Rooms with the "ended" message. This is
accepted: the result is truthful and nothing breaks. Replacing the destination would recreate the
entry and its view model mid-flow.

**Alternatives considered**: re-finding the playback by `inputId` after the move is ambiguous when
the same source plays twice, and the hub gives the id explicitly.

## R4. Leaving Now Playing and the "ended" message

**Decision**:
- Now Playing returns to Rooms when the followed route is absent from, or `STOPPED` in, a
  snapshot that passes the R3 fence. It posts **"Playback on <target> ended"** (FR-002), where
  `<target>` is the last known target name.
- **Stop tapped here**: on `Ok` the app returns at once without a message (US1-4, FR-009). On
  `Rejected(404)` it returns with the "ended" message, because the playback already ended
  elsewhere. On any other failure it stays and shows the 001 Stop error.
- A route id that is absent from the very first snapshot after opening counts as ended elsewhere.
- Cross-screen one-shot messages go through a small `AppMessages` holder in `AppGraph`
  (`post(text)`, `messages: Flow<String>`). Rooms collects it into the snackbar it already has,
  next to its own `message`. Now Playing has its own snackbar for errors that do not leave the
  screen (pause, volume, mute, move).
- Navigation: `NowPlayingViewModel.exit: StateFlow<Exit?>` (one-shot). The entry pops itself once
  it sees the exit. The view model never touches the back stack (keeps it testable).

**Alternatives considered**: passing the message as a navigation result. Nav3 has no result API,
and an app-level holder is simpler and testable.

## R5. Shared volume dragging with per-room pending values

**Decision**: Extract the drag / throttle / settle logic from `RoomsViewModel` (001 research R10:
`Drag`, `Settled`, `sendVolume`, `finishedAtRefresh`) into `VolumeDragController`. Both view models
own one, constructed with their `viewModelScope`, the session and an error callback. Its state is a
`StateFlow<Map<String, Int>>` of **pending volumes per room id** instead of one override per card:
- A drag is started with a key (pill id) and a base map of room id → volume. Group pill: all known
  members. Member pill or single-room pill: one room. Updates send
  `GroupVolume.scale(base, value)` for the changed rooms only, throttled to 250 ms, with the final
  value always last (unchanged from 001).
- What a pill shows is the pending value of its rooms when present, else the hub value. A group pill
  shows the **max** over its members (`scale` maps the loudest member to exactly the dragged value,
  so Rooms cards look exactly as in 001). Member pills follow a group drag, and the group pill
  follows a member drag (US2-3, US2-4).
- Pending values clear per room when a refresh that started after that room's final send succeeds,
  or when the room disappears (same rule as 001, keyed by room instead of card).
- `PUT /api/v2/groups/{id}/volume` remains absent from the repository (FR-015, SC-005).

**Rationale**: one implementation of the subtle throttle and reconcile rules (Constitution II "local
value wins while interacting"). Per-room values are what a group pill and its member pills need to
stay consistent with each other.

**Tests**: the 001 volume scenarios in `RoomsViewModelControlsTest` stay green against the extracted
controller. New `VolumeDragControllerTest` cases: a member drag changes only that room and the group
max; a group drag moves the member pills; a member drag right after a group release starts from the
settled group targets.

## R6. Now Playing content as a pure function

**Decision**: `NowPlayingBuilder.build(snapshot, routeId): NowPlayingContent` in `domain/`,
test-first. It reuses `RoomsBuilder`'s rules through shared internal helpers (status mapping,
target description, "occupies"), so Rooms and Now Playing cannot disagree about status, live, mute
or "not connected". Status wording is the existing `CardStatus` set. Now Playing shows the live
stream as a badge and a line, not as the chip text, so the builder returns `Playing` + `live = true`
for live streams (FR-006, FR-011).

Rules beyond 001:
- `pauseVisible = route.pauseable`. `pauseEnabled = status ∈ {Playing, Paused}` (FR-010).
- `moveVisible = route.transferable && status == Playing` (FR-012, never while Paused, Starting,
  Stopping, Unknown or Failed).
- Stop is always present and enabled except while its own request is in flight (FR-009).
- Volume section: single room → one pill + room mute. Group → group pill (max) + group mute + one
  pill per listed member in `GroupResponse.outputIds` order, skipping unknown ids (FR-015).
  `Target.Unknown` or a target missing from the hub → no pills and no mute button (spec edge case).
- `muteOn = masterMuted || target.muted` (hub's group `muted` = all members muted).
  `muteEnabled = !masterMuted`. While master mute is on, every pill is muted (not draggable) and
  the section notes "All rooms are muted" (FR-018). A member pill is muted when that room is muted.

## R7. Address detail

**Decision**: `addressDetail(kind: SourceKind, uri: String?): String?` next to `inferSourceKind` in
`domain/SourceKind.kt`, the only place it is derived (Constitution II, FR-005):
- Stream / Link → the host of an `http(s)` address (no port, no user-info). A non-http address on
  a Link → the address as typed.
- File → the last path segment of a `file:` URI or path (`/`, `\` separators, percent-decoding not
  attempted).
- Line-in → `null` (the line reads "Line-in").
- Anything unparseable or blank → the trimmed address as typed, or `null` when there is none.

Kind labels: Stream "Stream", Line-in "Line-in", File "File", Link "Link". The subtitle is
`"<Kind> · <detail>"`, or just `"<Kind>"` when the detail is null.

## R8. Move destinations as a pure function

**Decision**: `MoveDestinations.build(snapshot, routeId): MoveSheetContent?` in `domain/`, written
test-first against every FR-025 case. It returns structured notes, and one formatter
`destinationNoteText(note)` (tested) renders the copy. Both live in one place each (FR-021 "decided
in one place").

Occupancy: a map room id → the *other* live route occupying it (non-`STOPPED` routes, same rule as
Rooms, the current route excluded), with that route's source name and, for group routes, the group
name.

Room rows (FR-020–FR-022):
- Single-room route: every room except the current one.
- Group route: every room outside the group, plus each member as `"<Room> only"` with note
  `OthersStop(<other members joined>)`.
- Notes in order of precedence: disabled → `TurnedOff`; unavailable → `NotConnected`; occupied by a
  single-room route → `WillStop([source])`; occupied by a group route →
  `WillStopOnGroup(source, group)`; else `Idle`.
- **Plan-level reading** (not spelled out in the spec): a `"<Room> only"` member that is turned off
  or not connected shows that note instead of "… stop" and is unselectable, by the same precedence.
  It sorts with the unselectable rooms and keeps its "only" label.
- Ordering: selectable non-members, "only" options, unselectable. Each bucket is sorted by label,
  case-insensitively, with the id as tiebreaker.

Group rows (FR-020a):
- Every group except the current target (for a single-room route, all groups).
- Disabled → `TurnedOff`, unselectable. Otherwise selectable, with note
  `WillStop(distinct other sources occupying any member, in member order)` when any member is
  occupied by another route, else `Members(member names)`. In both cases
  `notConnected = names of unavailable members` is appended as " · X not connected" /
  " · X and Y not connected".
- Members that the current route occupies do not count as "will stop" (spec Assumptions: overlap
  keeps playing).
- **Plan-level reading**: a group whose listed members are all unknown to the hub (none resolve)
  reads "No rooms" and is unselectable, because the hub would have nothing to play on.
- Ordering: selectable alphabetical, then unselectable alphabetical. An empty list hides the
  section.

Selection lives in the view model (`selected: Target?`). The view model clears it when a refresh
makes that destination unselectable or removes it (spec edge case). The CTA is
`"Move to <name>"`, where name is the room name without " only", or the group name (FR-023).

Copy (formatter):

| Note | Text | Colour |
|---|---|---|
| `Idle` | Idle | textMuted |
| `WillStop([a])` / `([a, b])` | "a will stop" / "a and b will stop" | warning |
| `WillStopOnGroup(a, g)` | "a will stop on g" | warning |
| `OthersStop([x, y])` | "x and y stop" (3+: "x, y and z stop") | textMuted |
| `Members([x, y], notConnected=[])` | "x + y" | textMuted |
| any group note + notConnected | "… · p not connected" | base colour of the note |
| `TurnedOff` / `NotConnected` / `NoRooms` | "Turned off" / "Not connected" / "No rooms" | textMuted, row dimmed |

## R9. Mute endpoints

**Decision**: Add `setRoomMute(roomId, muted)` → `PUT /api/v2/outputs/{id}/mute {muted}` and
`setGroupMute(groupId, muted)` → `PUT /api/v2/groups/{id}/mute {muted}` (sets every member,
idempotent). The button shows the hub's state only: a tap sends `!shownState`, disables the button
while in flight (FR-013), and the next refresh is the confirmation. There is no optimistic toggle,
because the hub's group `muted` means "all members muted" (spec edge case), and a refresh shows that
truthfully. Disabled while master mute is on (FR-018).

## R10. Move request and errors

**Decision**: `transferRoute(routeId, target: Target)` → `POST /api/v2/routes/{id}/transfer
{targetId, targetType}` where `Target.Room` → `SINGLE_OUTPUT`, `Target.Group` → `OUTPUT_GROUP`
(`Target.Unknown` is never a destination). Response body decoded into `Route`. A 2xx body that
cannot be decoded → `Unexpected`.

On a failure the sheet closes and a message is shown (FR-024). New error copy, added to the single
`actionErrorMessage` function (the 001 contract table is extended in
[contracts/hub-repository.md](contracts/hub-repository.md)). 404 cannot tell a vanished playback
from a vanished destination, so it uses the general text. If the playback really is gone, the next
refresh takes the user back to Rooms (R4).

## R11. Bottom sheet

**Decision**: Material 3 `ModalBottomSheet` (already a dependency, `compose-material3` 1.9.0),
styled with theme tokens: container `surface`, scrim `rgba(5,6,8,0.72)`, top radius 28,
36×4 drag handle `#3A3E46`. Swipe-down, scrim tap and system Back dismiss it (US3-7). Content follows
`Transfer.dc.html`. The destination list scrolls inside the sheet once the Groups section makes it
taller than the design's 600 dp. The note and buttons stay pinned at the bottom. The list uses
`Modifier.selectableGroup()` and each row uses `selectable(role = Role.RadioButton)`, so TalkBack
announces a single-choice list (FR-027).

**Alternatives considered**: a custom sheet costs more code for gestures and accessibility, and
adds no dependency savings.

## R12. Design prerequisite (FR-026)

**Finding**: the canvas (`project/Transfer.dc.html`, checked 2026-10-03) and the offline copy have
no "Groups" section yet. The NowPlaying design shows only the stream variant and has no Pause or
"All rooms are muted" state, which the spec covers in its Assumptions.

**Decision**: updating the canvas and `design/screens/Transfer.dc.html` with a "Groups" section
(the room rows' style with a group icon and the notes from R8) is a **gate task before the sheet
UI**. It is done where the canvas can be edited, which is a local session. Domain logic, repository,
view models and the Now Playing screen do not wait for it. If an implementing session reaches the
sheet UI and the design is still not updated, it stops and asks (project workflow).

## R13. Version

The first implementation commit sets `sonora.versionName=0.2.0-alpha` and `sonora.versionCode=2`
in `gradle.properties` (AGENTS.md workflow, spec Assumptions).
