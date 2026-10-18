# Quickstart: validate Rooms a Playback Is Really On, and Plain Refusal Messages

Feature: [spec.md](spec.md) · Expected results: [contracts/ui-behaviour.md](contracts/ui-behaviour.md)

## 0. Before implementation (local)

- The design canvas's Move to room sheet has a "<room> only" sample row with the note "Leaves
  <group>", and `design/screens/Transfer.dc.html` is the updated copy
  ([research R9](research.md#r9-design-documentation-and-version)).

## 1. Cloud / CI checks (no hub, no emulator)

Prerequisites: JDK 17+ and `ANDROID_HOME` set. `gradle.properties` shows
`sonora.versionName=0.6.0-alpha` and `sonora.versionCode=6`. `api/openapi.json` is 0.1.25.

```bash
./gradlew :androidApp:assembleDebug
./gradlew :composeApp:testAndroidHostTest
./gradlew :composeApp:allTests
./gradlew :composeApp:check                  # includes verifyLayering
```

Expected: all green, no generated files in `git status`. Every 001–005 suite stays green without
edits to its existing assertions: those routes carry no `outputs` (SC-005).

| Suite | Proves |
|---|---|
| `KtorHubRepositorySnapshotTest` | `outputs` missing → null, `[]` → empty, blanks and duplicates dropped, order kept |
| `OccupiedRoomsTest` (new) | every row of the data-model table: all members, one off, missing, empty, unknown id, only unknown ids, outside the group, room target, unknown target; group order kept on rejoin (FR-012) |
| `RoutesByRoomTest` | `occupancy` and `routesByRoom` follow the list; unchanged without it |
| `RoomsBuilderTest` | fixture A/B/C: card rooms, pill value, `memberVolumes`, `inUse`, Idle and Off lists |
| `NowPlayingBuilderTest` | A/B/C: heading names, member pills, main pill volumes |
| `MoveDestinationsTest` | A/B/C: "only" notes incl. `LeavesGroup`, no duplicate plain row, "will stop" of other routes follows the list |
| `StartConsequenceTest` | the extra case of the contract: no "will stop" for a route that left the room |
| `SettingsBuilderTest`, `SettingsConfirmTest` | Rooms-tab line and room dialog follow the list; group dialog names occupied rooms |
| `MessagesTest`, `DestinationTextTest` | the contract's copy tables (FR-011) |
| `PlaybackStarterTest` | a `TurnedOff` failure requests a refresh, on the screen and detached; others do not |
| `NowPlayingViewModelTest` | the contract's retry table on virtual time (1 s), busy state during the wait, no "ended" during it |
| `RoomsViewModelControlsTest`, `NowPlayingViewModelTest` | group drag in fixture A sets only `kitchen`, on Rooms and on Now Playing |

## 2. On-device checks (local session, real hub)

Follow AGENTS.md "On-device verification": the POCO, volumes noted and set to 1 before the first
start, Do Not Disturb on, test routes stopped before volumes are restored, and every output left
enabled at the end. Use KissFM. `G` is a group with two rooms, `M1` and `M2` (on the hub: Living
Room = Office Speaker + Kitchen Speakers).

| # | Setup (`curl` on `/api/v2`) | Action in the app | Expected |
|---|---|---|---|
| 1 | KissFM on `G`; `PUT /outputs/M1/enabled {false}`; `GET /routes` shows `outputs: [M2]` | Rooms | card lists `M2` only; "1 of N rooms in use" counts only `M2` for it; `M1` under "Off" (SC-001) |
| 2 | as 1 | open the card's Now Playing | "on `G` · `M2`"; pills `G` and `M2` only |
| 3 | as 1 | "Move to room…" | "`M2` only" reads "Leaves `G`"; "`M1` only" reads "Turned off"; `M1` is listed once |
| 4 | as 1, `M2` volume 1 and `M1` volume 1 | drag the group pill on Rooms to ~5 % | only `M2` changes (`GET /outputs`); `M1` keeps 1 |
| 5 | as 1 | Settings → Rooms; Settings → Groups → turn off `G` → Keep | `M1` row "Off"; dialog says KissFM plays on `M2` only |
| 6 | as 1, then `PUT /outputs/M1/enabled {true}` | wait one refresh on Rooms and Now Playing | both rooms shown again, in the group's order, with `M1`'s pill (SC-002) |
| 7 | KissFM on `G`, both on | Settings → Rooms: turn `M1` off, then on; go straight to Now Playing → Move to room… → a free room → Move | the move succeeds with no message (SC-004). Repeat 3 times. If the hub's rejoin is too fast to hit `ROUTE_CHANGING_OUTPUTS`, record "not reached"; the view-model tests cover the retry |
| 8 | Start Playback open on a free room `R`; `PUT /outputs/R/enabled {false}` by `curl` | Play KissFM on `R` | "`R` is turned off"; `R` shows as off right after (SC-003). Turn `R` back on |
| 9 | Start Playback open on `G`; `PUT /groups/G/enabled {false}` | Play | "`G` is turned off"; afterwards `G` is off in the list. Turn `G` back on |
| 10 | Start Playback open on `G`; turn both `M1` and `M2` off by `curl` | Play | "All rooms in `G` are turned off". Turn both back on |
| 11 | Start Playback open with KissFM selected; `PUT /inputs/kissfm/enabled {false}` | Play | "KissFM is turned off"; KissFM leaves the source list. Turn it back on |
| 12 | KissFM playing in room `A`, Move sheet open with free room `B` selected; `PUT /outputs/B/enabled {false}` | Move | "`B` is turned off"; the sheet closes; the next sheet shows `B` "Turned off". Turn `B` back on |

Rows 8–12 depend on the app's lists being a refresh behind the `curl` call. Tap within ~2 s of the
`curl` call. If a refresh lands first, the target shows as off and cannot be chosen: retry the row.

Record the build, device and each row's evidence in `verification.md`. Then update
`docs/testing/full-app-test.md` (the constitution's gate).
