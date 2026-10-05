# Verification: Settings Tabs and Sources

Results of the on-device checks in [quickstart.md](quickstart.md) §2, to be run locally against the
real hub. §1 (build and tests) passes in the cloud session that implemented the feature.

**§2 waits for the hub release** (plan "Open points": stopping playback when a room or group is
turned off) and starts with `./gradlew refreshOpenApi`. If the contract changed, reconcile it
before running the rows (Constitution I) and ask before changing behaviour. Rows 5–7 can only pass
after the release; the others can run before it.

**Build under test**: CI build 27, commit `4f5dd24` (`0.4.0-alpha+27`), run on 2026-10-05. Hub API
0.1.21; `./gradlew refreshOpenApi` showed no contract change.
**Device**: Pixel 5, wireless adb, driven by Claude Code (`input tap`, `uiautomator dump`,
`screencap`); rows 16 and 18 and the by-ear parts of row 6 by the user. The POCO could not be
driven, see Issues found. · All outputs at volume 1 during the run; test routes stopped before the
volumes were restored (Office 13, Kitchen 41, Bedroom 7, Bathroom 50); everything switched off
turned back on.

Status: ✅ passed · ❌ failed · ⏳ not checked yet · n/a not checkable on device

| # | Check | Status | Date | Notes |
|---|---|---|---|---|
| 1 | Bottom bar → Settings: Hub row "Connected" + address; tabs; Rooms tab selected; every room listed with the right status line (SC-003) | ✅ | 2026-10-05 | Fresh install opened on the Rooms tab. Bathroom "Off"; Bedroom "In Broadcast to all rooms"; Kitchen and Office "In Broadcast to all rooms, Living Room"; A→Z |
| 2 | Bottom bar → Sources: Settings on the Sources tab; Settings highlighted | ✅ | 2026-10-05 | Screenshot: Sources tab selected, Settings amber in the bottom bar |
| 3 | Groups tab, Rooms tab, leave to Rooms, come back: reopens on the last tab (FR-002) | ✅ | 2026-10-05 | Groups → Rooms screen → Settings reopened on Groups; the same for the Rooms tab. Group members in hub order |
| 4 | Turn an idle room off: no dialog; switch moves at once; `curl` shows `enabled:false` within 3 s; Rooms shows it "Off" (SC-001, SC-002); turn it back on | ✅ | 2026-10-05 | Bedroom: no dialog, `enabled:false` 0.49 s after the tap, Rooms "Turned off · Off"; turned back on |
| 5 | ⚑ hub release: play on the lone room, its row reads "Playing · <source>" in amber; turn it off: dialog with the room text; "Keep playing" changes nothing; "Turn off" → room off and its playback gone on Rooms | ✅ | 2026-10-05 | Every enabled room is in a group, so KissFM played on Bedroom alone (single-output route). Row "Playing · KissFM"; dialog "Turn off Bedroom Speakers? / KissFM is playing in Bedroom Speakers. Turning the room off stops playback there."; Keep playing: route ACTIVE, room on; Turn off: route gone within 1 s, Rooms idle. The hub already does this at 0.1.21 |
| 6 | ⚑ hub release: play on the group, turn one member off: dialog for the room; after "Turn off" the group plays on in the other member (by ear), the room is off; turn it back on: the hub does not add it back (Rooms may still count it, known hub gap) | ✅ | 2026-10-05 | KissFM on Living Room, Office off: dialog for Office Speaker; group route stayed ACTIVE; user heard Kitchen only. Office back on: still silent (user); the app shows it "Playing · KissFM" and Rooms "2 of 4 rooms in use" (known gap) |
| 7 | ⚑ hub release: with the group playing, turn the group off: group dialog with members; after "Turn off" the group playback stops | ❌ | 2026-10-05 | App side correct: dialog "Turn off Living Room? / KissFM is playing on Office Speaker, Kitchen Speakers. Turning the group off stops playback in all of these rooms.", group `enabled:false`. **Hub side missing**: the group route stayed ACTIVE (checked after 11 s) and Rooms kept showing it. Waits for the hub change for groups. **Accepted for release** by the user on 2026-10-05, see Issues found |
| 8 | Turn a playing source off: no dialog; message "<name> is off. What's playing from it keeps playing."; it keeps playing; turn it back on | ✅ | 2026-10-05 | KissFM, playing on Living Room: no dialog, message as specified (screenshot), route stayed ACTIVE; turned back on |
| 9 | Play a link, stop it (auto-removed), play another; Sources tab: "Added from apps" lists the playing one with "Added today HH:mm · removed when it stops" in phone time | ✅ | 2026-10-05 | `soundcloud.com/forss/flickermood` via `curl /play` twice on Bedroom; the second replaced the first, whose input the hub removed. Only the playing one listed: "Added today 11:18 · removed when it stops" (hub 08:18Z, phone EEST) |
| 10 | Remove a stopped runtime source (a DLNA source, or `curl -X POST /api/v2/inputs`): gone at once, no dialog; `curl` no longer lists it | ✅ | 2026-10-05 | `sonora-test-r10` from `curl`: listed first (newest) as "Added today 11:18"; trash → gone at once, no dialog, hub no longer lists it |
| 11 | Remove the playing link: Remove dialog naming the room; "Remove" → playback stops, row gone | ✅ | 2026-10-05 | Dialog "Remove Playback (…)? / … is playing in Bedroom Speakers. Removing it stops playback there."; Remove → no routes, input gone, the section shows its empty text |
| 12 | Extensions tab: every extension of `GET /api/v2/extensions` with "Active" and the right connection line; stop the MQTT broker (if possible) → "Disconnected" within a refresh | ✅ | 2026-10-05 | All five match the hub: Chromecast, DLNA, MQTT "Connected"; REST, TTS "No connection needed"; all "Active". The MQTT broker was not stopped, so "Disconnected" is unchecked on the device |
| 13 | Hub row → sheet; test `https://example.com:443`, `10.255.255.1` and the real address: "Can't reach the hub at this address" (a non-hub reply); the same after ~3 s; "Hub found · N rooms" with N = all rooms incl. off | ✅ | 2026-10-05 | example.com:443 → "Can't reach…"; 10.255.255.1 → "Checking…" at 2.2 s, "Can't reach…" by 3.5 s (screencap frames); real address → "Hub found · 4 rooms" (incl. the off Bathroom) |
| 14 | Type a wrong address, Close: nothing saved; reopen shows the saved address | ✅ | 2026-10-05 | `http://wrong.lan:1`, Close: hub row unchanged; reopened sheet shows the saved address |
| 15 | Save a new valid address, then back to the real one: sheet closes; status goes "Connecting…" then follows; Rooms uses it | ✅ | 2026-10-05 | Saved `http://192.168.1.111:8080`: sheet closed, "Connecting…" with the extensions list cleared at 0.2 s, "Connected" by 1.2 s; Rooms loaded. Saved the original address back: Connected |
| 16 | Cut the phone's Wi-Fi with Settings open (user): "Not connected", stale banner, lists kept, switches and trash disabled, "Test connection" still answers; restore → re-enabled | ✅ | 2026-10-05 | Run by the user on the Rooms tab: every point as described. No runtime source was listed, so the trash state was not seen |
| 17 | Flip a switch and switch to Rooms at once: the change lands; a failure (e.g. hub stopped) shows as a message on Rooms | ✅ | 2026-10-05 | Bedroom switch + Rooms tap in one adb call: hub `enabled:false`, Rooms "Turned off"; turned back on. The failure path was not checked (the hub was not stopped) |
| 18 | TalkBack over a switch row, the tabs, the hub row, a trash button and the dialog: announcements as in FR-023 | ✅ | 2026-10-05 | User heard: "On, Bedroom Speakers, Bedroom Speakers, Playing KissFM, Switch"; "Groups, tab, 2 of 4"; "Hub connection: Connected. http://multiroom.lan:8080, Change address, Button"; "Remove Sonora test source, Button"; the dialog worked. Name read twice on switch rows, see Issues found. Fixed in `e895010`; rechecked with a local debug build of it: rooms, groups and sources rows read the name once |

