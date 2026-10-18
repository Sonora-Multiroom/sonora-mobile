# Verification: Rooms a Playback Is Really On, and Plain Refusal Messages

Results of [quickstart.md](quickstart.md): §1 (build and tests) and the on-device checks of §2,
run locally against the real hub.

## §1 Build and tests

Run on 2026-10-18 in a local session (Windows 10, Git Bash), feature branch
`006-route-outputs-refusals` with the code as committed in `ece4e7a`, `api/openapi.json` at 0.1.25:

```bash
./gradlew :androidApp:assembleDebug :composeApp:testAndroidHostTest :composeApp:allTests :composeApp:check --rerun-tasks
```

Exit code 0, `BUILD SUCCESSFUL`. 654 Android host tests, 0 failed, 0 skipped (604 before this
feature: 50 new tests, no 001–005 assertion edited). iOS targets are skipped on Windows. No
generated files in `git status`. The first run of the same command failed once in an existing test
(see Issues found); the rerun above passed.

Test-first evidence, each run before its implementation:

| Tasks | Failing run | Then |
|---|---|---|
| T006–T014 → T015–T020 | Compiled against stubs (`occupiedRooms` = `TODO()`, `NothingStops` text `TODO()`): 639 tests, the 29 new US1 tests failed, every 001–005 test green | 639 green |
| T021–T023 → T024–T025 | `turnedOff` stubbed to return null: 648 tests, 7 failed (4 `MessagesTest`, 2 `PlaybackStarterTest`, 1 `NowPlayingViewModelTest`) | 648 green |
| T026–T027 → T028–T029 | 654 tests, 5 failed (the "try again" copy and four retry rows). The "screen closed during the wait" row was green before, as nothing retried yet | 654 green |

## §2 On-device checks

**Build under test**: local debug build of `ece4e7a` (`0.6.0-alpha`, versionCode 6), installed with
`adb install -r` over the CI build `0.5.0-alpha+33`; run on 2026-10-18. Hub API 0.1.25.
**Device**: Pixel 5, wireless adb; Claude Code made the taps (`input tap`), read the screen
(`uiautomator dump`, `screencap`) and set up and checked the hub with `curl`, Do Not Disturb on.
**Hub**: before the run Chromecast Audio played on Kitchen Speakers; volumes Office 30, Kitchen 30,
Bedroom 50, Bathroom 50 (Bathroom off). Volumes set to 1 for the run. At the end the test route was
stopped first, then volumes restored and Chromecast Audio started on Kitchen again, as the user
asked; a fresh snapshot matched the one taken before except for that route's new id.
**Fixture**: `G` = Living Room (`M1` = Office Speaker, `M2` = Kitchen Speakers), free room =
Bedroom Speakers (rows 1–7), Kitchen / Office (rows 8–12), source = KissFM (KissFM 2.0 Deep for
rows 8–10).

Status: ✅ passed · ❌ failed · ⏳ not checked yet · n/a not checkable on device

