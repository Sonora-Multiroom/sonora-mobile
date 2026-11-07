# TinySpec: "Change source…" only for replacing playbacks

**Branch**: `tiny/change-source-replace-only`
**Date**: 2026-11-07
**Status**: draft
**Complexity**: small
**Version**: `0.8.1-alpha` (versionCode 8)

## What

Now Playing offers "Change source…" (MOB-008) only for a playback that joined its room or group
with `REPLACE`, or whose join mode the hub did not report. Rooms shows a card per playback, so a
mixed-in (`MIX`) or announcement (`DUCK_OTHERS`) playback has its own Now Playing. There the action
misleads: a replacing start leaves an announcement playing and starts the new source under it, and
from a mixed-in playback it also stops the room's main playback, which the user never chose.

## Context

| File | Role |
|------|------|
| `composeApp/src/commonMain/kotlin/sonora/multiroom/mobile/domain/NowPlayingBuilder.kt` | Modified: `changeSourceVisible` also checks `route.joinMode` |
| `composeApp/src/commonTest/kotlin/sonora/multiroom/mobile/domain/NowPlayingBuilderTest.kt` | Modified: join-mode cases |
| `composeApp/src/commonMain/kotlin/sonora/multiroom/mobile/domain/Models.kt` | Context: `JoinMode { Replace, Mix, Announcement, Unknown }`, `Route.joinMode` |
| `composeApp/src/commonMain/kotlin/sonora/multiroom/mobile/ui/nowplaying/NowPlayingText.kt` | Context: button count follows `changeSourceVisible`, so the row stays even |
| `docs/testing/full-app-test.md` | Modified: new row, header |
| `gradle.properties`, `CHANGELOG.md` | Modified: `0.8.1-alpha` |
| `docs/backlog/rooms-with-several-playbacks.md` | Context: the decision (2026-11-07), "Proposal" |

## Requirements

1. "Change source…" is shown when the route's `joinMode` is `REPLACE` or absent (`Unknown`), and
   its target is known, as today.
2. It is hidden for a `MIX` or `DUCK_OTHERS` route, whatever its status; Stop (and Pause, Move when
   they apply) stay.
3. With the button hidden, the remaining buttons share the row as for any other count.

## Constitution Check

| Principle | Status |
|---|---|
| I. API Contract Fidelity | ✅ Reads `RouteResponse.joinMode`, already mapped; no new call |
| II. Truthful UI | ✅ Removes an action whose effect does not match its label |
| III. Shared-First | ✅ `commonMain` domain only |
| IV. Test-First | ✅ T2 is written and seen failing before T3; T6 is the build-and-tests gate |
| V. Resilient LAN Networking | ✅ No change |
| VI. Design Fidelity & Accessibility | ✅ No new UI; the row layout already handles 1–4 buttons |
| VII. Minimal Dependencies | ✅ None added |
| Full app test gate | ✅ T5 |

## Tasks

- [X] T1 Version `0.8.1-alpha`, versionCode 8 in `gradle.properties` (first commit)
- [X] T2 Tests in `NowPlayingBuilderTest`: shown for `Replace` and `Unknown`; hidden for `Mix` and
      `Announcement` (playing and paused). Run them and see the hidden cases fail
- [X] T3 `NowPlayingBuilder`: `changeSourceVisible` requires `joinMode` `Replace` or `Unknown`
- [X] T4 `CHANGELOG.md`: `0.8.1-alpha` entry under "Changed"
- [X] T5 `docs/testing/full-app-test.md`: row for a `MIX` route (started by `curl` with
      `joinMode`) without "Change source…"; header to `0.8.1-alpha`
- [X] T6 `assembleDebug`, `testAndroidHostTest`, `allTests`, `check` all exit 0
- [ ] T7 On-device check, recorded below

## On-device check

Not run yet.

Gate (T6), 2026-11-07, local: `assembleDebug :composeApp:testAndroidHostTest :composeApp:allTests :composeApp:check` exit 0; host tests 705, 0 failed. T2 seen failing first ("Mix playing").
