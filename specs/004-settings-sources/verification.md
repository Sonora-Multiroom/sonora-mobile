# Verification: Settings Tabs and Sources

Results of the on-device checks in [quickstart.md](quickstart.md) §2, to be run locally against the
real hub. §1 (build and tests) passes in the cloud session that implemented the feature.

**§2 waits for the hub release** (plan "Open points": stopping playback when a room or group is
turned off) and starts with `./gradlew refreshOpenApi`. If the contract changed, reconcile it
before running the rows (Constitution I) and ask before changing behaviour. Rows 5–7 can only pass
after the release; the others can run before it.

**Build under test**: _(CI build number and commit, `0.4.0-alpha`)_
**Device**: _(model, how driven)_ · All outputs at volume 1 during the run; test routes stopped
before the volumes are restored; everything switched off turned back on.

Status: ✅ passed · ❌ failed · ⏳ not checked yet · n/a not checkable on device

| # | Check | Status | Date | Notes |
|---|---|---|---|---|
| 1 | Bottom bar → Settings: Hub row "Connected" + address; tabs; Rooms tab selected; every room listed with the right status line (SC-003) | ⏳ |  |  |
| 2 | Bottom bar → Sources: Settings on the Sources tab; Settings highlighted | ⏳ |  |  |
| 3 | Groups tab, Rooms tab, leave to Rooms, come back: reopens on the last tab (FR-002) | ⏳ |  |  |
| 4 | Turn an idle room off: no dialog; switch moves at once; `curl` shows `enabled:false` within 3 s; Rooms shows it "Off" (SC-001, SC-002); turn it back on | ⏳ |  |  |
| 5 | ⚑ hub release: play on the lone room, its row reads "Playing · <source>" in amber; turn it off: dialog with the room text; "Keep playing" changes nothing; "Turn off" → room off and its playback gone on Rooms | ⏳ |  |  |
| 6 | ⚑ hub release: play on the group, turn one member off: dialog for the room; after "Turn off" the group plays on in the other member (by ear), the room is off; turn it back on: the hub does not add it back (Rooms may still count it, known hub gap) | ⏳ |  |  |
| 7 | ⚑ hub release: with the group playing, turn the group off: group dialog with members; after "Turn off" the group playback stops | ⏳ |  |  |
| 8 | Turn a playing source off: no dialog; message "<name> is off. What's playing from it keeps playing."; it keeps playing; turn it back on | ⏳ |  |  |
| 9 | Play a link, stop it (auto-removed), play another; Sources tab: "Added from apps" lists the playing one with "Added today HH:mm · removed when it stops" in phone time | ⏳ |  |  |
| 10 | Remove a stopped runtime source (a DLNA source, or `curl -X POST /api/v2/inputs`): gone at once, no dialog; `curl` no longer lists it | ⏳ |  |  |
| 11 | Remove the playing link: Remove dialog naming the room; "Remove" → playback stops, row gone | ⏳ |  |  |
| 12 | Extensions tab: every extension of `GET /api/v2/extensions` with "Active" and the right connection line; stop the MQTT broker (if possible) → "Disconnected" within a refresh | ⏳ |  |  |
| 13 | Hub row → sheet; test `https://example.com:443`, `10.255.255.1` and the real address: "Can't reach the hub at this address" (a non-hub reply); the same after ~3 s; "Hub found · N rooms" with N = all rooms incl. off | ⏳ |  |  |
| 14 | Type a wrong address, Close: nothing saved; reopen shows the saved address | ⏳ |  |  |
| 15 | Save a new valid address, then back to the real one: sheet closes; status goes "Connecting…" then follows; Rooms uses it | ⏳ |  |  |
| 16 | Cut the phone's Wi-Fi with Settings open (user): "Not connected", stale banner, lists kept, switches and trash disabled, "Test connection" still answers; restore → re-enabled | ⏳ |  |  |
| 17 | Flip a switch and switch to Rooms at once: the change lands; a failure (e.g. hub stopped) shows as a message on Rooms | ⏳ |  |  |
| 18 | TalkBack over a switch row, the tabs, the hub row, a trash button and the dialog: announcements as in FR-023 | ⏳ |  |  |

## Open points

- The dialog scrim is the platform's dim, not exactly `rgba(5,6,8,0.72)` (plan "Open points"). If it
  looks clearly wrong on the device, record it here.
- A runtime source without `createdAt` drops the "Added <when>" part and sorts last (defensive; the
  hub always sets it).

## Issues found

_(none yet)_

Observations from the cloud session, not defects:

- The generated client splits its paths on `/`, so a hub id containing a slash cannot be addressed
  by any call (this applies to the 001–003 calls as well). Ids with spaces are encoded. Hub ids are
  slugs, so this is expected to stay theoretical; contract test 2 checks the space case.
- The extensions fixture in `Fixtures.kt` is written from the 0.1.21 schema, not copied from a live
  answer (the hub is not reachable from cloud sessions). Compare it with the real
  `GET /api/v2/extensions` during row 12.
