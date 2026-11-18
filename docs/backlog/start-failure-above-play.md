# Show a failed start as a line above Play

- **Status:** Proposed, not started. Written 2026-11-16.
- **Origin:** MOB-009 planning ([research R5](../../specs/009-radio-stations/research.md#r5-starting-a-station-fr-015-to-fr-020)).
  The radio artboard `design/screens/StartPlaybackRadio.dc.html` (property `startFailed`) draws
  "Couldn't start <station>" as a line above Play. The owner decided on 2026-11-16 that MOB-009
  keeps the snackbar, so that a station failure and a link failure look the same, and that the
  line is considered for every start failure together.
- **Depends on:** MOB-009 (the artboard and the "Couldn't start <station>" wording come from it).
  A decision on which failures move (see Open questions).
- **Effort:** Small. One field of Start Playback's view state, one line in the footer, the rule
  for when it goes, tests, and the full app test rows that read the notice.

## Problem

When a start fails, Start Playback stays open with the selection kept, and says why in a snackbar
that leaves after a few seconds (MOB-003). A user who looked away sees the same screen as before
Play and cannot tell that anything happened. A long source or station name may not fit the
notice. On-device checks have to capture frames in a loop to see it at all.

## Proposal

- Show the failure as a line above Play, in the warning colour with the info icon, in the place
  the "will stop" line uses, as the radio artboard draws it.
- The line stays until the user presses Play again or changes the source, station, link or
  target.
- Apply it to every start from Start Playback (configured source, link, station), so the screen
  has one way of showing a failed start.
- A start whose screen was closed keeps reporting to Rooms as today.

## Open questions

1. **Which failures move?** Those that keep the selection for another try (the hub could not play
   the link or station, the hub is unreachable) fit a line that stays. Those that change the
   lists ("<room> is turned off", "<source> is no longer on the hub") come with a refresh that may
   remove the selection the line refers to; they may be better left as notices.
2. **Together with "will stop"?** The artboard replaces the "will stop" line with the failure. A
   failed start on a busy room has already stopped what played there (hub behaviour), so after
   the next refresh there is nothing left to warn about; before it, both lines would compete.
3. **The artboard** `StartPlayback.dc.html` has no failure state; it needs one when this is
   specified.
