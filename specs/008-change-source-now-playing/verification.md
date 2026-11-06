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

⏳ Not run yet: they need the user's go-ahead (the run replaces what plays in rooms `A`, `B` and
group `G` on the real hub) and a phone. Status legend: ✅ pass · ❌ fail · ⏳ not run.

| # | Check | Status | Evidence |
|---|---|---|---|
| 1 | Action row: Stop · Change source… · Move to room… | ⏳ | |
| 2 | Change source… opens the fixed Start Playback; old playback still audible | ⏳ | |
| 3 | "will stop" line and Play label | ⏳ | |
| 4 | Play replaces; Back → Rooms | ⏳ | |
| 5 | Close and system Back return to the same Now Playing | ⏳ | |
| 6 | "Already playing" disables Play (fixed mode) | ⏳ | |
| 7 | "Already playing" from Rooms' "Play in room" | ⏳ | |
| 8 | Link replaces | ⏳ | |
| 9 | Group: card and replacement on the whole group | ⏳ | |
| 10 | Group with a room off keeps the group as target | ⏳ | |
| 11 | Target turned off while open | ⏳ | |
| 12 | Playback ends while Start Playback is open: no false "ended" notice | ⏳ | |
| 13 | Paused playback: Change source…; hub replaces it | ⏳ | |
| 14 | Four-column row at phone width | ⏳ | |
| 15 | Failed start | ⏳ | |
| 16 | Stale connection | ⏳ | |
| 17 | Process death restores the fixed screen | ⏳ | |
| 18 | TalkBack | ⏳ | |

## Issues found

None yet.
