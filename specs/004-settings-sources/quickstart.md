# Quickstart: validate Settings Tabs and Sources

## 1. Cloud / CI checks (no hub, no emulator)

Prerequisites: JDK 17+ and `ANDROID_HOME` set. `gradle.properties` shows
`sonora.versionName=0.4.0-alpha` and `sonora.versionCode=4`, and `api/openapi.json` is 0.1.21.

```bash
./gradlew :androidApp:assembleDebug
./gradlew :composeApp:testAndroidHostTest
./gradlew :composeApp:allTests
./gradlew :composeApp:check                  # includes verifyLayering
```

Expected: all green, with no generated files in `git status`. Every 001–003 suite stays green.
001's `SettingsViewModelTest` is replaced by the suites below.

New or extended suites (FR-021):

| Suite | Proves |
|---|---|
| `SettingsBuilderTest` | room status: off, not connected, off + not connected → Off, playing on its own, playing through a group, several playbacks (" + ", hub order, no duplicates), off while playing → Off, one group, several groups A→Z, no group → Speaker, Failed/Starting routes count as playing, Stopped doesn't. Group rows: members in hub order, unknown members skipped, none → empty; playing line for own playback, idle, off, and a member-only playback (no line). Sorting A→Z case-insensitive with id tie-break. Source split: configured / runtime / unknown origin → configured; runtime newest first, undated last |
| `AddedLineTest` | today / yesterday / earlier ("2 Oct 14:30"), across midnight and a DST change in a fixed `TimeZone`; with and without auto-removal and "Off · "; undated (parts kept, line omitted when empty) |
| `SourceDetailTest` | stream host, file name, line-in without its scheme (`alsa://…`), line-in without a scheme, blank → null |
| `SettingsConfirmTest` | asks: playing room, room playing through a group, playing group. Doesn't ask: idle room, idle group, group whose members play on their own, turning on, any source switch. Dialog values for a room, a group and several sources. `removeConfirmation` with routes addressed to a room and to a group, several routes, none. `keepsPlaying` in use / idle |
| `ExtensionRowsTest` | every badge and connection line including `Unknown`, Rejected/Disabled/Inactive overriding the connection, A→Z, loading off, empty |
| `SettingsTextTest` | every string of the UI contract "Copy" table, including "1 room" / "N rooms" and the hub row statuses |
| `MessagesTest` (extended) | every `SettingsFailure` copy and its mapping (FR-013, FR-018) |
| `KtorHubRepositorySettingsTest` / `…SnapshotTest` (extended) | [contract tests](contracts/hub-repository.md) 1–8 |
| `SettingsActionsTest` | optimistic value at once; second tap while in flight ignored; success → override kept until a refresh started after completion, then the hub decides (including the hub disagreeing); a refresh in flight during the request does not override; failure → override dropped and the message for 404 (+ refresh), unreachable and other; "keeps playing" message only for a source in use and only on success, never for rooms or groups; removal success, 404 as success, 400 / unreachable / other messages, row hidden until a fenced refresh drops it; detached → messages go to `AppMessages`; address change clears everything |
| `SettingsViewModelTest` (rewritten) | tab from `SettingsNavigator` and remembered after the view model is cleared; sheet open request consumed once; hub row for each session state; stale disables controls; turning off a playing room opens the dialog and sends nothing until "Turn off"; "Keep playing" sends nothing; dialog text follows a new snapshot and keeps the last text when the playback ends; remove with and without the dialog; extensions requested on tab show and after each refresh only on that tab, last inventory kept on failure; sheet: invalid address shows the 001 message and neither tests nor saves; test → Checking → Found(N) / Failed for no answer, timeout, error status and a non-hub reply; editing clears the result and cancels the test; Save normalises, saves and closes; Close discards |
| `AppBackStackTest` (extended) | `"sources"` in a saved stack decodes to Settings; Sources is no longer a destination |
| `ContrastTest` (extended) | danger on dangerContainer, onDanger on danger, positive on positiveContainer, textSoft on surfaceRaised |

