# Quickstart: validate Now Playing and "Move to room…"

## 1. Cloud / CI checks (no hub, no emulator)

Prerequisites: JDK 17+, `ANDROID_HOME` set. `gradle.properties` shows `sonora.versionName=0.2.0-alpha`
and `sonora.versionCode=2`.

```bash
./gradlew :androidApp:assembleDebug
./gradlew :composeApp:testAndroidHostTest
./gradlew :composeApp:allTests
./gradlew :composeApp:check                  # includes verifyLayering
```

Expected: all green, with no generated files in `git status`.

Suites that must exist and pass, in addition to every 001 suite (which stay green):

| Suite | Proves |
|---|---|
| `AddressDetailTest` | host for stream/link, file name for `file:` and paths, null for line-in, unparseable shown as typed (FR-005) |
| `NamesTest` | `joinNames`: 1, 2, 3+ names |
| `NowPlayingBuilderTest` | [data-model](data-model.md) "Validation / rules": gone, failed, live vs line-in, pause/move visibility per status, group members order, missing target, mute and master mute |
| `MoveDestinationsTest` + `DestinationTextTest` | the FR-025 matrix (data-model, 18 cases) and the copy table (research R8) |
| `HubSessionTest` | the 001 polling scenarios moved here: 2.5 s, no overlap, 3 s timeout → Unreachable, stale keeps the snapshot, holder count 0 → no requests, acquire from 0 refreshes at once, address change resets |
| `VolumeDragControllerTest` | throttle ≤ 4 sends/s + final send; pending beats refresh until a later refresh; member drag changes one room and the group max; group drag moves member values; no `/groups/{id}/volume` |
| `KtorHubRepositoryActionsTest` | [contract tests](contracts/hub-repository.md) 1–4 |
| `NowPlayingViewModelTest` | opens from the session snapshot; Stop ok → `exit=Stopped`; Stop 404 → `Ended`; route gone elsewhere → `Ended` + "Playback on X ended" posted; pause/resume/mute requests and in-flight blocking; stale disables everything; sheet opens only when Live and moveVisible; selection cleared when it becomes unselectable; sheet closes when paused; confirm → `transferRoute` → follows the new id without a false "ended" (refresh fence); move failure closes the sheet with the message |
| `RoomsViewModel*Test` | the 001 scenarios against the shared session; `AppMessages` text appears as the Rooms message |
| `MessagesTest` | mute and move rows of the message table |
| `AppBackStackTest` | Now Playing push/pop, saver round-trip unchanged |
| `ContrastTest` | new text colours (contracts/now-playing-ui.md) |

## 2. On a device against the real hub (user, locally)

Prerequisites: the 001 setup (hub address saved). On the hub: one pauseable source, one live
stream, one line-in, a group of two rooms, and at least one idle room. Use `curl
http://multiroom.lan:8080/api/v2/routes` or another client to compare.

1. **Install**: `adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk`. Settings
   footer shows `0.2.0-alpha`.
2. **Open (US1-1/2)**: tap each card. Check the title, the "<Kind> · detail" line, the status
   chip and "on …" (group: "on Downstairs · Living Room + Kitchen").
3. **Live vs line-in (US1-5)**: the live stream shows the badge, Stop and "Live streams can't be
   paused", with no Pause. Line-in shows Stop only, without the badge or the line.
4. **Pause / Stop (US1-3/4, SC-001)**: pause and resume the pauseable source. Stop it: the app
   returns to Rooms (2 taps from Rooms) and the room becomes idle.
5. **Ended elsewhere (US1-6, SC-004)**: with Now Playing open, stop the route from another
   client. Within 3 s Rooms shows "Playback on <target> ended".
6. **Volume (US2, SC-005)**: on the group route, drag the group pill (members keep their ratio,
   checked against `/api/v2/outputs`). Drag one member (only it changes, and the group pill shows
   the new loudest). Mute and unmute the group. Turn master mute on in Rooms, come back: the pills
   are frozen, the button is disabled, and "All rooms are muted" shows.
7. **Move sheet (US3-2/3/4, SC-003)**: open "Move to room…" on the group stream. Check every note:
   idle room "Idle", busy room "<source> will stop", "Living Room only" → "Kitchen stops", a
   turned-off room dimmed, and the Groups section notes. Nothing is preselected and the button is
   disabled.
8. **Moves (US3-5, SC-002)**: move to an idle room, then to the busy room (its playback stops as
   announced), then to a group, then from the group to one member via "<Room> only". After each
   move Now Playing shows the source on the new target within 3 s, without going back to Rooms.
9. **Hidden while paused (FR-012)**: pause a transferable, pauseable source. "Move to room…"
   disappears.
10. **Failures (US3-6, edge cases)**: stop the hub with the sheet open. The button is disabled and
    the stale banner shows. Restart it. Cancel, swipe down and Back each close the sheet with no
    change.
