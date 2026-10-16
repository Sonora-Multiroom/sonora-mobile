# Use the hub's newer route data: refusal reasons and the rooms a playback is on

- **Status:** Proposed, not started. Written 2026-10-16 after checking the hub's changes since API
  0.1.21.
- **Origin:** The hub's 024 (API 0.1.22) added `RouteResponse.outputs` and new 409 refusal reasons;
  027 (API 0.1.25) makes turning a room back on put it back into live group playback.
- **Depends on:** `api/openapi.json` at 0.1.25 (refreshed 2026-10-16). No hub change. Touches the
  same mapping as [Rooms with several playbacks](rooms-with-several-playbacks.md); do it first or
  together with that.
- **Effort:** Small to medium. Domain mapping, error copy, tests.

## Problem

1. **Group playback is counted on every member.** The app derives the rooms of a group route from
   the group's members. A member that is turned off has left that playback, yet Rooms, Now Playing
   and the move sheet still count it. `RouteResponse.outputs` now lists the outputs a route really
   plays on.
2. **New refusals get the generic message.** Starting or moving onto something turned off answers
   409 with `OUTPUT_DISABLED`, `GROUP_DISABLED`, `INPUT_DISABLED` or `ALL_MEMBERS_DISABLED`; a move
   can answer `ROUTE_CHANGING_OUTPUTS` for a moment after a room is turned back on.
   [Messages.kt](../../composeApp/src/commonMain/kotlin/sonora/multiroom/mobile/ui/Messages.kt) maps
   only `ROUTE_LIMIT_REACHED` and `INPUT_ALREADY_ON_OUTPUT`, so these read "Couldn't start
   playback" / "Couldn't move …". The app hides targets that are off, so this happens only when
   another client changed them since the last refresh.

## Proposal

- Map `RouteResponse.outputs` into the domain `Route`; use it for "what plays on output X" and for
  a group card's rooms, falling back to the group's members when it is absent.
- Add `StartFailure`/move cases: "<room> is turned off", "<group> is turned off", "<source> is
  turned off", "All rooms in <group> are turned off", each followed by a refresh. Retry
  `ROUTE_CHANGING_OUTPUTS` once after a short delay before showing an error.
- Update AGENTS.md ("Hub gaps", "Domain rules") once shipped.

## Open questions

1. Should a group card whose playback runs on only some members say so ("Playing in 2 of 3
   rooms")? Needs a design decision.
