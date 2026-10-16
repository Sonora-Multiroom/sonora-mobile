# Quickstart: validate Confirm Before Turning Off a Playing Source

## 0. Before implementation (local)

- The design canvas's Settings screen shows a playing configured source and its turn-off dialog,
  and `design/screens/Settings.dc.html` is the updated copy ([research R7](research.md#r7-design-update-first)).

## 1. Cloud / CI checks (no hub, no emulator)

Prerequisites: JDK 17+ and `ANDROID_HOME` set. `gradle.properties` shows
`sonora.versionName=0.5.0-alpha` and `sonora.versionCode=5`; `api/openapi.json` is 0.1.25.

```bash
./gradlew :androidApp:assembleDebug
./gradlew :composeApp:testAndroidHostTest
./gradlew :composeApp:allTests
./gradlew :composeApp:check                  # includes verifyLayering
```

Expected: all green, no generated files in `git status`, every 001–004 suite still green apart from
the "keeps playing" assertions this feature replaces.

| Suite | Proves (FR-007, FR-009) |
|---|---|
| `SettingsConfirmTest` | `TurnOffSource` for a source playing in a room, on a group, in several places (each once, hub order), only paused, starting or failed; null for an idle source, a source that is off, a stopped-only route, a source the hub no longer lists, and turning on. `removeConfirmation` results unchanged. No `keepsPlaying` left |
| `SettingsBuilderTest` | `playingIn` for the same cases; empty when the source is off even with a live route; runtime rows unchanged |
| `SettingsTextTest` | the [copy table](contracts/settings-ui.md#copy-table-additions-for-settingstexttest); kind line unchanged when idle; no "keeps playing" string |
| `SettingsActionsTest` | turning a source off reports nothing on success; failures as 004 FR-013 |
| `SettingsViewModelTest` | flipping a playing source off opens the dialog and sends nothing; "Keep playing" sends nothing; "Turn off" sends `enabled=false` once; an idle source is sent at once; an open source dialog stays a turn-off dialog across refreshes and follows the places |

## 2. On-device checks (local session, real hub)

Follow AGENTS.md "On-device verification": the POCO phone, volumes noted and set to 1 before the
first start, test routes stopped before volumes are restored, and every source left in the state
it had before the run (KissFM on, the `file:` source of row 5 off). Use a known-good stream (KissFM).

| # | Setup (`curl` on `/api/v2`) | Action in the app | Expected |
|---|---|---|---|
| 1 | KissFM playing in one room | Settings → Sources | KissFM reads "Playing in <room>" in amber; idle sources show their kind line |
| 2 | as 1 | flip KissFM off → "Keep playing" | dialog text as the contract; switch stays on; still playing |
| 3 | as 1 | flip KissFM off → "Turn off" | switch off; playback gone on Rooms at the next refresh; no "keeps playing" message |
| 4 | KissFM in a room and on a group that does not contain that room (otherwise the hub refuses with `INPUT_ALREADY_ON_OUTPUT`) | flip off | the dialog names the room and the group |
| 5 | KissFM cannot be paused (live stream), so use a configured `file:` source, e.g. the oceanwave sample. All of them are off on the hub: turn it on with `PUT /inputs/{id}/enabled`, start it in one room, confirm `pauseable: true` in `GET /routes` and pause it | flip off | the dialog appears. Afterwards the source stays off (its original state). If no `file:` route is pauseable, mark the row n/a: T006 and T017 cover the paused case |
| 6 | KissFM idle | flip off | no dialog, no message, off in one tap |
| 7 | KissFM off | flip on | on at once, no dialog |
| 8 | dialog open; stop the route by `curl` | wait one refresh, then "Turn off" | dialog stays; source turns off |

Record the build, device and each row's evidence in `verification.md`.
