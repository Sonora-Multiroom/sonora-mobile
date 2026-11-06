# Change what plays from Now Playing

- **Status:** Specified as MOB-008, [specs/008-change-source-now-playing/spec.md](../../specs/008-change-source-now-playing/spec.md)
  (2026-11-06). Written 2026-11-06.
- **Origin:** The user: on Now Playing the only actions are Stop and "Move to room…". To swap the
  radio station or source in the room they are listening in, they have to stop it, go back to Rooms
  and start something new, or find the room's "Play" entry there.
- **Depends on:** Start Playback (MOB-003), which already opens for one preselected room or group
  (`Destination.StartPlayback(targetId)`, used by "Play in room" on Rooms) and already shows what
  will stop. No hub change, no new call. A design update for the Now Playing button first
  (Constitution VI).
- **Effort:** Small to Medium. One button and one navigation callback; the open questions below decide
  how much more (the back stack after the start, groups, paused routes).

## Problem

To play something else in a room, the user must leave Now Playing, stop the current playback (or
not), open Start Playback and pick the room again. The wish: a button on Now Playing that opens the
Start Playback screen with this playback's room (or group) already chosen. The music keeps playing
while the user browses; picking another source and confirming replaces it.

## Proposal

1. **A "Change source" action on Now Playing**, next to Stop and "Move to room…". Label candidates:
   "Change source…" (matches "Move to room…") or "Play something else…". It never stops anything
   by itself.
2. **It opens Start Playback with the target preselected**: the same screen "Play in room" opens
   from Rooms, with the target tile selected. The user picks another configured source or pastes a
   link, then taps Play.
3. **Almost nothing new on the Start Playback side** (only point 6). It already shows "<current source> will stop in
   <room>" for a busy target, so the user sees that the current playback will be replaced. The
   preselected target stays changeable (the user can still pick another room). The app sends only
   the start command and never stops the old route itself; the hub replaces it.
4. **Which target.** The playback's `targetId` and `targetType`, as addressed: a single output for a
   single-room route, the group for a group route. The route's `outputs` (what it plays on right
   now) is not the target: a group route playing on fewer rooms than its group has would otherwise
   change meaning. For a group route the group is preselected (decided 2026-11-06), and the "will
   stop" line names every room it affects.
5. **Availability.** The action shows for any live playback, paused or playing, like Stop does. It is
   not gated on `transferable` or `pauseable`.
6. **Same source picked.** Picking the source already playing on the target starts nothing: no
   request is sent and the playback goes on (decided 2026-11-06). Today Start Playback does not
   do that: it shows the line "<source> is already playing in <room>" (`StartConsequence`), keeps
   Play enabled, and a tap sends the request, which the hub refuses with 409 `INPUT_ALREADY_ON_OUTPUT`
   (message "... is already playing in ..."). The change: with the line showing, Play is disabled
   (or does nothing), so the refusal is never provoked. It applies to Start Playback in general,
   not only when coming from Now Playing, and is the one change on the Start Playback side.
7. **Back stack.** Start Playback replaces itself with Now Playing for the new route
   (`replaceTop`), which would leave the old Now Playing, now stale, one step below. When the old
   Now Playing is the entry directly below Start Playback, replace it too, so the stack is
   Rooms → Now Playing(new) and Back leads to Rooms (decided 2026-11-06).
8. **Failed start.** The hub stops the old playback first and then tries the new one (confirmed
   2026-11-06; AGENTS.md "Known hub behaviour"), so a station that is down leaves the room silent.
   The app cannot know beforehand whether a source works, so it neither warns nor offers to restore
   the old one (decided 2026-11-06); it shows the existing failure message. A hub-side fix (try the
   new one first, replace on success) belongs in the `multiroom-ai` backlog.
9. **Paused playback.** Replaced like any other: the "will stop" line reads the same, with no
   special wording for a paused playback (decided 2026-11-06). A start on the target removes it, and
   Resume is no longer possible. The quickstart checks on device that the hub does the same for a
   paused playback as for a playing one.
10. **Test:** a unit test for the navigation (the callback receives the route's target, not an
   output of `outputs`); the quickstart check on device: with a stream playing in a room, tap the
   action, see the room selected and the "will stop" line, pick another station, confirm Now Playing
   shows the new one. Repeat with the playback paused.

## Open questions

1. **Placement and label.** A third button in the action row, an overflow entry, or a row under the
   title. Needs the design pass; the label is decided there.

## Related

- [rooms-with-several-playbacks.md](rooms-with-several-playbacks.md): once a room can hold several
  playbacks, "change source" must say which playback it replaces (Proposal 4 grows).
- [playback-shortcuts.md](playback-shortcuts.md): a shortcut also starts a source on a target in one
  tap; this item is the interactive version.
