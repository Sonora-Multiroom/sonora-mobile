# Verification: Find and Play Radio Stations

Results of [quickstart.md](quickstart.md): §1 (build and tests) and the on-device checks of §2.

## §1 Build and tests

Run on 2026-11-17 in a local session (Windows 10, Git Bash), feature branch `009-radio-stations`
at commit `990fb3a` (`daefce5` plus the tracker fix of the code review, see Issues),
`api/openapi.json` at 0.1.34, `gradle.properties` at `0.9.0-alpha` / code 9. An earlier run
(2026-11-16, `67b2189`, 928 tests) came before two code commits and is superseded. Commands one at
a time with `ANDROID_HOME="$LOCALAPPDATA/Android/Sdk"` and each exit code read directly:

```bash
./gradlew :androidApp:assembleDebug         # exit 0
./gradlew :composeApp:testAndroidHostTest   # exit 0, 930 tests, 0 failed, 0 skipped
./gradlew :composeApp:allTests              # exit 0 (iOS targets skipped on Windows)
./gradlew :composeApp:check                 # exit 0, includes verifyLayering
```

`verifyLayering` found no generated radio type under `ui/` or `domain/`.

Test-first evidence for the review fix: `RecentStationTrackerTest.aFailedRefreshReplayingAnOldSnapshotNeverCountsAsPlaying`
failed with the old code (13 tests, 1 failed) and passes with the fix.

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

Run on 2026-11-16 on the Pixel 5 (`model:Pixel_5`, 1080x2340), the device the owner named for this
run (not the POCO). Production hub `multiroom.lan:8080`, API 0.1.34, `radio` extension ACTIVE,
no routes playing at the start. Rooms: `A` = Office Speaker, `B` = Kitchen Speakers, Bedroom also
used. Volumes noted (Office 30, Kitchen 30, Bedroom 50, Bathroom 50 and turned off) and set to 1
before the first start; test routes stopped first, then volumes restored; screen timeout 60000
and Do Not Disturb off restored. Taps by `input tap` after a foreground check. Build under test:
local debug APK 0.9.0-alpha (built from the working tree before `ab3f7d0` and `4abd363`; no CI build number was recorded, and the rows are not re-run on `daefce5`); rows 15-19 and later ran on the build with the two fixes listed
under Issues. The first session stopped at 13% battery; the rest ran after the phone was charged. Status legend: ✅ pass · ❌ fail · ⏳ not run · n/a not applicable.

