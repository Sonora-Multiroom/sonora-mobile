# Adopt HUB-034: a playback's own volume next to the room's volume

- **Status:** Proposed, not started. Written 2026-10-24; UX proposal added 2026-10-30.
- **Origin:** HUB-034 (`specs/034-per-route-volume-ducking`, `multiroom-api` 0.1.29, on the Pi since
  2026-10-24).
- **Depends on:** `api/openapi.json` refreshed at 0.1.29 or later. The nested rows (case C below) depend
  on [rooms-with-several-playbacks.md](rooms-with-several-playbacks.md).
- **Effort:** Medium. Contract refresh, domain mapping, one new hub call, Rooms and Now Playing UI, a
  design update first, and tests. Case C adds little once the nested rows exist.

## Problem

Until HUB-034, volume belonged only to a room (output): one number per room, applied to everything playing
there, kept after playback ends. Since HUB-034 a playback (route) may carry its own volume, `routeVolume`:

- It is **absolute**: the playback sounds as if the room's volume were `routeVolume`, on every room it
  plays on, whatever those rooms are set to. Changing the room's volume does not reach it; the room's
  mute and master mute still silence it.
- It is set when the playback starts (by the request or the source's `defaultRouteVolume`) or later by
  `PUT /api/v2/routes/{id}/volume`, and it **cannot be cleared**: once set, the playback never follows
  the room's volume again.
- It works for any playback, music included. Its first users are announcements and alerts (Home
  Assistant's air-raid siren at 100 by day and 50 at night, TTS with `tts.playback.route-volume`), but
  any client may start music with one or set one later. The contract's only caution is that the room's
  volume control no longer reaches such a playback.
- `RouteResponse` reports it, so the app's existing `GET /api/v2/routes` poll already carries it.

What goes wrong in the app today:

- **The pill lies.** A siren playing in Kitchen at route volume 100, with Kitchen at 30, shows a
  Kitchen card whose pill reads 30 %. Dragging that pill changes nothing you can hear. Constitution II
  (Truthful UI) forbids this.
- **Nothing in the app can change such a playback's volume**, only stop it.
- The contract copy predates HUB-034 (API 0.1.28). The original step "send `outputVolume` where Start
  Playback sent `volume`" has nothing to change: the app never sent `volume` (see
  `KtorHubRepository.playLink`, "No displayName, volume or joinMode"). The refreshed contract only has
  to keep the generated client compiling; the app sends neither `volume` nor `outputVolume`.

## Proposal

**The pill you see always controls what you hear.** It holds on Rooms and Now Playing alike, for every
pill that belongs to a playback:

- `routeVolume` null: the pill shows and sets the room's volume (`outputVolume`), as today.
- `routeVolume` set: the pill shows and sets the playback's own volume (`routeVolume`).

No pill is shown that doesn't change what the playback sounds like. The app never gives a playback its
own volume by itself (case D).

### Case A: the playback follows the room (today's normal, `routeVolume` null)

No change. The card's pill and Now Playing's pills are room volumes. Group pills keep the
loudest-member and scaling rules (AGENTS.md, "Hub gaps").

### Case B: the card's playback has its own volume

```
┌──────────────────────────────────────────┐
│ [▣]  Kitchen                        (■)  │
│      Air-raid siren                      │
│      Own volume                          │  ← status line tag
│ [🔊━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ 100%] │  ← this playback's volume, not Kitchen's
└──────────────────────────────────────────┘
```

- **Rooms card:** the pill shows and sets the playback's `routeVolume` through
  `PUT /api/v2/routes/{id}/volume`, throttled like the room pill. The status line gets
  "Own volume", after "Live stream" or "Paused" ("Live stream · Own volume"). The pill's content
  description names the source ("Air-raid siren volume") instead of the room. The mute button still
  mutes the room, which also silences this playback, so it stays as it is.
- **Group playbacks:** one value for every room, so one pill with no loudest-member scaling.
- **Now Playing:**

  ```
  [🔊 Air-raid siren ━━━━━━━━━━━━━━━━ 100%] (🔇)
  Plays at its own volume, whatever the rooms are set to.
  ```

  The one pill is the playback's volume, for a single room and a group alike. A group's member pills
  are hidden: they are room volumes, which this playback doesn't follow. The room's volume can still be
  set in Now Playing of a playback that follows it, or in the next playback.
- **No room or group name on the playback's pill** (decided 2026-11-04). The pill is labelled only by
  its accessible description ("Air-raid siren volume"). The mute button keeps its room or group name,
  since it mutes that room or group. The "on Kitchen" line under the source name stays: it says where
  the playback plays, not whose volume the pill sets. Group playbacks with their own volume get the
  same single pill, with the same label rule.

### Case C: another playback in the room has its own volume (nested rows)

Needs the nested rows of [rooms-with-several-playbacks.md](rooms-with-several-playbacks.md).

```
│   ┌────────────────────────────────────┐ │
│   │ ◔ Announcement · TTS   80%    (■)  │ │  ← own volume shown in the row
│   │ ♫ Mixed in · Rain      40%    (■)  │ │
│   └────────────────────────────────────┘ │
│ [🔊━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ 30%]  │  ← card pill: the room, for the main playback
```

- A row with its own volume shows the percentage. A row without one shows none (it follows the room
  pill).
- **Rooms card:** rows show the percentage only. Their volume is set in Now Playing, so the card
  stays one pill high and announcements don't make it jump.
- **Now Playing, "Also playing here":** a row with its own volume has a compact pill that sets it,
  per the rule. This is the hub's story 3: "turn just that route down from the mobile app". A row
  without one has no pill: its pill would be the room's, which the main pill already is.

### Case D: giving a playback its own volume

The app never sets `routeVolume` without the user asking, because it cannot be undone while the
playback lasts. After that, the room pill no longer reaches the playback: the user turns Kitchen down
and that playback stays as loud.

- **No "own volume" action in this item**, for the main playback or a mixed-in one. Under the rule a
  pill never switches by itself from the room's volume to the playback's. An explicit action for it
  (Now Playing, with a confirmation) was considered on 2026-11-03 and not built: for a single playback
  the room pill does the job, and balancing two playbacks in one room is rare.
- **Why this stays out:** a route volume cannot be cleared on the hub (HUB-034, API 0.1.32). Once the app
  sets one, the playback's pill is its own for good, so case D is one-way until the hub can clear it.
  Only ship it together with a hub request for "follow room volume again".
- **Start Playback:** sends no volume, as today. A volume choice belongs with an "Announce" mode, if
  that is ever designed (rooms-with-several-playbacks, Open question 5).

### What stays out

- **Ducking level** (`duckingLevelDb`) is fixed when a playback starts and is a tuning value in dB.
  The app shows nothing for it beyond the planned "Lowered" status.
- **`outputVolume` on `play`**: the app does not send it (see Problem).

### Steps

1. Refresh `api/openapi.json` (`./gradlew refreshOpenApi`) and note the hub version in AGENTS.md
   ("API").
2. Map `RouteResponse.routeVolume` into the domain playback. One function, used by Rooms and Now
   Playing, decides what a playback's pill shows and sets: its own volume when `routeVolume` is set,
   else the room's (the loudest member for a group).
3. Add `setPlaybackVolume(routeId, volume)` to `HubRepository` (`PUT /api/v2/routes/{id}/volume`).
   `VolumeDragController` gets a second sink for it.
4. Rooms card and Now Playing for case B. Case C goes with the nested rows.
5. Update the design canvas (Main, Now Playing) first, per Constitution VI, and AGENTS.md: the "Agreed
   details" on the pill and a domain rule for `routeVolume`.

To test case B without Home Assistant, start a route with its own volume using `curl`
(`POST /api/v2/routes` with `routeVolume`) on a room set to 1. Note that the playback then plays at
the volume given, not at the room's 1, so give a low one.

## Open questions

2. **Case B on Rooms for a group.** Should the card name the source in the pill ("Siren 100 %") for
   groups too, or is the status-line tag enough?
