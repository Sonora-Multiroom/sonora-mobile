# Verification: Confirm Before Turning Off a Playing Source

Results of [quickstart.md](quickstart.md): §1 (build and tests) and the on-device checks of §2,
run locally against the real hub.

## §1 Build and tests

Run on 2026-10-16 in a local session (Windows 10, Git Bash), commit `33c79bc` (feature branch
`005-source-off-confirm`), `api/openapi.json` at 0.1.25:

```bash
./gradlew :androidApp:assembleDebug :composeApp:testAndroidHostTest :composeApp:allTests :composeApp:check
```

Exit code 0, `BUILD SUCCESSFUL`. 604 Android host tests, 0 failed, 0 skipped (599 before US3: the
five new `SettingsBuilderTest` cases). iOS targets are skipped on Windows. No generated files in
`git status`.

Test-first evidence, each run before its implementation:

| Tasks | Failing run | Then |
|---|---|---|
| T004 → T005 | `Unresolved reference 'sourcePlaces'` | 21 `SettingsConfirmTest` tests green, the removal tests unchanged |
| T006–T008 → T009–T011 | `Unresolved reference 'TurnOffSource'` (3 suites) | 599 green |
| T012–T014 | green before T016, as planned (regression tests and removed assertions) | — |
| T015 → T016 | `No value passed for parameter 'keepsPlaying'` | 599 green, no `keepsPlaying` left in `composeApp/src` |
| T017–T018 → T019–T020 | `No parameter with name 'playingIn'`, `Too many arguments` | 604 green |

## §2 On-device checks

**Build under test**: ⏳ · **Device**: POCO 25078PC3EG ⏳ · Hub API 0.1.25.

Status: ✅ passed · ❌ failed · ⏳ not checked yet · n/a not checkable on device

| # | Check | Status | Date | Notes |
|---|---|---|---|---|
| 1 | KissFM playing in one room: Sources tab shows "Playing in <room>" in amber (13 sp); idle sources keep their kind line | ⏳ | | |
| 2 | Flip KissFM off → "Keep playing": dialog text as the contract; switch stays on; still playing | ⏳ | | |
| 3 | Flip KissFM off → "Turn off": switch off; playback gone on Rooms at the next refresh; no "keeps playing" message | ⏳ | | |
| 4 | KissFM in a room and on a group not containing it: the dialog names both | ⏳ | | |
| 5 | A paused `file:` source (turned on for the check, off again after): the dialog appears | ⏳ | | |
| 6 | KissFM idle: flip off: no dialog, no message, one tap | ⏳ | | |
| 7 | KissFM off: flip on: on at once, no dialog | ⏳ | | |
| 8 | Dialog open, route stopped by `curl`: dialog stays; "Turn off" turns the source off | ⏳ | | |

## Issues found

None so far.
