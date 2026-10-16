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

**Build under test**: local debug build of `33c79bc` (`0.5.0-alpha`, versionCode 5), installed with
`adb install -r` over the CI build `0.4.0-alpha+27`; run on 2026-10-16. Hub API 0.1.25.
**Device**: POCO 25078PC3EG, wireless adb. adb could install and read the screen (`uiautomator dump`,
`screencap`) but not tap (see Issues found), so the user made each tap and Claude Code set up the hub
with `curl`, read the screen and checked the hub after each step.
**Hub**: nothing was playing at the start. Volumes before: Office 30, Kitchen 30, Bedroom 50,
Bathroom 50 (Bathroom off); all set to 1 for the run. At the end the test routes were stopped first,
then KissFM turned back on and the volumes restored; every input's on/off state matched the record
taken before the run.

Status: ✅ passed · ❌ failed · ⏳ not checked yet · n/a not checkable on device

| # | Check | Status | Date | Notes |
|---|---|---|---|---|
| 1 | KissFM playing in one room: Sources tab shows "Playing in <room>" in amber (13 sp); idle sources keep their kind line | ✅ | 2026-10-16 | KissFM on Bedroom (`curl POST /routes`): row "Playing in Bedroom Speakers", text colour `#F2A541` sampled from the screencap, line 30 px high vs 28 px for the kind lines (13 vs 12 sp); KissFM 2.0 Deep and the others read "Stream · …" in `#9A9DA6` |
| 2 | Flip KissFM off → "Keep playing": dialog text as the contract; switch stays on; still playing | ✅ | 2026-10-16 | Dialog "Turn off KissFM? / KissFM is playing in Bedroom Speakers. Turning it off stops playback there." with "Keep playing" / "Turn off"; after Keep playing: no dialog, switch checked, line unchanged, hub `enabled:true`, route ACTIVE |
| 3 | Flip KissFM off → "Turn off": switch off; playback gone on Rooms at the next refresh; no "keeps playing" message | ✅ | 2026-10-16 | Screen and `/routes` logged every ~3.5 s: dialog at 18:54:36, route gone by 18:54:39, row back to "Stream · online.kissfm.ua" by 18:54:42; hub `enabled:false`. No message in any capture, none seen by the user. Rooms not opened; the hub had no routes |
| 4 | KissFM in a room and on a group not containing it: the dialog names both | ✅ | 2026-10-16 | KissFM on Bedroom and on group Living Room (Office + Kitchen): "KissFM is playing in Living Room and Bedroom Speakers. Turning it off stops playback there." (group by its own name, hub order) |
| 5 | A paused `file:` source (turned on for the check, off again after): the dialog appears | ✅ | 2026-10-16 | `oceanwavescrushing` turned on, started on Bedroom (`pauseable:true`), paused (`paused:true`; the hub kept `status: ACTIVE`). Flip off: "Turn off oceanwavescrushing? / oceanwavescrushing is playing in Bedroom Speakers. …"; Turn off → route gone, source off as before the run |
| 6 | KissFM idle: flip off: no dialog, no message, one tap | ✅ | 2026-10-16 | No routes; one tap, no dialog in the screen log, no message (user); hub `enabled:false` |
| 7 | KissFM off: flip on: on at once, no dialog | ✅ | 2026-10-16 | One tap, no dialog (user and screen log); hub `enabled:true` |
| 8 | Dialog open, route stopped by `curl`: dialog stays; "Turn off" turns the source off | ✅ | 2026-10-16 | With row 4's dialog open, both routes stopped (`DELETE`, 204); 8 s later (~3 refreshes) the dialog still showed its last text; Turn off → hub `enabled:false` |

## Issues found

None in the app.

- **Test device (observation, as in 004)**: the POCO (HyperOS) refuses `input tap` (INJECT_EVENTS)
  and `settings put` over adb unless Developer options → "USB debugging (Security settings)" is on,
  which needs a Mi account. The screen timeout therefore stayed at 5 min, and the user made the taps.
