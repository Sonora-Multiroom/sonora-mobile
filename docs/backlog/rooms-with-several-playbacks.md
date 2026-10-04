# Rooms with several playbacks: main playback as the card, the others nested under it

**Status:** Proposed, not started. Written 2026-10-04 while specifying `003-start-playback`.
Intended as the next feature after 003 (`004-…`).
**Origin:** The hub's `023-multi-route-output-mixing` (API 0.1.21, deployed 2026-10-02) lets one
output play several routes at once: `MIX` routes play alongside, and `DUCK_OTHERS` routes
(announcements, e.g. the TTS extension) lower everything else while they play. The app was built
on the rule "an output plays at most one route" (AGENTS.md, "Domain rules"), which no longer holds.
[003's spec](../../specs/003-start-playback/spec.md) (Assumptions) defers this work here.
**Depends on:** `api/openapi.json` at 0.1.21 (refreshed 2026-10-04). Probably a hub API addition
(see Open question 1), which belongs in the `multiroom-ai` backlog.
**Effort:** Medium. Domain mapping, Rooms and Now Playing UI, the move sheet's notes, a design
update first, and tests.

## Problem

When a room carries more than one route, the app shows it wrongly:

- **Rooms** shows one card per route, so the room appears on two or more cards. Each card's volume
  pill sets the same room's volume, because output volume applies to everything playing there.
  An announcement appears as a full card for a few seconds and then disappears, so the list jumps.
- **"N of M rooms in use"** is right only by accident, because occupancy is a set of rooms.
- **The move sheet** (feature 002) maps each room to a single route. It names only one of several
  playbacks as "will stop", and it names an announcement as stopping although a `REPLACE` leaves
  ducking routes playing.
- **Nothing says the music is lowered** while an announcement plays, so it can look as if the
  radio broke.

Hiding the extra routes, as Home Assistant does with its single "source" per output, would break
Constitution II (Truthful UI). The hub's 023 spec also notes that a route nobody can see or stop is
a support problem.

## Proposal

**One card per main playback, the others nested in it.**

```
┌──────────────────────────────────────────┐
│ [▣]  Kitchen                        (■)  │
│      Lounge FM                           │
│      Live stream · Lowered               │  ← main route; "Lowered" while ducked
│   ┌────────────────────────────────────┐ │
│   │ ◔ Announcement · TTS          (■)  │ │  ← ducking route, compact row
│   │ ♫ Mixed in · Doorbell chime   (■)  │ │  ← mix route, compact row
│   └────────────────────────────────────┘ │
│ [🔊━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ 40%]  │  ← one pill: the room's volume
└──────────────────────────────────────────┘
```

- **The card stays per main route**: the hub's `main` (the first non-ducking route to join the
  output, else the first route). With one route per room, Rooms looks exactly as today.
- **Every other route is a compact row** inside the card of the rooms it shares: kind icon, a tag
  ("Announcement" for `DUCK_OTHERS`, "Mixed in" for `MIX`), the source name and its own Stop.
  Rows have no volume pill: the hub has no per-route volume, and the card's pill is the room's.
- **"Lowered"** is appended to the main route's status line while its `duckState` is `LOWERED`
  (an unknown `duckState` reads as full, as the hub asks).
- **Now Playing** gets an "Also playing here" section with the same rows, and the "Lowered" state.
- **The move sheet** names every non-announcement playback that a move would stop, and never an
  announcement.
- **AGENTS.md** replaces the "at most one route" domain rule with the 023 rules.
- The design canvas (Main, Now Playing) is updated first, per Constitution VI.

## Open questions

1. **Data cost.** `main` and `duckState` exist only in `GET /api/v2/outputs/{id}/routes`, one call
   per output on top of the current ones at every 2.5 s refresh. They cannot be derived from
   `GET /routes`: the join order changes when a route is moved and is not reported. Options: the
   hub addition proposed in `multiroom-ai/docs/backlog/routes-per-output-in-outputs-list.md`
   (each output in `GET /outputs` lists its routes with `main` and `duckState`); or, until it ships,
   per-output calls only for outputs with more than one route, using the oldest `createdAt` as
   "main" otherwise.
2. **Routes that cross cards.** A route mixed into one room of a group's playback: its row says where
   it plays ("in Kitchen"). A secondary route on a group whose rooms have different main routes:
   shown under each of those cards, or under the first one only?
3. **A room whose only routes are announcements**: its own card with the announcement as main (the
   hub's rule), or an Idle row with an "Announcement" note?
4. **Stop on Rooms cards.** The card's Stop ends the main route only. Should a card also offer
   "stop everything here" (`DELETE /api/v2/outputs/{id}/routes`, which stops ducking routes too)?
5. **Start Playback in mix or announcement mode.** 003 follows each source's default join mode and
   offers no choice. Is a choice on the screen ("Play alongside", "Announce") wanted, and with what
   design?
