# Verification: Change What Plays from Now Playing

Results of [quickstart.md](quickstart.md): §1 (build and tests) and the on-device checks of §2.

## §1 Build and tests

Run on 2026-11-06 in a local session (Windows 10, Git Bash), feature branch
`008-change-source-now-playing`, `api/openapi.json` at 0.1.33, commands one at a time with
`ANDROID_HOME="$LOCALAPPDATA/Android/Sdk"` and each exit code read directly:

```bash
./gradlew :androidApp:assembleDebug     # exit 0
./gradlew :composeApp:testAndroidHostTest   # exit 0, 703 tests, 0 failed
./gradlew :composeApp:allTests          # exit 0 (iOS targets skipped on Windows)
./gradlew :composeApp:check             # exit 0
```

Test-first evidence: the new and extended tests (`AppBackStackTest`, `FixedTargetTest`,
`NowPlayingBuilderTest`, `NowPlayingTextTest`, `NowPlayingViewModelTest`, `StartPlaybackTextTest`,
`StartPlaybackViewModelTest`, `StartPlaybackBuilderTest`) were written before the code they cover
and did not compile against it (`Unresolved reference` to `FixedTarget`, `showStarted`,
`actionRowLayout`, `changeSourceVisible`, the new view model parameters). The first full run with
the implementation had three failures, all in tests written from a wrong assumption or against
the old fixture, and were fixed there: a room playing a live stream reads `LiveStream` in
`FixedTarget` (the picker's text shows it as "Playing"), and a Now Playing that has to decide
"ended" must be visible in the fenced-view-model fixture. One deviation from the task order: the
group rows of `FixedTarget` and `GroupPlaying.status` were written in the same pass as the room
rows instead of after a room-only stub that throws, because the tests for both were already
written and failing.

`StartConsequenceTest.aPausedPlaybackWillStopLikeAPlayingOne` is the characterization test the
tasks expect to pass at once.

## §2 On-device checks

Run on 2026-11-06 on the Pixel 5 (`model:Pixel_5`, 1080x2340), the only device connected; the user
named it for this run. Build under test: local debug APK `0.8.0-alpha` from commit `9963662`,
installed over `0.7.0-alpha`. Production hub, API 0.1.33. Rooms: `A` = Office Speaker,
`B` = Kitchen Speakers, `G` = Living Room (Office + Kitchen); Bedroom and Radio NV also used. Volumes
noted (Office 10, Kitchen 30, Bedroom 50) and set to 1 before the first start, restored at the end
after the routes were stopped; `oceanwavescrushing` was enabled for rows 13-14 and disabled again;
Do Not Disturb off and screen timeout 60000 restored. Taps by `input tap` with a foreground check
before each. Status legend: ✅ pass · ❌ fail · ⏳ not run.

| # | Check | Status | Evidence |
|---|---|---|---|
| 1 | Action row: Stop · Change source… · Move to room… | ✅ | UI dump and screenshot: three 76 dp buttons, captions "Change source…" and "Move to room…" one line each, nothing clipped, "Live streams can't be paused" below |
| 2 | Change source… opens the fixed Start Playback; old playback still audible | ✅ | Title "Change source"; "PLAY IN" has one node "Office Speaker, Playing · KissFM", no radio, no other target; nothing selected; `GET /routes` still held the KissFM route |
| 3 | "will stop" line and Play label | ✅ | "KissFM will stop in Office Speaker"; "Play Radio NV in Office Speaker" |
| 4 | Play replaces; Back → Rooms | ✅ | Now Playing for Radio NV on Office; `GET /routes`: one route, `radionv` on `office`; one Back → Rooms |
| 5 | Close and system Back return to the same Now Playing | ✅ | Both times Now Playing for Radio NV; route id unchanged (`route-c018daca6195`) |
| 6 | "Already playing" disables Play (fixed mode) | ✅ | "Radio NV is already playing in Office Speaker"; the Play parent node is `enabled=false`; a tap changed nothing (same route id, same screen). KissFM: "Radio NV will stop in Office Speaker", Play enabled; a typed SoundCloud link: Play link enabled |
| 7 | "Already playing" from Rooms | ✅ | Deviation: a busy room has no "Play in room" button, so via Rooms' "Play something" with the room picker: Radio NV on Bedroom → "Radio NV is already playing in Bedroom Speakers", the picker still shown, Play inert, routes unchanged (2 before and after) |
| 8 | Link replaces | ✅ | `soundcloud.com/forss/flickermood` from Change source: route on `office` (input `playback_…`), Now Playing "Flickermood — Forss"; Back → Rooms |
| 9 | Group: card and replacement on the whole group | ✅ | Card "Living Room, Group · Playing Kraina FM", "Kraina FM will stop in Living Room"; after Play: `kissfm` on `livingroom` `OUTPUT_GROUP`, `outputs` office + kitchen |
| 10 | Group with a room off keeps the group as target | ✅ | Kitchen turned off: route `outputs` = `[office]`; Change source card is still the group ("Group · Playing KissFM"); "Won't play in Kitchen Speakers (turned off)". Kitchen turned on again |
| 11 | Target turned off while open | ✅ | Office turned off: "Office Speaker is turned off", card "Turned off", Play disabled; turned on: "Idle", Play enabled with the same source. Close → Rooms with "Playback on Office Speaker ended" (the hub stopped the route when the room went off) |
| 12 | Playback ends while Start Playback is open: no false "ended" notice | ✅ | KissFM on `G`; Change source with Radio NV picked, `DELETE` of the route: the "will stop" line disappeared on the next refresh, no notice in three dumps over 6 s; Play started Radio NV on `G`; Back → Rooms, no notice in three dumps over 3 s. So the covered Now Playing really stopped announcing |
| 13 | Paused playback: Change source…; hub replaces it | ✅ | Paused `oceanwavescrushing` on Office: Stop · Resume · Change source…; "oceanwavescrushing will stop in Office Speaker", card "Paused · oceanwavescrushing"; after Play `GET /routes` held only `kissfm` on `office`: the hub replaced the paused route |
| 14 | Four-column row at phone width | ✅ | Screenshot: Stop · Pause · Change source… · Move to room…, equal columns, "Change source…" on two lines, nothing clipped |
| 15 | Failed start | ✅ | Link `http://10.255.255.1/stream.mp3` (DJ FM answered this time, so the unreachable link was used): "Starting…", then ~10 s later "The hub couldn't play this link" on the same screen, link kept; `GET /routes` no longer held Office's route (the hub stopped it first). Close: first run no notice seen in a dump 0.8 s later (the process had not been killed); repeated after row 17 with frames: Rooms shows "Playback ended" (no name, the view model was restored) |
| 16 | Stale connection | ✅ | Run by the user (Wi-Fi off ~8 s): stale banner and disabled Play on Start Playback, all four Now Playing buttons disabled, all back after Wi-Fi returned. Reported by the user, not seen by me |
| 17 | Process death restores the fixed screen | ✅ | Home, `am kill` (`pidof` empty), reopen: "Change source" with the Office card "Playing · KissFM", no picker |
| 18 | TalkBack | ⏳ | TalkBack speech not run. Accessibility tree checked instead: the button's description is "Change source"; the fixed card is one node "Office Speaker, Playing · KissFM"; the disabled Play's parent has `enabled=false` with the line above it readable |

## Issues found

- Row 15: in the first attempt a dump 0.8 s after Close showed no "ended" notice; I did not find out
  why (the dump may have come before the refresh answered). The later run with frames showed
  "Playback ended" on Rooms, so the behaviour is as specified.
- Hub observation (not an app issue): turning a room off stops the route playing only on it (row 11).
- One run of the "delete every route" teardown loop was blocked by the tool's permission check; the
  single route left running was deleted by id and the hub ended with no routes.