| # | Check | Status | Evidence |
|---|---|---|---|
| 1 | Rooms → "Play something" | ✅ | Fresh app data, address `http://multiroom.lan:8080`: "OR A RADIO STATION" with "Find a radio station" between the link field and "OR PICK A SOURCE"; no recent rows; sources as before |
| 2 | "Find a radio station" | ✅ | Chip "The United States Of America" (phone region US), heading "STATIONS IN THE UNITED STATES OF AMERICA" (cut with "…"), "Most voted", no Recent. Screenshot checked: chip cut with "…" and ✕, heading and order control fit one line |
| 3 | type `mango` | ✅ | Chip removed, typed `mango`: MANGORADIO, Radio Mango, … within ~2 s; no "RECENT" while text was typed |
| 4 | look at rows; tap MANGORADIO | ✅ | Rows "name" + "DE · MP3 · 128 kbps"; tapped: Start Playback with "MANGORADIO, selected. Choose another station", "Radio · DE · MP3 · 128 kbps", "Change"; `GET /routes` was `[]` |
| 5 | select room `A` | ✅ | KissFM on Office (started by `curl`), room Office selected: "KissFM will stop in Office Speaker", "Play MANGORADIO in Office Speaker" |
| 6 | Play | ✅ | Play at 21:05:58; Now Playing "MANGORADIO", "Radio", Stop only, "Live streams can't be paused"; route `startedAt` 2.6 s after the tap; input `radiobrowser://78012206-…` ephemeral |
| 7 | Back to Rooms | ✅ | Rooms card: Office Speaker, MANGORADIO, "Radio · Live stream" (wording chosen by the owner 2026-11-16) |
| 8 | "Play something" | ✅ | After 40 s MANGORADIO is the first row under "Find a radio station" and first under "RECENT" |
| 9 | select the recent MANGORADIO row, room `A` | ✅ | MANGORADIO on Office: "MANGORADIO is already playing in Office Speaker", Play clickable node `enabled=false`; on Kitchen: enabled |
| 10 | a second station on `B`; `DELETE` its route by `curl` after ~10 s; open the radio screen | ✅ | Classic Vinyl HD on Kitchen, route deleted by `curl` ~9 s after the start: not under "RECENT" (only MANGORADIO) |
| 11 | a third station on `B`; press Home within 10 s; wait 40 s; reopen the app | ✅ | 101 SMOOTH JAZZ on Kitchen, Home after 3 s, reopened 42 s later: still playing, first under "RECENT" |
| 12 | Now Playing → "Move to room…" → `A` (when the hub marks it transferable); wait until 30 s after the  | ✅ | Blu Radio started 21:34:19 on Office, moved to Kitchen by "Move to room…" (same route id, `startedAt` unchanged); it entered "RECENT" at 30 s from the start. Caveat: the move itself came ~35 s after the start, so this does not tell "from the start" apart from "from the move"; the rule is covered by `RecentStationTrackerTest` |
| 13 | radio screen → ✕ on one "Recent" row | ✅ | ✕ on 101 SMOOTH JAZZ: gone at once, no question; gone from Start Playback; the route kept playing |
| 14 | Country chip → sheet; type `ger`; choose Germany | ✅ | Country sheet: every country with counts (US 6,979, Germany 5,604, …), field `ger` narrowed to Germany, Algeria, Nigeria, The Niger; chip "Germany" with ✕; heading "STATIONS IN GERMANY" |
| 15 | Tag chip → type `jazz` → choose "jazz"; then Language → choose "german" | ✅ | Tag `jazz` → "jazz, 1,064"; Language `german` → "german, 3,976"; three chips fit. First rows compared with `curl` only after the fix below; see Issues |
| 16 | order control → "Trending"; then "Name A–Z" | ✅ | Order control named "Trending", then "Name A–Z", then "Most voted"; the list reloaded each time (after the fix below) |
| 17 | reopen a sheet with a value chosen | ✅ | Order sheet reopened with Trending: that row selected (screenshot) |
| 18 | type `qzxvqzxv` | ✅ | `mango` with Germany/jazz/german: "No stations match", "Try another name or fewer filters.", "Clear filters"; tapped: three chips gone, name kept, order kept |
| 19 | `adb shell am force-stop sonora.multiroom.mobile`; reopen; open the radio screen | ✅ | `am force-stop`, reopened: chip Germany and the order kept, name empty, "RECENT" still there |
| 20 | scroll the list down several pages | ✅ | 14 swipes: 148 distinct names seen, so pages beyond the first 50 loaded. Duplicates across pages not checked by dump (overlapping windows); covered by `RadioViewModelTest` |
| 21 | look at the chips and the sheet | ✅ | Country "The United Kingdom Of Great Britain And Northern Ireland", tag "smooth jazz", language "english": the country chip ends in "…" with its ✕, the row scrolls sideways (the third chip is cut at the screen edge), heading "STATIONS IN THE UNITED KINGDOM OF …"; in the country sheet the long name is one row with its count 1,943 at the right |
| 22 | look at Start Playback and the Play button | ✅ | `Blu Radio, señal nacional (HJCK, …)` (246 characters): selected name scrolls as a marquee on Start Playback, unselected recent/list rows end in "…", Play reads "Play Blu Radio, señal naci… in Office Speaker" (the full label is the node text). The label runs close to the pill's right edge |
| 23 | look at the field and `GET` nothing by hand | ✅ | `100%` typed: no `%` appeared in the field, no message |
| 24 | rotate; press Home and return | ✅ | Name `jazz` typed; rotated to landscape and back, then Home and back: name, filters and the 20 listed rows kept |
| 25 | Rooms, Now Playing, "Play something", Settings → Sources | ⏳ | Intense Radio started by `curl` on Bedroom: Rooms "Radio · Live stream"; Settings → Sources "ADDED FROM APPS", "Added today 21:28 · removed when it stops" (no kind in words); not in "RECENT" after 4 min. Now Playing and Start Playback rows not re-read |
| 25a | "Play something" | ✅ | MANGORADIO playing and recent: shown once (row 8), not under "OR PICK A SOURCE" |
| 26 | select a station, a room, Play; Close at once | ✅ | Play of Blu Radio on Office, Close within 1 s: Rooms; the playback appeared on the Office card; no other notice later (dumps at 1 s and 7 s) |
| 27 | Play | n/a | Not tried: no station was found that fails to start |
| 28 | open the radio screen; Retry; restore access; Retry | ⏳ | User row, not done |
| 29 | look; restore Wi-Fi | ⏳ | User row, not done |
| 29a | look; restore Wi-Fi | ⏳ | User row, not done |
| 30 | focus a station row, a chosen chip's ✕, the order control, a "Recent" ✕, the selected station row in | ⏳ | User row (TalkBack), not done. Accessibility tree read instead: "Remove the country Germany", "Order: Most voted. Change the order", "Remove MANGORADIO from Recent", "MANGORADIO, selected. Choose another station" |
| 31 | "Play something" | n/a | No hub without radio at hand; covered by `StartPlaybackViewModelTest` |