## Open points

- The dialog scrim is the platform's dim, not exactly `rgba(5,6,8,0.72)` (plan "Open points").
  On the Pixel 5 it looked right (screenshots of rows 5 and 11).
- A runtime source without `createdAt` drops the "Added <when>" part and sorts last (defensive; the
  hub always sets it).

## Issues found

- **Switch rows announce the name twice** (row 18): TalkBack reads "Bedroom Speakers, Bedroom
  Speakers, Playing KissFM". The row merges its descendants and also sets
  `contentDescription = name`
  ([SettingRows.kt:89](../../composeApp/src/commonMain/kotlin/sonora/multiroom/mobile/ui/settings/SettingRows.kt#L89)),
  so both the description and the name text are read. Minor; FR-023 is otherwise met.
  **Fixed** in `e895010` (description dropped; `toggleable` already merges the texts), rechecked
  with TalkBack on the Pixel 5.
- **Hub (observation)**: turning a group off does not stop its playback (row 7); turning a room off
  does (rows 5, 6). The hub still reports API 0.1.21.
- **Known issue at release (row 7, accepted 2026-10-05)**: the feature ships before the hub change
  for groups. Until it lands, turning a playing group off shows "Turning the group off stops
  playback in all of these rooms", but the playback goes on; the group is off (nothing new can
  start on it), Rooms keeps showing the playback and Stop ends it. Turning a room off and the
  source switches are not affected. As the spec's Dependency section allows, the app has no
  version check or fallback wording; the dialog becomes true once the hub change ships, with no
  app change. Re-run row 7 then.
- **Test device (observation)**: the POCO (HyperOS 2) refuses `input tap` and `settings put` over adb
  unless Developer options → "USB debugging (Security settings)" is on, which needs a Mi account.
  The run used the Pixel 5.
- During row 6 the app was once found on Rooms with Start Playback open, without an adb tap that
  explains it (the user was handling the phone while listening). Not reproduced.

Observations from the cloud session, not defects:

- The generated client splits its paths on `/`, so a hub id containing a slash cannot be addressed
  by any call (this applies to the 001–003 calls as well). Ids with spaces are encoded. Hub ids are
  slugs, so this is expected to stay theoretical; contract test 2 checks the space case.
- The extensions fixture in `Fixtures.kt` is written from the 0.1.21 schema, not copied from a live
  answer (the hub is not reachable from cloud sessions). Compared during row 12: same shape; the
  live answer sends `rejectionReason: null` explicitly and range-style `requiredApiVersion`
  (`[0.1.21,0.2.0)`), both shown correctly on the device.