## 2. On a device against the real hub (user, locally)

**Precondition (spec Assumptions, Dependency):** the hub runs the release in which turning a room
off stops its playback and drops it from group playback, and turning a group off stops its
playback. Then:

```bash
./gradlew refreshOpenApi        # re-fetch the contract; reconcile any change before testing
```

If the contract changed, stop: reconcile it (Constitution I) before continuing.

Other prerequisites: at least two rooms in a group, one room outside it, a stream, a line-in or
file, and a link that plays (`soundcloud.com/forss/flickermood`). Follow AGENTS.md "On-device
verification": volumes at 1 before starting, and stop test routes before restoring them. Turn
every room, group and source you switch off back on at the end. Compare with
`curl http://multiroom.lan:8080/api/v2/{outputs,groups,inputs}?includeDisabled=true` and
`/api/v2/routes`. Record the results in `verification.md`.

| # | Do | Expect |
|---|---|---|
| 1 | Bottom bar → Settings | Hub row "Connected" + address; tabs; Rooms tab selected; every room listed with the right status line (SC-003) |
| 2 | Bottom bar → Sources | Settings on the Sources tab; Settings highlighted |
| 3 | Groups tab, Rooms tab, leave to Rooms, come back | Reopens on the last tab (FR-002) |
| 4 | Turn an idle room off | No dialog; switch moves at once; `curl` shows `enabled:false` within 3 s; Rooms shows it "Off" (SC-001, SC-002); turn it back on |
| 5 | Play on the lone room; its row reads "Playing · <source>" in amber; turn it off | Dialog with the room text; "Keep playing" → nothing changes; again → "Turn off" → the room is off and its playback is gone on Rooms (hub change) |
| 6 | Play on the group; turn one member off | Dialog for the room; after "Turn off" the group plays on in the other member (check by ear) and the room is off; turn the room back on → the hub does not add it back (Rooms may still count it, known hub gap) |
| 7 | With the group playing, turn the group off | Group dialog with members; after "Turn off" the group playback stops (**hub change for groups**) |
| 8 | Turn a playing source off | No dialog; message "<name> is off. What's playing from it keeps playing."; it keeps playing; turn it back on |
| 9 | Play a link, stop it (auto-removed) and play another; Sources tab | "Added from apps" lists the playing one with "Added today HH:mm · removed when it stops" in phone time |
| 10 | Remove a stopped runtime source (from `curl -X POST /api/v2/inputs` or a DLNA source) | Gone at once, no dialog; `curl` no longer lists it |
| 11 | Remove the playing link | Remove dialog naming the room; "Remove" → playback stops, row gone |
| 12 | Extensions tab | Every extension of `GET /api/v2/extensions` with "Active" and the right connection line; stop the MQTT broker (if possible) → "Disconnected" within a refresh |
| 13 | Hub row → sheet; test `https://example.com:443` (an explicit port, since a bare host gets :8443); test `10.255.255.1`; test the real address | "Can't reach the hub at this address" (a non-hub reply); the same after ~3 s; "Hub found · N rooms" with N = all rooms incl. off |
| 14 | Type a wrong address, Close | Nothing saved; reopen shows the saved address |
| 15 | Save a new valid address, then back to the real one | Sheet closes; status goes "Connecting…" then follows; Rooms uses it |
| 16 | Cut the phone's Wi-Fi with Settings open (user) | "Not connected", stale banner, lists kept, switches and trash disabled, "Test connection" still answers; restore → re-enabled |
| 17 | Flip a switch and switch to Rooms at once | The change lands; a failure (e.g. hub stopped) shows as a message on Rooms |
| 18 | TalkBack over a switch row, the tabs, the hub row, a trash button and the dialog | Announcements as in FR-023 |
