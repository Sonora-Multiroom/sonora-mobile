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

**Build under test**: not yet installed.
**Device**: Pixel 5 (the user's choice for this run).
**Hub**: not yet recorded.

Status: ✅ passed · ❌ failed · ⏳ not checked yet · n/a not checkable on device

| # | Check | Status | Date | Notes |
|---|---|---|---|---|
| 1 | Member `M1` of group `G` turned off while KissFM plays on `G`: Rooms card lists `M2` only, the count leaves `M1` out, `M1` under "Off" | ⏳ | | |
| 2 | Now Playing: "on `G` · `M2`"; pills `G` and `M2` only | ⏳ | | |
| 3 | Move sheet: "`M2` only" reads "Nothing stops", "`M1` only" "Turned off", `M1` listed once; moving to "`M2` only" is accepted | ⏳ | | |
| 4 | Dragging the group pill changes only `M2` | ⏳ | | |
| 5 | Settings: `M1` row "Off"; turning off `G` names `M2` only | ⏳ | | |
| 6 | `M1` back on: both rooms shown again in the group's order, with `M1`'s pill | ⏳ | | |
| 7 | Move straight after turning `M1` back on succeeds with no message (3 tries; "not reached" if the rejoin is too fast) | ⏳ | | |
| 8 | Start on a room turned off by `curl`: "`R` is turned off", `R` off right after | ⏳ | | |
| 9 | Start on a group turned off: "`G` is turned off" | ⏳ | | |
| 10 | Start on a group with every member off: "All rooms in `G` are turned off" | ⏳ | | |
| 11 | Start a source turned off: "KissFM is turned off", it leaves the list | ⏳ | | |
| 12 | Move to a room turned off while the sheet is open: "`B` is turned off", the sheet closes, the next sheet shows `B` "Turned off" | ⏳ | | |

## Issues found

- **Flaky existing test (not this feature)**:
  `RoomsViewModelControlsTest.dragAGroupThroughTheRealRepositoryNeverTouchesTheGroupVolumeEndpoint`
  failed once with `ConcurrentModificationException` at line 357. Its `MockEngine` appends to a
  plain `mutableListOf` on a real thread while the test reads the list, so the two can race. The
  test is unchanged since 001 and passed in every other run of this session.
