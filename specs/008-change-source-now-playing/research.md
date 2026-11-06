# Research: Change What Plays from Now Playing

**Feature**: [spec.md](spec.md) | **Date**: 2026-11-06

No new library, hub call or platform API is involved. The questions are where the existing code
has to bend: the navigation key, Start Playback's view model, the "already playing" rule, Now
Playing's action row, and one side effect of keeping Now Playing under Start Playback. Facts below
were read from the current code on branch `008-change-source-now-playing` (app 0.7.0-alpha, API
0.1.33).

## R1. How Start Playback learns its fixed target

**Decision**: Extend the existing destination:
`Destination.StartPlayback(targetId: String?, fixedTarget: Target? = null, fixedName: String? = null)`.
`fixedTarget` is a `Target.Room` or `Target.Group` taken from Now Playing's `content.target.target`
(the route's addressed target, `TargetLine.target`), never from `TargetLine.rooms`. `fixedName` is
the name Now Playing shows. `targetId` stays null when `fixedTarget` is set.

Saved form (`encodeDestination`): `change:room:<id>` / `change:group:<id>`. The name is not encoded
(as `NowPlaying.targetName` is not, 003 research R10); the view model keeps the last known name in
its `SavedStateHandle` (R3), so a restored screen still names a target that has meanwhile been
removed. Old saved strings (`play`, `play:<id>`) decode as before.

**Rationale**: every place that asks "is Start Playback on top" (`AppNavigation.onBack` detaching
the starter, the `onStarted` guard) keeps working unchanged. A separate `Destination.ChangeSource`
would duplicate those checks and the entry wiring for one flag. The `change:` prefix keeps the old
`play:` decoding untouched; ids after `change:room:` are taken whole (`removePrefix`), so an id
containing `:` still round-trips.

**Alternatives considered**: a new destination type (more wiring, same screen); passing the target
through a session-scoped holder like `SettingsNavigator` (does not survive process death by
itself, and a second Start Playback could read a stale value).

## R2. Back stack after a start from Now Playing (FR-008)

**Decision**: New `AppBackStack.replaceTopTwo(destination)`: removes the top two entries and pushes
`destination`. `onStarted` calls it when the top is a `StartPlayback` with `fixedTarget != null`
**and** the entry below it is a `NowPlaying`; otherwise it calls `replaceTop` as today. The rule is
one function, `AppBackStack.showStarted(started: Destination.NowPlaying)`, so it is unit-tested in
`AppBackStackTest` instead of living in a lambda.

**Rationale**: the stack is plain state owned by the app (`AppBackStack`), already tested. Popping
the old Now Playing entry also clears its view model (entry-scoped `ViewModelStore`), so it stops
collecting. The "entry below is Now Playing" guard keeps a restored or unusual stack (Start
Playback directly on Rooms with a fixed target) from losing Rooms: `replaceTopTwo` never removes
the root.

**Alternatives considered**: popping the old Now Playing when Change source is tapped (then Close
could not return to it, FR-009); keeping it and letting it leave on "ended" (it would flash and post
a misleading "Playback on Kitchen ended", R6).

## R3. Start Playback's view model in fixed mode (FR-003, FR-004, FR-005, FR-011)

**Decision**: `StartPlaybackViewModel` takes `fixedTarget: Target?` and `fixedName: String?` beside
`initialTargetId`. When `fixedTarget` is set:
- `selectedTarget` is always `fixedTarget`; `rebuild` does not prune it and does not apply the
  room preselection; `onSelectTarget` does nothing.
- Each snapshot computes `fixed: FixedTargetCard` with the pure `FixedTarget.of(snapshot, target,
  knownName)` (data-model.md). The name it returns is saved under `FIXED_NAME_KEY`; the key is
  seeded from `fixedName` on first creation and read back after process death.
- `derive` uses `fixed.name` as the target name (Play label, `StartNames`), and treats the target
  as ready only when `fixed.unusable == null`. When unusable, no consequence is computed (the
  design shows only the note, not a "will stop" line).
- No source and no link are preselected (FR-003): nothing new, since only the room preselection
  existed.

**Rationale**: the screen, its link field, source list, footer and the starter stay one code
path; only the target part differs. Keeping the target out of `content.targets` matching is what
lets a removed or turned-off target stay on screen (FR-005) instead of being pruned.

**Alternatives considered**: a second view model for "Change source" (duplicates link, source and
start handling); filtering `content.targets` to one element (loses removed targets and puts the
radio-button tile on screen, which the design replaces with a plain card).

## R4. "Already playing" disables Play everywhere (FR-007, User Story 3)

**Decision**: In `derive`, `playEnabled = ready && !starting && connection == Live &&
consequence?.line !is ConsequenceLine.AlreadyPlaying`. `StartConsequence.of` is unchanged: it
already decides `AlreadyPlaying` by `route.inputId == source && route.target == target`, i.e. on
the **addressed** target, which is exactly acceptance scenario 4 (a source playing on a group is
not "already playing" in one of its rooms). Links never produce `AlreadyPlaying` (edge case).

**Rationale**: one rule, one place; applies to Start Playback opened from Rooms too, as the spec
requires. Play's `clickable(enabled = false)` already exposes the disabled state to TalkBack, and
the line sits directly above Play in the footer (scenario 3).

**Note**: today's full app test row S6 records that Play on an already-playing selection "opens
the same playback" (the hub answered with the existing route), while the spec's background says
the hub refuses it (`INPUT_ALREADY_ON_OUTPUT`, mapped to `StartFailure.AlreadyThere`). Either
answer is moot once Play is disabled; S6 is rewritten to "Play disabled". The `AlreadyThere`
mapping stays for a race (the source started there by another client between refresh and tap).

**Alternatives considered**: dropping the start in `onPlay` and showing a message (Play would look
tappable but do nothing); sending it and relying on the hub's answer (a refusal or a silent no-op,
neither of which the screen's line promised).

## R5. The fixed target card's wording (FR-012)

**Decision**: The card's second line is `fixedTargetStatusText(card)`:
- a group always reads `Group · …`: `Group · Playing Radio Paradise`, `Group · Paused Morning
  playlist`, `Group · Kitchen + Living room` (idle, playable members, as the picker tile),
  `Group · Turned off`, `Group · Not connected`, `Group · No rooms`, `Group · Removed`;
- a room reads as its picker tile does (`Playing · KissFM`, `Paused · …`, `In Downstairs`, `Idle`,
  `Turned off`, `Not connected`), and `Removed` when it is gone.

To say "Playing" or "Paused" for a group, `TargetStatus.GroupPlaying` gains `status: CardStatus`
(of its first non-announcement route, a live stream reading "Playing" as for rooms). The picker
tile's wording (`Group · Radio Paradise`, `targetStatusText`) does not change.

The FR-005 note above Play (`fixedTargetNoteText`):

| Why | Note |
|---|---|
| room or group turned off | `Kitchen is turned off` |
| every member of the group turned off | `All rooms in Downstairs are turned off` (the wording already used for `ALL_MEMBERS_DISABLED`) |
| room not connected; group with no member that is on and connected | `Kitchen is not connected` / `No room in Downstairs is connected` |
| group without members | `Downstairs has no rooms` |
| not in the snapshot any more | `Downstairs no longer exists` |

**Rationale**: the design and spec give `Group · Playing Radio Paradise` and `Downstairs is turned
off`; the rest follows the same pattern and the wording Start Playback and the refusal messages
already use. Saying "Playing" for a paused group playback would break Principle II, hence the
status in `GroupPlaying`.

**Alternatives considered**: reusing `targetStatusText` unchanged (`Group · Radio Paradise` does
not say it plays, and the room/group distinction the spec asks for is missing for an off group).

## R6. The old Now Playing must not announce "ended" while covered (FR-013)

**Finding** (found while planning; the spec adopted it as FR-013): `NowPlayingViewModel` collects `session.state` for as long as its entry lives, also
while Start Playback covers it. When a change of source replaces the old route, the first snapshot
without it reaches the covered view model, which calls `end()` and posts "Playback on Kitchen
ended" to `AppMessages`. That message would appear on Rooms later, after a successful change,
which is wrong. The same happens today in no other flow, because nothing else is pushed on top of
Now Playing.

**Decision**: Now Playing decides "gone" only while it is visible. `onVisible`/`onHidden` already
exist (the screen calls them under `repeatOnLifecycle(STARTED)`); the view model records a
`visible` flag and `onGone` returns early while hidden. The snapshots it skipped are not
replayed (`session.state` is a StateFlow), and `acquire()` restarts the loop only when it was the
last holder: on Close, Now Playing may acquire before Start Playback releases, so the loop keeps
its ~2.5 s rhythm and the screen would stay stale, with live controls, until the next tick.
`onVisible` therefore also calls `session.requestRefresh()`, so the first fresh snapshot decides
at once. So:
- while Start Playback covers it, nothing is announced, whatever ended the playback (this start,
  another client, the end of a track), as FR-013 requires;
- after a successful change the old entry is removed by R2 before it is ever visible again, and
  posts nothing;
- after a failed start, Close returns to the old Now Playing, the next refresh finds the playback
  gone, and it leaves with "Playback on Kitchen ended" exactly as today (spec edge case);
- the app in the background is unchanged in effect: on return the next refresh decides.

**Rationale**: smallest change that keeps today's behaviour visible to the user; test-first in
`NowPlayingViewModelTest` (hidden → no message; visible again → message once).

**Alternatives considered**: having Start Playback tell the old Now Playing to stay quiet (couples
two view models); letting `replaceTopTwo` clear `AppMessages` (would drop unrelated messages).

## R7. Now Playing's action row (FR-001, FR-002, FR-012)

**Decision**:
- "Change source…" is a `RoundAction` with a new `SonoraIcons.Swap`
  (`M4 8h14M15 5l3 3-3 3M20 16H6M9 13l-3 3 3 3`, the canvas path), surface background, text tint,
  spoken label "Change source", placed after Stop and Pause/Resume and before "Move to room…".
- Visible for every playback Now Playing shows (playing, paused, failed, pauseable or not),
  except one addressed to a `Target.Unknown` (a target type from a newer hub), which cannot be a
  Start Playback target: there it is hidden (FR-002), as "Move to room…" is. The rule goes where
  `moveVisible` is decided: `NowPlayingContent.Playback.changeSourceVisible =
  route.target !is Target.Unknown` in `NowPlayingBuilder`. Enabled when `controlsEnabled` and no
  Stop or Move is in flight. The row layout is one pure function (`actionRowLayout(content)` in
  `NowPlayingText.kt` or a new `ActionRow.kt`), tested.
- Layout: with three buttons or fewer the row keeps 76 dp buttons, 28 dp icons, captions on one
  line, gap 24 dp (the canvas changed 40 → 24 so Stop, Change source…, Move to room… fit a 360 dp
  phone: 76 + ~88 + ~80 + 2 × 24 ≈ 292 dp of 320). With four (Stop, Pause, Change source…, Move to
  room…: `pauseVisible && moveVisible`) the row fills the content width with four equal
  `weight(1f)` columns, 4 dp apart, 64 dp buttons with 24 dp icons, captions centred and allowed
  to wrap to two lines.
- Tapping it calls `onChangeSource`, which navigation turns into
  `push(StartPlayback(null, target, name))`. The view model is not involved: the action changes
  nothing on the hub (FR-001).

**Rationale**: matches `NowPlaying.dc.html` / `NowPlayingFile.dc.html`. Touch targets stay ≥ 64 dp.

**Alternatives considered**: an overflow menu or a text button under the row (rejected in
clarification: a round button).

## R8. Mix and announcement sources, failures, a start in flight

**Decision**: nothing new. `StartConsequence` already words "plays alongside" / "will be lowered";
the stack is still replaced (R2) because the guard is "opened from Now Playing", not the join mode.
A failure is reported by the existing `startFailureMessage`; Close and system Back detach the
starter as today (003 research R9), so a start still in flight reports to Rooms.

## R9. Testing

| Suite | New or changed tests |
|---|---|
| `AppBackStackTest` | `showStarted` with a fixed Start Playback over Now Playing replaces both (Back reaches Rooms); without a fixed target replaces only the top; fixed target directly over Rooms keeps Rooms; `encode`/`decode` round-trip `change:room:<id>`, `change:group:<id>`, an id containing `:`; old `play`, `play:<id>` unchanged |
| `FixedTargetTest` (new, domain) | room idle / playing / paused / in a group / off / not connected / removed; group playing (status from its route) / paused / idle members / off / all members off / none connected / no rooms / removed; the name comes from the snapshot, else the known name, else the id |
| `StartPlaybackBuilderTest` | `GroupPlaying.status` (Playing, Paused, live stream → Playing) |
| `StartPlaybackViewModelTest` | fixed mode: target selected from the start and never changed by `onSelectTarget`; no source preselected; Play disabled with the note while the target is off, enabled again when it is turned on; a removed target keeps its last name after a restore (`SavedStateHandle`); already playing disables Play in fixed **and** picker mode, another source or a link re-enables it; a group route on a room is not "already playing" for that room |
| `StartPlaybackTextTest` | `fixedTargetStatusText` and `fixedTargetNoteText` for every row of R5 |
| `NowPlayingViewModelTest` | gone while hidden posts nothing and does not exit; becoming visible requests a refresh, and that snapshot then exits with "Playback on … ended" once |
| `NowPlayingBuilderTest` | `changeSourceVisible`: true for room and group playbacks (playing, paused, failed), false for an unknown target type |
| `NowPlayingTextTest` (or `ActionRowTest`) | `actionRowLayout`: counts and compact flag for live stream, pauseable playing, paused, not movable, unknown target (Stop only) |

All in `commonTest`, test-first (Principle IV). Compose UI tests stay optional; the layout at
360 dp, the card and TalkBack are checked on the device (quickstart §2).
