# Start Playback: a chosen station is sometimes lost

- **Status:** Observed twice, not reproduced on demand, cause unknown. Written 2026-11-16.
- **Origin:** MOB-009 (0.9.0-alpha), found by the owner while testing the pull request, and seen
  once more on the Pixel 5 during the on-device run.
- **Depends on:** nothing. A decision on the open question below only if the cause turns out to be
  the "radio not active" rule.
- **Effort:** Unknown until the cause is found; the hardening in the Proposal is small.

## Problem

On Start Playback the user opens the radio screen, picks a station, and comes back to find that
**no station is selected and Play is disabled**. The target room stays selected and the radio
section is still shown.

Owner's report (2026-11-16, build unknown): Rooms → plus next to Kitchen (idle) → "Find a radio
station" → chose the language "ukrainian" → chose a station. Back on Start Playback the station
was not selected and Play was not available.

Seen on the Pixel 5 (debug build 0.9.0-alpha, a process that had run for hours and had played
and stopped several stations): the same steps, picking "Kiss FM 106.5" from the Ukrainian list.
Start Playback showed "Find a radio station", the two recent rows, Kitchen selected and a disabled
"Play".

## What was tried (all on the Pixel 5, hub API 0.1.34, `radio` extension ACTIVE)

After reinstalling the build, the same steps worked every time (about ten runs), including after
a Play, after "Play and Close at once", with a station that was and was not under "Recent", with
a station playing in another room, and with the filters left as the owner had them (language
"ukrainian"). With temporary `println` lines in `StartPlaybackViewModel`, a working run logs
`offer Unknown -> Offered`, then `onSelectStation <name> starting=false offer=Offered`, and
nothing clears the station afterwards.

So `onSelectStation` runs and the radio section is offered in the runs that work. In the failing
run no logging was installed, so we do not know which of the paths below ran.

## Where the station can be lost (hypotheses, none confirmed)

1. **`clearStation()` after a "not offered" answer.** The view model drops the chosen station for
   good when `RadioAvailability` becomes `NotOffered`, or leaves `Offered` for another state (a
   hub change). Start Playback asks the hub's extension list again every time it becomes visible,
   which is exactly when the user comes back from the radio screen. One answer that does not list
   `radio` as `ACTIVE` (the extension reloading, a hub restart) clears the station even though the
   next answer says `Offered` again and brings the rows back. This matches the symptom well.
   The same would happen if the radio screen met a plain 404 (`RadioFailure.Gone`): it calls
   `markGone()` and `reportGone()`, which clears the station and shows "Radio is no longer
   available on this hub" (a notice that may have been missed).
2. **A second, stale `StartPlaybackViewModel` taking the pick.** `StationPicker.picked` is a
   `StateFlow` that each Start Playback view model collects and then consumes. If an old view
   model were still alive (an entry not cleared), it could call `consumePicked()` first, and the
   visible one would miss the value through conflation. Not seen in the runs above; it would give
   exactly one working run after a restart and failures after some navigation paths.
3. **`started` or `starting` stuck on a new view model** (a leftover attempt in
   `PlaybackStarter`). Unlikely: Play would read "Starting…" and the screen would close.

## How to reproduce or diagnose next time

- Note first: the build (`Settings → footer`, `0.9.0-alpha+<n>` for a CI build), whether anything
  had been played in that process, and whether a "Radio is no longer available on this hub"
  notice appeared even briefly.
- On the phone, in the same process, repeat: Rooms → plus on an idle room → "Find a radio
  station" → choose a station. If the station is missing, **do not restart the app**.
- With adb (the POCO or the Pixel 5): read the hub's view at that moment with
  `curl $HUB/api/v2/extensions` and look at the `radio` entry's `status` and `connectionState`.
- To see which path ran, install a build with temporary `println` lines (`SPLOG`) in
  `StartPlaybackViewModel`: at the start of `onSelectStation`, in `clearStation()` (with
  `Throwable().stackTraceToString()`), and where the availability state is collected, then read
  `adb logcat -d -s System.out:I`. A `clearStation` line with an `availability` caller means
  hypothesis 1; a missing `onSelectStation` line on the visible screen means hypothesis 2.
- To test hypothesis 1 without waiting: make `RadioAvailability.refresh()` return `NotOffered`
  once (a debug-only switch or a fake hub answer) and pick a station.

## Proposal (once the cause is known; the first is cheap whatever it turns out to be)

- A single "radio not active" answer should hide the radio rows but **keep** the chosen station;
  drop it only after a second consecutive answer, a hub change, or a `Gone` from the radio screen.
  This changes the rule written in MOB-009 (a later answer that says the extension is not active
  deselects the station at once), so it needs the owner's decision.
- Add a unit test with two Start Playback view models on one `StationPicker` to pin down
  hypothesis 2, whichever way it falls.

## Open questions

- Should a briefly missing extension cost the user their chosen station? (See Proposal.)
- Does the hub's `radio` extension ever report anything but `ACTIVE` while it reloads? Ask on the
  hub side (`multiroom-ai`), not here.
