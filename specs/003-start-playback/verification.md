# Verification: Start Playback

Results of the on-device checks in [quickstart.md](quickstart.md) §2, to be run by the user against
the real hub. §1 (build and tests) passes in the cloud session that implemented the feature.

**Build under test**: `0.3.0-alpha`, CI build 21, commit `96022e6` (Settings footer "Sonora 0.3.0 - Alpha - Build N (sha)").
Device: Pixel 5, driven over adb; hub state read with `curl` against `/api/v2`. All outputs at volume 1 during the run.

Status: ✅ passed · ❌ failed · ⏳ not checked yet · n/a not checkable on device

Rows 5 and 13 check hub assumptions. If either fails, record it here and ask before changing
behaviour (consequence line for row 5, timeout match for row 13).

| # | Check | Status | Date | Notes |
|---|---|---|---|---|
| 1 | Rooms → "Play something": nothing selected, Play reads "Play" and is disabled | ✅ | 2026-10-04 |  |
| 2 | Idle room → "Play something in <room>": that room is selected | ✅ | 2026-10-04 | Bedroom Speakers preselected |
| 3 | Stream + idle room → Play: Now Playing within ~3 s; Back → Rooms shows it | ✅ | 2026-10-04 | KissFM → Bedroom: Now Playing slid in at 1.1 s, "Playing" by 2.0 s. A first try with Lounge FM stayed STARTING on the hub and was then dropped by the hub (stream problem, not the app) |
| 4 | A different source on that busy room: "<first> will stop in <room>" shown before Play; only it stops | ✅ | 2026-10-04 | Kraina FM on Bedroom while KissFM played: "KissFM will stop in Bedroom Speakers"; Office playback untouched |
| 5 | ⚑ hub assumption: with a group playing, select one of its rooms: warning names the group, and after Play the whole group playback stops | ✅ | 2026-10-04 | Assumption holds. KissFM 2.0 Deep on Living Room, Radio NV → Kitchen: "KissFM 2.0 Deep will stop in Living Room"; after Play the whole group route ended, Office included |
| 6 | The source already playing on exactly that target: "<source> is already playing in <target>"; Play opens Now Playing for the same playback | ✅ | 2026-10-04 | Radio NV on Kitchen: "Radio NV is already playing in Kitchen Speakers"; Play opened the same route (same `startedAt`, not restarted) |
| 7 | Paste `soundcloud.com/<artist>/<track>` → target → Play: "Starting…" while the hub resolves, then Now Playing | ✅ | 2026-10-04 | `soundcloud.com/forss/flickermood` → Bedroom: "Starting…" at 0.6 s, Now Playing with "Playing" by ~2.5 s |
| 8 | A link the hub cannot reach or a non-media page: screen stays, link and target kept, message per FR-016 | ✅ | 2026-10-04 | `https://example.com/` → Office: "The hub couldn't play this link"; link and Office kept |
| 9 | Type `hello world`: no message while typing; leave the field → "Enter a web address (https://…)"; Play disabled | ✅ | 2026-10-04 | Message after Done, not while typing; Play disabled |
| 10 | Turn a group member off, select the group: "Won't play in <room> (turned off)" | ✅ | 2026-10-04 | Office turned off, Living Room + a source: "Won't play in Office Speaker (turned off)". The note shows once a source is picked too |
| 11 | Cut the phone's Wi-Fi with the screen open: stale banner, selections kept, Play disabled; restore → Play re-enables | ✅ | 2026-10-04 | Run by the user |
| 12 | Play on a link and Close at once: Rooms; the playback appears, or a failure message shows there | ✅ | 2026-10-04 | Failure: `http://10.255.255.1/stream.mp3` (hub refuses after ~10 s), Close at once → Rooms showed Office "Starting…", then "The hub couldn't play this link". Success: SoundCloud link, system Back at once → stayed on Rooms, playback appeared there; a second Back left the app (Rooms not replaced, PR #7 review fix) |
| 13 | ⚑ hub assumption: link timeout recovery with a slow link. When the 30 s answer is lost but the hub plays it, Now Playing opens. Does the runtime input keep the exact address sent? | n/a | 2026-10-04 | No link that takes ~30 s to start was available, so the timeout recovery was not exercised. R8's risk checked on a fast link: the runtime input kept exactly the address sent (`https://soundcloud.com/forss/flickermood`) |

## Open points

- A source that already plays as an announcement on or overlapping the chosen target: whether the
  hub returns that playback or refuses cannot be checked until a configured source declares
  `DUCK_OTHERS` as its default. Record it here when one does.

## Issues found

1. **Rooms has no "Play something" button when every room is busy** (feature 001). The button is
   drawn inside the Idle section ([RoomsScreen.kt](../../composeApp/src/commonMain/kotlin/sonora/multiroom/mobile/ui/rooms/RoomsScreen.kt)),
   but the design places it below that section. With all rooms playing there is no way into Start
   Playback from Rooms.
   **Fixed** in `2058a44`; checked on the device with 3 of 3 rooms in use.
2. **Minor:** after a successful start, Start Playback refreshes while it fades out and briefly
   shows "<x> is already playing in <room>" or "<x> will stop in <room>" for the playback it just
   started (and, for a link, the new runtime source in the list).
   **Fixed** in `2590294`; checked on the device (the closing screen keeps "Starting…" and the
   pre-start lists).
3. **Minor visual:** typed text in the link field sits at the top of the field instead of
   vertically centred with the link icon. **Fixed** in `8858cb8`; checked on the device.

Observations, not defects:

- Links play under the hub's generated name, e.g. "Playback (1791127318982)", because the app
  never sends `displayName` (plan). Candidate for the backlog.
- Hub: a start that fails (422) still stops the playback it would have replaced. DJ FM (stream
  unreachable) cut what was playing in Office. Hub-side; belongs in the `multiroom-ai` backlog.
