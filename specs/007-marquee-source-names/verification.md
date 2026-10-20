# Verification: Scroll Long Source Names

Results of [quickstart.md](quickstart.md): §1 (build and tests) and the on-device checks of §2.

## §1 Build and tests

Run on 2026-10-20 in a local session (Windows 10, Git Bash), feature branch
`007-marquee-source-names`, `api/openapi.json` at 0.1.28:

```bash
./gradlew :androidApp:assembleDebug :composeApp:testAndroidHostTest :composeApp:allTests :composeApp:check
```

Exit code 0, `BUILD SUCCESSFUL`. 662 Android host tests, 0 failed. iOS targets are skipped on Windows.

Test-first evidence: `MarqueeTextTest` was run before `MarqueeText.kt` existed and failed to compile
(`Unresolved reference 'marqueeLayout'`); it passes with the implementation.

## §2 On-device checks

Run on 2026-10-20 on the Pixel 5 (`model:Pixel_5`, 1080x2340, density 440), local debug build
`0.7.0-alpha` installed over `0.6.0-alpha` (a local build, so no `+<build>` suffix), from commit
`76f6942` plus the uncommitted feature work, against the production hub (API 0.1.28).
Setup: KissFM on Office and Radio NV on Kitchen at volume 1, both renamed with
`PUT /inputs/{id}/display-name`; Bedroom (Lounge FM, 7%) was playing before and was not touched.
A first attempt on a Lenovo tablet (TB331FC, ~800 dp wide) was abandoned: a 64-character name fits
one Rooms card line there, so nothing overflows. Status legend: ✅ pass · ❌ fail · ⏳ not run.

| # | Check | Status | Evidence |
|---|---|---|---|
| 1 | Long name scrolls on a Rooms card, short one still | ✅ | `screencap` frames 11:14:03-14: Office's name at its start, then scrolled ("…use Session with a very long tit…"), then wrapped round with the end and the start side by side; edges fade; text stops before the Stop button. Kitchen's "Radio NV" never moved, no "…" |
| 2 | No jump on refresh; keeps cycling past 3 cycles | ✅ | 30 frames over 45 s: every frame differs; frames at 11:14:38 and 11:15:09 are mid-scroll and continuous. Sampling was ~1.5 s, so a sub-second jump could not be ruled out by eye |
| 3 | Rename restarts from the beginning | ✅ | Renamed at 11:15:55; the frame at 11:15:56 shows "Zebra Sunset Radio - Afternoon" from its first letter, then scrolling |
| 4 | Now Playing: two lines first, then one scrolling line | ✅ | 64-char name "Up all night to the deep blue sea and back home again by the sea": one line scrolling ("Up all night to the deep blue sea a…") with animations on; with `animator_duration_scale 0` it fills two lines and ends "…", so it needs more than two. A name that fits two lines was not tried |
| 5 | Two long names start at different moments | ✅ | Radio NV renamed at 11:16:07; the frame at 11:16:13 shows Kitchen at "Morning Talk, News and Intervie…" while Office is near the end of its name: the phases differ. Two scrolling cards at once did not look too busy |
| 6 | Notification shade stops it | ✅ | `dumpsys window` showed `mCurrentFocus` on `NotificationShade` while the shade was down; frames 0.3 s and 0.8 s after closing show both names at their beginning. One earlier run caught a name mid-scroll about 1.7 s after closing, consistent with the 1.2-3 s start delay |
| 7 | Home and return | ✅ | HOME for 8 s, then relaunch: no error, `logcat -b crash` empty; names back at their start and scrolling |
| 8 | Stale: still, "…" | ✅ | User ran it (Wi-Fi cut): both cards dimmed, names still at their start ending in "…"; scrolled again when the network returned |
| 9 | Animations off: still, "…"; restore resumes | ✅ | `animator_duration_scale 0`: Rooms shows one line "Up all night to the deep blue se…" and "Radio NV - Morning Talk, New…", Now Playing shows two lines ending "…"; two screencaps 2 s apart identical. Restored to 1.0: scrolling resumed on Rooms without reopening |
| 10 | TalkBack reads the name once | ✅ | User ran it: the whole name was read once |
| 11 | Move sheet and Settings still end in "…" | ✅ | Settings → Rooms: "Playing · Radio NV - Morning Talk, N…" and "Playing · Up all night to the deep blu…". The Move to room sheet wraps names onto two lines as before (they fit two lines, so no "…" appeared) |

Also noted: the first build faded both edges, and the 12 dp left fade dimmed the first letter of a
name at rest on Rooms ("U", "Z", "K" looked faint). Padding the text into the gutter was tried and
rejected by the user on the device; the plan's fallback, fading the right edge only, was built
and the user judged it better (POCO, Rooms: the left edge is a plain cut, the right edge fades).
Rows 1-11 were run on the earlier, two-edge build; the change is in the draw code only. Two scrolling
cards at once did not look too busy.

Restored afterwards: both test routes stopped (Bedroom untouched), Office 8 and Kitchen 30, both
inputs renamed back with `{"displayName": null}`, the Pixel's screen timeout 60000, Do Not
Disturb off, `animator_duration_scale` 1.0. The tablet was returned to its own `wm size`, screen
timeout (300000) and Do Not Disturb off after the abandoned attempt.

### Second device: POCO (`model:25078PC3EG`, 720x1600, density 320, 360 dp wide)

Same build and setup, 2026-10-20 ~11:48. `persist.security.adbinput` is empty, so `input` taps and
`settings put` are refused: only the rows that need `curl` and `screencap` ran. Do Not Disturb was
set with `cmd notification set_dnd` (the screen timeout could not be changed, 300000 kept).

| # | Status | Evidence |
|---|---|---|
| 1 | ✅ | Office's long name scrolls with faded edges (both, in that build) and stops short of the Stop button; the frames show it at its start and mid-scroll |
| 3 | ✅ | After the rename, Office shows "Zebra Sunset Radio - After…" from its first letter |
| 5 | ✅ | Kitchen and Office scroll with different phases |
| 6 | ✅ | `mCurrentFocus` = `NotificationShade` while the shade was down; ~0.5 s after closing both names are at their start |
| 7 | ✅ | Launcher in front for 8 s (HOME intent), relaunch: no crash (`logcat -b crash` has only 10-16 entries), names back at their start |
| 2, 4, 8, 9, 10, 11 | ⏳ | Need taps, settings changes or the user's hands; run on the Pixel |

Restored afterwards: both routes stopped, Office 8, Kitchen 30, both names back, Do Not Disturb off.

## Issues found

- Hub: `PUT /inputs/{id}/display-name` answers 400 "Invalid UTF-8" for an em dash sent from Git
  Bash (curl passes the argument as cp1252); send the body from a file with `--data-binary @file`.
  Names over 64 characters are also refused with 400.
- App (cosmetic, fixed): the left fade dimmed the first letter of a still name; now only the right edge fades, see "Also noted".
