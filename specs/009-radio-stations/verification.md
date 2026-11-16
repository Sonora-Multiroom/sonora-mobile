# Verification: Find and Play Radio Stations

Results of [quickstart.md](quickstart.md): §1 (build and tests) and the on-device checks of §2.

## §1 Build and tests

Run on 2026-11-16 in a local session (Windows 10, Git Bash), feature branch `009-radio-stations`
at commit `67b2189` plus the documentation edits of phase 8, `api/openapi.json` at 0.1.34,
`gradle.properties` at `0.9.0-alpha` / code 9. Commands one at a time with
`ANDROID_HOME="$LOCALAPPDATA/Android/Sdk"` and each exit code read directly:

```bash
./gradlew :androidApp:assembleDebug         # exit 0
./gradlew :composeApp:testAndroidHostTest   # exit 0, 928 tests, 0 failed, 0 skipped
./gradlew :composeApp:allTests              # exit 0 (iOS targets skipped on Windows)
./gradlew :composeApp:check                 # exit 0, includes verifyLayering
```

`verifyLayering` found no generated radio type under `ui/` or `domain/`.

Test-first evidence: in every phase the new tests were written before the code they cover. They
never ran red: the first failure each time was a compile error (`Unresolved reference` to the
new types and functions), not an assertion. Failures after the code compiled were test mistakes
and were fixed in the tests: three `PlaybackStarterTest` tracker tests (the starter's immediate
refresh judged the entry against a snapshot without the route and dropped it), and two
`RadioViewModelTest` sheet tests (a wrong expected list for the letters "AN", and a tag sheet with
no tags to choose from). One pre-existing flaky test
(`RoomsViewModelControlsTest.dragAGroupThroughTheRealRepositoryNeverTouchesTheGroupVolumeEndpoint`,
a shared list read from two threads) failed once and passed on rerun; it is not part of this feature.

## §2 On-device checks

Not run yet. Status legend: ✅ pass · ❌ fail · ⏳ not run · n/a not applicable.

| # | Check | Status | Evidence |
|---|---|---|---|
| 1 | Rooms → "Play something" | ⏳ | |
| 2 | "Find a radio station" | ⏳ | |
| 3 | type `mango` | ⏳ | |
| 4 | look at rows; tap MANGORADIO | ⏳ | |
| 5 | select room `A` | ⏳ | |
| 6 | Play | ⏳ | |
| 7 | Back to Rooms | ⏳ | |
| 8 | "Play something" | ⏳ | |
| 9 | select the recent MANGORADIO row, room `A` | ⏳ | |
| 10 | a second station on `B`; `DELETE` its route by `curl` after ~10 s; open the radio screen | ⏳ | |
| 11 | a third station on `B`; press Home within 10 s; wait 40 s; reopen the app | ⏳ | |
| 12 | Now Playing → "Move to room…" → `A` (when the hub marks it transferable); wait until 30 s after the  | ⏳ | |
| 13 | radio screen → ✕ on one "Recent" row | ⏳ | |
| 14 | Country chip → sheet; type `ger`; choose Germany | ⏳ | |
| 15 | Tag chip → type `jazz` → choose "jazz"; then Language → choose "german" | ⏳ | |
| 16 | order control → "Trending"; then "Name A–Z" | ⏳ | |
| 17 | reopen a sheet with a value chosen | ⏳ | |
| 18 | type `qzxvqzxv` | ⏳ | |
| 19 | `adb shell am force-stop sonora.multiroom.mobile`; reopen; open the radio screen | ⏳ | |
| 20 | scroll the list down several pages | ⏳ | |
| 21 | look at the chips and the sheet | ⏳ | |
| 22 | look at Start Playback and the Play button | ⏳ | |
| 23 | look at the field and `GET` nothing by hand | ⏳ | |
| 24 | rotate; press Home and return | ⏳ | |
| 25 | Rooms, Now Playing, "Play something", Settings → Sources | ⏳ | |
| 25a | "Play something" | ⏳ | |
| 26 | select a station, a room, Play; Close at once | ⏳ | |
| 27 | Play | ⏳ | |
| 28 | open the radio screen; Retry; restore access; Retry | ⏳ | |
| 29 | look; restore Wi-Fi | ⏳ | |
| 29a | look; restore Wi-Fi | ⏳ | |
| 30 | focus a station row, a chosen chip's ✕, the order control, a "Recent" ✕, the selected station row in | ⏳ | |
| 31 | "Play something" | ⏳ | |

## Issues found

None yet.
