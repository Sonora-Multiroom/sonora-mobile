# Quickstart: validate Change What Plays from Now Playing

Feature: [spec.md](spec.md) · Expected results: [contracts/ui-behaviour.md](contracts/ui-behaviour.md)
· Model: [data-model.md](data-model.md)

## 1. Cloud / CI checks (no hub, no emulator)

Prerequisites: JDK 17+ and `ANDROID_HOME` set. `gradle.properties` shows
`sonora.versionName=0.8.0-alpha` and `sonora.versionCode=8`. `api/openapi.json` is 0.1.33.

```bash
./gradlew :androidApp:assembleDebug
./gradlew :composeApp:testAndroidHostTest
./gradlew :composeApp:allTests
./gradlew :composeApp:check
```

Expected: all green. The suites and what they prove are listed in
[research R9](research.md#r9-testing).

## 2. On-device checks (local session, real hub)

Follow AGENTS.md "On-device verification": the POCO (phone width, 360 dp class), volumes noted
and set to 1 before the first start, Do Not Disturb on, Sonora in the foreground before every
input, test routes stopped before volumes are restored. Use known-good streams (KissFM, Radio NV,
Kraina FM, KissFM 2.0 Deep). Rooms `A`, `B` and a group `G` containing `A` and `B`; a pauseable
source `F` (a file) if the hub has one. Say before row 1 that the run replaces what plays in `A`,
`B` and `G`.

| # | Setup (`curl` on `/api/v2`) | Action in the app | Expected |
|---|---|---|---|
| 1 | KissFM on `A` | Rooms → `A`'s card → Now Playing | Row: Stop · Change source… · Move to room…, 76 dp, nothing clipped |
| 2 | as 1 | Change source… | Title "Change source"; "Play in" shows one card `A` with "Playing · KissFM", no radio, no other target; no source selected; KissFM still audible (SC-005) |
| 3 | as 2 | pick Radio NV | "KissFM will stop in A" above Play; Play reads "Play Radio NV in A" |
| 4 | as 3 | Play | Radio NV plays in `A`; Now Playing for Radio NV within ~3 s; one Back → Rooms (SC-001, SC-004) |
| 5 | Radio NV on `A` | Now Playing → Change source… → Close; again → system Back | back on the same Now Playing both times; `GET /routes` unchanged (FR-009) |
| 6 | Radio NV on `A` | Change source… → pick Radio NV | "Radio NV is already playing in A", Play dimmed and does nothing; pick KissFM: Play enabled; paste `https://soundcloud.com/forss/flickermood`: Play enabled (US3) |
| 7 | Radio NV on `A` | Rooms → "Play in room" on `A`, pick Radio NV | same "already playing" line and disabled Play; the room picker is still shown (FR-004, FR-007) |
| 8 | Radio NV on `A` | Change source… → paste the SoundCloud link → Play | the link starts in `A`; Now Playing for it; Back → Rooms (US1 scenario 5) |
| 9 | Kraina FM on `G` | Now Playing for `G` → Change source… | card `G`, "Group · Playing Kraina FM"; pick KissFM: "Kraina FM will stop in G"; Play → KissFM on the whole group (`GET /routes`: target `G`, `outputs` = both) (US2, SC-002) |
| 10 | Kraina FM on `G`; `B` turned off (`PUT /outputs/B/enabled false`) | Now Playing (plays on `A` only) → Change source… | target is `G`, not `A`; "Won't play in B (turned off)" with a source picked. Turn `B` back on afterwards |
| 11 | KissFM on `A` | Change source… open; `PUT /outputs/A/enabled false` by `curl` | within ~3 s the card dims, "A is turned off" above Play, Play disabled; turn `A` on: Play enabled again (FR-005). Close |
| 12 | KissFM on `G` | Change source… open; `DELETE` KissFM's route by `curl` | the "will stop" line disappears on the next refresh; Play starts on the idle `G`; Back from the new Now Playing → Rooms |
| 13 | `F` playing on `A`, paused (`PUT /routes/{id}/pause`) | Now Playing | Stop · Resume · Change source…; Change source… → pick KissFM: "F will stop in A"; Play replaces the paused playback (`GET /routes`: `F` gone) (US4; checks the spec's assumption that the hub replaces a paused route) |
| 14 | `F` playing on `A` and movable | Now Playing | four equal columns, 64 dp buttons, "Change source…" and "Move to room…" may wrap, nothing clipped at the POCO's width (FR-012) |
| 15 | KissFM on `A` | Change source… → pick a stream known not to answer (DJ FM) or paste `http://10.255.255.1/stream.mp3` → Play | the existing failure message; `A` falls silent (hub stops first); Close → old Now Playing leaves with "Playback on A ended" on Rooms. No "ended" message appears on Rooms after rows 4, 8, 9 (research R6) |
| 16 | KissFM on `A` | Change source… open; the user cuts the phone's Wi-Fi, then Now Playing | Start Playback stale banner, Play disabled; back on Now Playing all buttons incl. Change source… disabled. Restore Wi-Fi (the user; adb may need re-pairing) |
| 17 | KissFM on `A` | Change source… open; `adb shell am kill sonora.multiroom.mobile` after Home; reopen | same fixed target card and title, no picker (FR-011) |
| 18 | as 1, TalkBack on (the user) | focus the new button; on Start Playback with "already playing", focus Play | "Change source, button"; Play announced disabled, the line above readable |

At the end: stop the test routes, restore volumes, turn `A` and `B` back on if still off, restore
screen timeout and Do Not Disturb.