## Issues found

- **A station list stayed on "Loading stations" after a filter or order change (rows 15-17).**
  Found on the device and confirmed by the owner. Cause: `HubAddressStore.address` came from the
  one shared preferences file, which emits on every write; saving the new filters replaced the
  session's repository while the list's request was still on its way, so its answer was dropped
  as stale. Proved with temporary log lines (`same repo=false` after a successful call); fixed
  by `distinctUntilChanged()` on the address, with `HubAddressStoreTest` (red first). The unit
  tests had not seen it because the in-memory address store never emits twice.
- The search field's typed text was reported as aligned to the top of the field by the owner on
  their device. On the Pixel it already looked centred; the text field now fills its 44 dp box
  and centres its text, in the radio screen and in the filter sheets.
- Observation (rows 22): the Play button's label runs up to the pill's right edge when the station
  name is long, because the name takes all the remaining width. Readable and complete; not changed.
- **Code review (2026-11-17, `daefce5`): the Recent tracker judged a failed refresh.** A failed
  refresh re-emits the last good snapshot as Unreachable, so a station stopped while the app was
  away could enter "Recent" when the hub could not be reached on return. Fixed: only a Live state
  is judged; test above. Row 25 is ⏳ because its Now Playing and Start Playback rows were not re-read.
- **Open, cause unknown: a station chosen on the radio screen is sometimes not selected on
  return, and Play stays disabled.** Seen once by the owner and once in the Pixel 5 run; not
  reproduced. Recorded in `docs/backlog/start-playback-station-lost.md`. On 2026-11-18 the owner
  could not reproduce it either and decided it stays in the backlog, so it does not block this PR.
- **Chip ✕ size (2026-11-18): measured on the POCO, 48 × 48 dp, not 40 × 44 dp.** The ✕ is drawn
  at 40 dp wide in `RadioScreen.kt`, but its accessibility bounds are 96 × 96 px at 320 dpi
  (`[272,342][368,438]` on the radio screen, country chip The United States Of America), so the
  touch area is 48 dp square. The chip pill itself is 88 px (44 dp) tall. Cause: Compose applies
  its 48 dp minimum to clickable elements. The 44 dp touch rule (constitution VI) is therefore met
  without a code change, and the 44 dp variant that was built for comparison measured the same.
  Build: local debug APK, source as at `git` HEAD `acb48c3` (`composeApp` clean), SHA-256
  `b8be06f5…537d`, identical to the installed `base.apk` on the phone (versionName 0.9.0-alpha).
  Measured with `uiautomator dump`, read-only; the phone refused injected taps, so the radio
  screen was opened by hand.
  Again with the country chip Germany (2026-11-18): the ✕ is `[177,342][273,438]`, 96 × 96 px,
  48 × 48 dp, the same as with The United States Of America.
- Rows 28-30 need the user and were not done; row 27 and 31 were not possible (no failing station,
  no hub without radio).

## Reviews

| Mode | Date | Commit | Verdict | Notes |
|---|---|---|---|---|
| code | 2026-11-17 | daefce5 | CONCERNS | Tracker fix applied; chip ✕ measured at 48 dp on the phone (2026-11-18, no code change); selection-loss defect not reproduced, kept in the backlog by the owner (2026-11-18) |
