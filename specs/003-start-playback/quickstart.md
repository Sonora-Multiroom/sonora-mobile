# Quickstart: validate Start Playback

## 1. Cloud / CI checks (no hub, no emulator)

Prerequisites: JDK 17+, `ANDROID_HOME` set. `gradle.properties` shows `sonora.versionName=0.3.0-alpha`
and `sonora.versionCode=3`. `api/openapi.json` is version 0.1.21.

```bash
./gradlew :androidApp:assembleDebug
./gradlew :composeApp:testAndroidHostTest
./gradlew :composeApp:allTests
./gradlew :composeApp:check                  # includes verifyLayering
```

Expected: all green, with no generated files in `git status`. Every 001 and 002 suite stays green
(the contract reconciliation of research R1).

New or extended suites (FR-017):

| Suite | Proves |
|---|---|
| `LinkAddressTest` | empty, spaces, `soundcloud.com/a/b` → `https://…`, `http://` kept, `ftp://` invalid, `https://` without host invalid, single-label host valid, IPv6 literal, port, text with spaces invalid (FR-006) |
| `RoutesByRoomTest` | room route, group route under each member, several routes per room in hub order, Stopped ignored, unknown target covers nothing |
| `StartPlaybackBuilderTest` | source filtering (turned off left out, runtime included) and A→Z order; target order (FR-007); every `TargetStatus` row of the [data model](data-model.md) (idle; own playback in each state incl. live stream → Playing; several playbacks joined; in a group; own beats group; turned off; not connected; both → turned off; group playing; group idle with members; group with unplayable members; no rooms; no playable room → turned off / not connected; announcements ignored); unknown target types not listed |
| `StartConsequenceTest` | Replace: idle (no line), busy room, room in a group playback (named by group), group over several playbacks, same source on same target (already playing), same source on overlapping target (named as stopping), announcement left out. Mix: busy → alongside, idle → none. Announcement: busy → lowered including announcements, idle → none. Link → always replace. Unknown/absent default → replace. A route with an unknown join mode is named as stopping. Notes: won't play (turned off, not connected, both), target muted (room, group, all members), master mute replaces target muted |
| `StartPlaybackTextTest` | the strings for every status, line and note; name joining |
| `MessagesTest` (extended) | every `StartFailure` copy; the R6 mapping table for `/routes` and `/play` |
| `KtorHubRepositoryActionsTest` / `…SnapshotTest` (extended) | [contract tests](contracts/hub-repository.md) 1–7 |
| `StartPlaybackViewModelTest` | opens empty / with the room preselected; preselected room unselectable → dropped; FR-005 exclusivity; link message only after paste/blur/Done and cleared when valid; Play enabled only with both selections and Live; stale disables; pruning when the source or target vanishes or turns off; Play → "Starting…", selections locked, one request; success → `exit=Started(routeId, startedAfterSeq, targetName)`; existing route returned → same; 404 → refresh then "<name> is no longer on the hub"; timeout + matching route in the fresh snapshot → `Started` (source and link); timeout + no match → "Couldn't reach the hub"; timeout + refresh failing → same; other errors keep selections and show the message; selections restored from `SavedStateHandle` |
| `HubSessionTest` (extended) | `awaitFreshSnapshot`: requests a refresh; ignores the refresh already in flight and returns the next one's snapshot; returns `null` after 5 s when refreshes fail or no screen holds the session |
| `PlaybackStarterTest` | the request survives the view model; a failure after Close is posted to `AppMessages`; a success after Close posts nothing; `requestRefresh()` after success |
| `NowPlayingViewModelTest` (extended) | opened with `startedAfterSeq`: a snapshot without the route does not end the screen until a refresh started after the fence; then a missing route → "Playback on <targetName> ended" |
| `AppBackStackTest` (extended) | `replaceTop` swaps Start Playback for Now Playing, so Back → Rooms; the saver still writes `now:<id>` |

## 2. On a device against the real hub (user, locally)

Prerequisites: the hub at 0.1.21 with at least one stream, one line-in or file, a group of two
rooms, and one idle room. Compare with `curl http://multiroom.lan:8080/api/v2/routes`. Record the
results in `verification.md`.

| # | Do | Expect |
|---|---|---|
| 1 | Rooms → "Play something" | Screen opens with nothing selected; Play reads "Play" and is disabled |
| 2 | Idle room → "Play something in <room>" | That room is selected |
| 3 | Pick a stream + the idle room → Play | Now Playing opens for it within ~3 s; Back → Rooms, which shows it (SC-001, SC-004) |
| 4 | Start a different source on that busy room | Warning "<first> will stop in <room>" shown first; after Play the first stops and nothing else (SC-003) |
| 5 | With a group playing, select one of its rooms | Warning names the group ("… will stop in <group>"); after Play the whole group playback stops (confirms the spec assumption for starts) |
| 6 | Select the source already playing on exactly that target | "<source> is already playing in <target>"; Play opens Now Playing for the same playback |
| 7 | Paste `soundcloud.com/<artist>/<track>` → target → Play | "Starting…" while the hub resolves; Now Playing opens (SC-002) |
| 8 | Paste a link the hub cannot reach / a non-media page | Screen stays, link and target kept, message per FR-016 |
| 9 | Type `hello world` | No message while typing; leave the field → "Enter a web address (https://…)"; Play disabled |
| 10 | Turn a group member off (Settings or hub) → select the group | "Won't play in <room> (turned off)" |
| 11 | Cut the phone's Wi-Fi with the screen open | Stale banner, selections kept, Play disabled; restore → Play re-enables |
| 12 | Tap Play on a link and Close at once | Rooms; the playback appears, or a failure message shows there |
| 13 | Link timeout recovery (if a slow link is available) | When the 30 s answer is lost but the hub plays it: Now Playing opens. **Check R8's risk**: does the runtime input keep the exact address sent? |