| # | Check | Status | Date | Notes |
|---|---|---|---|---|
| 1 | Member `M1` of group `G` turned off while KissFM plays on `G`: Rooms card lists `M2` only, the count leaves `M1` out, `M1` under "Off" | ✅ | 2026-10-18 | KissFM on Living Room, then `PUT /outputs/office/enabled {false}`: `GET /routes` `outputs: ["kitchen"]`. Rooms: "1 of 4 rooms in use"; card "Living Room · Group / KissFM / Kitchen Speakers · Live stream", pill 1 % (Kitchen's); Idle lists Office Speaker "Turned off · Off" |
| 2 | Now Playing: "on `G` · `M2`"; pills `G` and `M2` only | ✅ | 2026-10-18 | "on Living Room · Kitchen Speakers"; pills "Living Room volume" and "Kitchen Speakers volume" only |
| 3 | Move sheet: "`M2` only" reads "Nothing stops", "`M1` only" "Turned off", `M1` listed once; moving to "`M2` only" is accepted | ✅ | 2026-10-18 | Rows: Bedroom "Idle", "Kitchen Speakers only · Nothing stops" (grey, as Idle), Bathroom "Turned off", "Office Speaker only · Turned off"; no plain Office or Kitchen row. Move to Kitchen Speakers: sheet closed, "on Kitchen Speakers", hub `targetId: kitchen`, same route id |
| 4 | Dragging the group pill changes only `M2` | ✅ | 2026-10-18 | Swipe on the Rooms pill to ~5 %: `GET /outputs` kitchen 6, office 1 |
| 5 | Settings: `M1` row "Off"; turning off `G` names `M2` only | ✅ | 2026-10-18 | Rooms tab: Office "Off", Kitchen "Playing · KissFM". Groups → Living Room switch: "Turn off Living Room? / KissFM is playing on Kitchen Speakers. …"; Keep playing left the group on |
| 6 | `M1` back on: both rooms shown again in the group's order, with `M1`'s pill | ✅ | 2026-10-18 | Hub `outputs: ["kitchen","office"]` (join order); Rooms "2 of 4 rooms in use", "Office Speaker + Kitchen Speakers"; Now Playing "on Living Room · Office Speaker + Kitchen Speakers", pills Living Room, Office, Kitchen |
| 7 | Move straight after turning `M1` back on succeeds with no message (3 tries; "not reached" if the rejoin is too fast) | ✅ (retry not reached) | 2026-10-18 | Sheet open with Bedroom selected, then Office off + on by `curl` and Move tapped at once, 3 times: each move landed on Bedroom with no message (frames and dumps). Hub log: the rejoin completes inside the PUT, 0.15–0.2 s before the transfer arrives, so no `ROUTE_CHANGING_OUTPUTS`; the retry is covered by `NowPlayingViewModelTest` |
| 8 | Start on a room turned off by `curl`: "`R` is turned off", `R` off right after | ✅ | 2026-10-18 | Kitchen selected with KissFM 2.0 Deep, Kitchen off by `curl`, Play at once: "Kitchen Speakers is turned off"; the Play in list then showed Kitchen Speakers "Turned off" |
| 9 | Start on a group turned off: "`G` is turned off" | ✅ | 2026-10-18 | "Living Room is turned off"; Living Room then "Turned off" in the list |
| 10 | Start on a group with every member off: "All rooms in `G` are turned off" | ✅ | 2026-10-18 | Office and Kitchen off by `curl`: "All rooms in Living Room are turned off"; both rooms and the group then "Turned off" |
| 11 | Start a source turned off: "KissFM is turned off", it leaves the list | ✅ | 2026-10-18 | Office selected with KissFM, `PUT /inputs/kissfm/enabled {false}`, Play: "KissFM is turned off"; KissFM gone from the sources after the refresh |
| 12 | Move to a room turned off while the sheet is open: "`B` is turned off", the sheet closes, the next sheet shows `B` "Turned off" | ✅ | 2026-10-18 | KissFM on Kitchen, sheet with Office selected, Office off by `curl`, Move: "Office Speaker is turned off", back on Now Playing "on Kitchen Speakers"; the reopened sheet shows "Office Speaker, Turned off" |

## Issues found

None in the app.

- **Hub observation**: turning a group member back on rejoins it before the PUT answers, so a move
  sent right after it was not refused with `ROUTE_CHANGING_OUTPUTS` in three tries (row 7).
- **Flaky existing test (not this feature)**:
  `RoomsViewModelControlsTest.dragAGroupThroughTheRealRepositoryNeverTouchesTheGroupVolumeEndpoint`
  failed once with `ConcurrentModificationException` at line 357. Its `MockEngine` appends to a
  plain `mutableListOf` on a real thread while the test reads the list, so the two can race. The
  test is unchanged since 001 and passed in every other run of this session.
