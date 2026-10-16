# Use the hub's newer route data: refusal reasons and the rooms a playback is on

- **Status:** Specified as
  [006-route-outputs-refusals](../../specs/006-route-outputs-refusals/spec.md) on 2026-10-17.
  Written 2026-10-16 after checking the hub's changes since API 0.1.21.
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
   plays on. Seen on the Pixel 5 on 2026-10-17 (`0.5.0-alpha+33`, hub 0.1.25) with KissFM on
   Living Room and Office turned off, while the hub reported `outputs: ["kitchen"]`:
   - Rooms: the card reads "Office Speaker + Kitchen Speakers", "2 of 4 rooms in use", and Office
     is not listed as "Off".
   - Now Playing: "on Living Room · Office Speaker + Kitchen Speakers", with a volume pill for
     Office.
   - Move sheet: "Kitchen Speakers only, Office Speaker stops", though Office is not playing. The
     rows that read the on/off state are right ("Office Speaker only, Turned off"; the Broadcast row
     notes "Office Speaker turned off").
   - Start Playback is not affected: its group tile leaves the member out and says "Won't play in
     Office Speaker (turned off)".

   Since hub 0.1.25 a room turned back on rejoins the playback under the same route id, so the
   wrong count lasts only while the room is off.
2. **New refusals get the generic message.** Starting or moving onto something turned off answers
   409 with `OUTPUT_DISABLED`, `GROUP_DISABLED`, `INPUT_DISABLED` or `ALL_MEMBERS_DISABLED`; a move
   can answer `ROUTE_CHANGING_OUTPUTS` for a moment after a room is turned back on.
   [Messages.kt](../../composeApp/src/commonMain/kotlin/sonora/multiroom/mobile/ui/Messages.kt) maps
   only `ROUTE_LIMIT_REACHED` and `INPUT_ALREADY_ON_OUTPUT`, so these read "Couldn't start
   playback" / "Couldn't move …". The app hides targets that are off, so the `*_DISABLED` reasons
   come only when another client changed them since the last refresh. `ROUTE_CHANGING_OUTPUTS` is
   more likely: turning a room back on in Settings and moving its group's playback right away.

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
