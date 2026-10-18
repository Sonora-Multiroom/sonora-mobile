# Full app test

A regression run of the whole app on a real phone against the real hub. Every feature's
`quickstart.md` checks only what that feature added, at the time it shipped. This script checks
what the app does **today**, across every shipped feature, in one pass.

**Covers features**: 001–006 (`0.6.0-alpha`) · **Hub API**: 0.1.25 · **Last updated**: 2026-10-18

Run it when the user asks for a "full app test", before a release, after a hub upgrade, and after
a dependency update that touches Compose, Ktor or the generated client.

## How to run it

- Run only from a **local session**: the hub and the phone are not reachable from the cloud.
- Follow [AGENTS.md](../../AGENTS.md) "On-device verification" for adb, the phone, keeping it awake
  and the safety rules for the hub. This script does not repeat them; they apply to every step.
- The hub is the user's real system and plays on speakers in their home. Before the first start,
  tell the user that the run will replace what is playing, and wait for their go-ahead.
- Taps: try `input tap` once. If the phone refuses it (the POCO does unless "USB debugging
  (Security settings)" is on), ask the user to make each tap with AskUserQuestion, then read the
  screen and the hub yourself.
- Before **every** tap, swipe, key or text input, check that Sonora is the foreground app
  (`dumpsys activity activities | grep topResumedActivity` names `sonora.multiroom.mobile`), and
  send nothing if it is not. A notification once dropped over the app and an adb tap opened a
  messaging app, which then received the keystrokes meant for a text field. Do Not Disturb (P3)
  prevents that; the check catches anything else.
- Reading the screen: dump it again before every tap. Coordinates from an older dump go stale
  when the keyboard opens or the list scrolls, and a stale tap can land on the keyboard or in
  another app. Buttons change their label with the selection ("Move to <room>", "Play <source>
  in <target>", "Play link in <target>"), so look them up by position or by prefix, not by the
  bare "Move" / "Play". A disabled button shows as `enabled="false"` on its clickable parent
  node; the text node inside it stays `enabled="true"`.
- Messages ("Playback on … ended", "The hub couldn't play this link") are temporary notices that
  disappear after a few seconds, and a dump takes ~2.5 s. To be sure of seeing one, capture
  `screencap` frames in a loop on the phone and read the frames.
- Hub requests: the hub logs one "Listing routes" line per app poll, so Loki
  (`{app="multiroom-core"} |= "Listing routes"`, endpoint in `~/.config/.loki/loki.json`) counts
  polls for R3. The hub does not log volume changes; for R4 the evidence is the members' ratio
  (the group volume call would set them equal).
- Checks marked **User** need the user's hands or ears (Wi-Fi cut, listening, TalkBack). Ask the
  user, or mark them `n/a` if they decline.
- Work through the sections in order: later sections assume the state earlier ones leave. If a
  check fails, record it with its evidence and carry on. Stop only for a contract change (P2) or
  something that would leave the hub in a bad state.
- Retry a failed start with a known-good source before calling it an app bug (AGENTS.md "Known hub
  behaviour").
- Record the results as described in [Report](#report).

Notation: `$HUB` = the hub address, e.g. `http://multiroom.lan:8080`; `curl` calls go to
`$HUB/api/v2/...`. "Within a refresh" means within ~3 s (polling every 2.5 s).

## Fixture

What the hub needs for full coverage. Check it in P4 and map each role to a real id; write the map
into the report. Where the hub lacks a role, mark the rows that need it `n/a`.

| Role | Needs | Used by |
|---|---|---|
| `GROUP` | a group of two rooms, `G1` and `G2` | R4, R10–R12, N5–N7, N12–N14, S5, S14, ST6–ST7, ST18 |
| `SOLO` | a room in no group, or in a group other than `GROUP` | R1, N7, ST5 |
| `STREAM` | a known-good `http(s)` stream (KissFM) | most rows |
| `STREAM2` | a second known-good stream (KissFM 2.0 Deep) | S4, N7 |
| `PAUSEABLE` | a configured `file:` source (`oceanwavescrushing`); may be off on the hub, then turn it on for the run and off again in teardown. The sample lasts ~2 min, after which the hub removes its route: start it just before each row that needs it | R5, N3, N8, ST10 |
| `LINEIN` | a line-in source, if the hub has one (Chromecast Audio, `alsa://…`, usually off: turn it on for N2 and off right after) | N2 |
| `LINK` | `soundcloud.com/forss/flickermood` (plays ~3 min, then the hub removes it) | S7, ST11–ST12 |
| `SLOWLINK` | `http://10.255.255.1/stream.mp3` (refused after ~10 s) | S8, S12 |

## P. Preflight

| # | Do | Expect |
|---|---|---|
| P1 | `./gradlew :androidApp:assembleDebug :composeApp:testAndroidHostTest :composeApp:allTests :composeApp:check > $TEMP/full-test-build.log 2>&1; echo $?` | Exit code 0. Note the number of tests (`composeApp/build/test-results/`) and compare it with the last report: a drop means tests were lost |
| P2 | `./gradlew refreshOpenApi` then `git diff --stat api/openapi.json` | No change. If it changed, **stop**: tell the user the hub contract moved (Constitution I) and update this script's expectations before testing. Then `git checkout api/openapi.json` unless the user wants to keep it |
| P3 | `adb devices -l`; pick the phone the user named (default: the POCO); check the lock; save and raise the screen timeout; save `settings get global zen_mode` and turn on Do Not Disturb with `cmd notification set_dnd none` | One device chosen; unlocked; `zen_mode` 2, so no notification can drop over the app and take a tap |
| P4 | Snapshot the hub into the scratchpad: `outputs`, `groups`, `inputs` (all `?includeDisabled=true`), `routes`, `master-mute` | Saved. Fill the fixture map. Note every output's volume, mute and on/off, every group's and input's on/off, and what plays |
| P5 | Tell the user what plays now and that the run will stop it; on their go-ahead, set every output's volume to 1 | Volumes at 1 |
| P6 | Install the build under test (`adb install -r` the APK from P1, or the CI build the user names); `dumpsys package sonora.multiroom.mobile \| grep versionName` | The expected version; map a CI build to its commit with `gh run list` |

## F. First run and hub address (001, 004)

| # | Do | Expect |
|---|---|---|
| F1 | Note the saved hub address, then `adb shell pm clear sonora.multiroom.mobile` and open the app | Rooms: "Set your hub address" with "Open Settings" |
| F2 | "Open Settings" | Settings with the address sheet already open; the hub row behind it reads "Not set" |
| F3 | Type `multiroom lan` | Refused (spaces); neither tests nor saves |
| F4 | Test `https://example.com:443` | "Can't reach the hub at this address" (a non-hub reply) |
| F5 | Test `10.255.255.1` | Same message after ~3 s |
| F6 | Type a host without scheme or port (`multiroom.lan`), test | Normalised to `http://…:8080`; "Hub found · N rooms", N = every room including off ones |
| F7 | Type a wrong address, Close; reopen the sheet | Nothing saved; the sheet shows the saved address (still unset here, then the real one after F8) |
| F8 | Enter the real address, Save | Sheet closes; hub row "Connecting…" then "Connected"; Rooms loads |
| F9 | Kill the app (`am force-stop`) and reopen | Rooms loads without asking |
| F10 | Settings footer | `Sonora <version> - Alpha - Build <n> (<sha>)` matching P6 |

## R. Rooms (001, 006)

| # | Setup (`curl`) | Do | Expect |
|---|---|---|---|
| R1 | `STREAM` on `SOLO`; one room off if the hub has one | Compare Rooms with `GET /routes` and `/outputs` | One card per playing room; group cards list members; Idle section; off rooms "Off", unplugged ones "Not connected"; "N of M rooms in use" right |
| R2 | — | Change a volume and start a route by `curl` | The screen follows within a refresh |
| R3 | — | Home for 30 s, then back. **User or logs**: hub access log or Loki | No `/api/v2` requests while in the background; immediate refresh on return. `n/a` if neither source is available |
| R4 | `STREAM` on `GROUP`, members at different volumes (e.g. 4 and 2) | Drag the group pill | Members keep their ratio (`GET /outputs`); no `PUT /groups/{id}/volume` |
| R5 | `PAUSEABLE` on a room | Pause, then Resume on its card | Card "Paused", then playing; hub `paused` follows |
| R6 | `STREAM` playing | Card status | "Live stream"; Stop only, no Pause |
| R7 | One output muted by `curl` | Master mute on, then off | On: every pill frozen, "Unmute all rooms". Off: the muted output is still muted, others not (hub 0.1.23) |
| R8 | Every room busy | Scroll Rooms | "Play something" is still there (003 issue 1) |
| R9 | — | Tap each tab, a card, an idle room's play button, "Play something"; Back from each | Settings, Now Playing, Start Playback with that room selected, Start Playback empty; Back returns to Rooms |
| R10 | `STREAM` on `GROUP`; then `PUT /outputs/G1/enabled {false}`; `GET /routes` shows `outputs: [G2]` | Rooms | The card lists `G2` only, its pill shows `G2`'s volume; "N of M rooms in use" leaves `G1` out; `G1` under "Off" |
| R11 | as R10, `G1` and `G2` at volume 1 | Drag the group pill to ~5 % | Only `G2` changes (`GET /outputs`); `G1` keeps 1 |
| R12 | as R10, then `PUT /outputs/G1/enabled {true}` | Wait one refresh on Rooms, then on Now Playing | Both rooms shown again, in the group's order (not the order they joined), with `G1`'s pill |

## N. Now Playing and Move to room (002, 006)

| # | Setup (`curl`) | Do | Expect |
|---|---|---|---|
| N1 | `STREAM` on `GROUP` | Open its card | Title, "Stream · <host>", status chip, "on <group> · G1 + G2" |
| N2 | `LINEIN` playing, if available | Open it | Stop only; no "Live stream" badge, no "Live streams can't be paused". The `STREAM` route shows both |
| N3 | `PAUSEABLE` on a room | Pause, Resume, Stop | Stop returns to Rooms; the room is idle |
| N4 | Now Playing open | Stop the route by `curl` | Back on Rooms within ~3 s with "Playback on <target> ended" |
| N5 | `STREAM` on `GROUP` | Drag the group pill; drag `G1` only; mute and unmute the group | Ratio kept; only `G1` changes and the group pill shows the new loudest; mute follows on the hub. Then master mute on in Rooms → back here: pills frozen, mute disabled, "All rooms are muted"; master mute off |
| N6 | `STREAM` on `GROUP`, `STREAM2` on `SOLO`, a room off (with four rooms that leaves none idle; the "Idle" note is then seen in N7) | "Move to room…" | Nothing preselected, Move disabled. Notes: idle "Idle", busy "<STREAM2> will stop", "<G1> only" → "<G2> stops", the off room dimmed, group notes. If the room off is a member of `GROUP`, the other member's "only" row reads "Nothing stops" instead (see N13) |
| N7 | as N6 | Move to an idle room, to the busy room, to a group, then "<room> only" | After each: Now Playing shows the new target within ~3 s without going back to Rooms; the busy room's playback stopped as announced; `routeId` unchanged in `GET /routes` (0.1.22) |
| N8 | `PAUSEABLE` playing, transferable | Pause | "Move to room…" disappears |
| N9 | Sheet open | Cancel; swipe down; Back | Each closes it with no change on the hub |
| N10 | — | On Rooms drag a pill, release, within 1 s tap Settings then Rooms | The pill does not jump back before the hub confirms |
| N11 | After a move | Home, `am kill`, check `pidof` (kill -9 via `run-as` if needed), reopen from recents | Now Playing for the moved playback on its new target, no "ended" message |
| N12 | as R10 (`G1` off while `STREAM` plays on `GROUP`) | Open the card | "on <group> · <G2>"; pills `GROUP` and `G2` only |
| N13 | as N12 | "Move to room…"; then pick "<G2> only" → Move | "<G2> only" reads "Nothing stops" in the plain grey note (not amber); "<G1> only" reads "Turned off"; `G1` is listed once. The move is accepted, the sheet closes, music keeps playing on `G2`, Now Playing reads "on <G2>". Turn `G1` back on |
| N14 | `STREAM` on `GROUP`, both members on | Settings → Rooms: turn `G1` off ("Turn off"), then on; go straight to Now Playing → "Move to room…" → an idle room → Move. Repeat 3 times | Each move succeeds with no message (a refusal while `G1` rejoins is retried once after ~1 s). If the rejoin is always too fast to hit, record "not reached": the view-model tests cover the retry |
| N15 | `STREAM` on a room, Move sheet open with an idle room `B` selected; then `PUT /outputs/B/enabled {false}` | Move | "<B> is turned off"; the sheet closes; the next sheet shows `B` "Turned off". Turn `B` back on |

## S. Start Playback (003, 006)

| # | Setup (`curl`) | Do | Expect |
|---|---|---|---|
| S1 | — | Rooms → "Play something" | Nothing selected; Play disabled |
| S2 | — | Idle room → its play button | That room selected |
| S3 | — | `STREAM` + an idle room → Play | Now Playing within ~3 s; Back → Rooms shows it |
| S4 | as S3 | Pick `STREAM2` + the same room | "<STREAM> will stop in <room>" before Play; after Play only that stops |
| S5 | `STREAM` on `GROUP` | Select `G1` with another source | Warning names the group; after Play the whole group playback stops |
| S6 | `STREAM` on `SOLO` | Select `STREAM` + `SOLO` | "<STREAM> is already playing in <SOLO>"; Play opens the same playback |
| S7 | — | Paste `LINK` → a room → Play | "Starting…", then Now Playing |
| S8 | — | Paste `https://example.com/` → Play; then `SLOWLINK` | Screen stays, link and target kept, a plain message each time |
| S9 | — | Type `hello world`, leave the field | No message while typing; then "Enter a web address (https://…)"; Play disabled |
| S10 | `G1` off | Select `GROUP` | "Won't play in <G1> (turned off)"; turn `G1` back on |
| S11 | — | Play on `LINK`, Close at once | Rooms; the playback appears, or a failure message shows there |
| S12 | — | Play on `SLOWLINK`, Close at once | Rooms; ~10 s later the failure message shows on Rooms |
| S13 | Start Playback open with an idle room `R` selected; then `PUT /outputs/R/enabled {false}` | Play `STREAM` | "<R> is turned off"; `R` shows as off right after. Turn `R` back on |
| S14 | Start Playback open on `GROUP`; then `PUT /groups/GROUP/enabled {false}`. Then the same with the group on and both members off | Play | "<GROUP> is turned off"; afterwards `GROUP` is off in the list. With both members off: "All rooms in <GROUP> are turned off". Turn everything back on |
| S15 | Start Playback open with `STREAM` selected; then `PUT /inputs/STREAM/enabled {false}` | Play | "<STREAM> is turned off"; `STREAM` leaves the source list. Turn it back on |

## ST. Settings (004, 005, 006)

**Automated**: ST01–ST16 run as Maestro flows, one per row, in
[scripts/full-test/flows/settings/](../../scripts/full-test/flows/settings/):
`scripts/full-test/run.sh --device <serial> --tags settings` (gates, snapshot, Do Not Disturb and
teardown included; Maestro CLI in `~/.maestro`). Record its JUnit result per row in the report and
recheck failures by hand. ST17 and ST18 stay manual. When a row below changes, change its flow too.

| # | Setup (`curl`) | Do | Expect |
|---|---|---|---|
| ST1 | — | Bottom bar → Settings | Hub row "Connected" + address; Rooms tab; each room's status line right |
| ST2 | — | Bottom bar → Sources | Settings on the Sources tab; Settings highlighted |
| ST3 | — | Groups tab → leave to Rooms → back | Reopens on Groups |
| ST4 | An idle room | Turn it off, check Rooms, turn it on | No dialog; `enabled:false` within a refresh; Rooms shows "Off"; back on |
| ST5 | `STREAM` on `SOLO` | Row reads "Playing · <STREAM>" in amber; turn off → "Keep playing"; again → "Turn off" | Keep: nothing changes. Turn off: room off, playback gone on Rooms. Turn it back on |
| ST6 | `STREAM` on `GROUP` | Turn `G1` off → "Turn off"; then on again | Plays on in `G2`: the route's `outputs` in `GET /routes` lists only `G2` (by ear if the user is listening). Back on: `G1` rejoins the same route id, `outputs` lists both (hub 0.1.25) |
| ST7 | `STREAM` on `GROUP` | Turn `GROUP` off → "Turn off"; then on again | Group dialog lists members; the playback stops (hub 0.1.25) |
| ST8 | `STREAM` on a room | Sources: row "Playing in <room>" in amber; flip off → "Keep playing" | Dialog "Turn off <STREAM>? / <STREAM> is playing in <room>. Turning it off stops playback there."; Keep: switch on, still playing |
| ST9 | `STREAM` on a room and on a group without that room | Flip off → "Turn off" | Dialog names both; playback gone; no "keeps playing" message. Turn it back on |
| ST10 | `PAUSEABLE` on a room, paused | Flip off | The dialog appears |
| ST11 | Nothing plays `STREAM` | Flip off, then on | One tap each, no dialog, no message |
| ST12 | `LINK` played, stopped, another `LINK` played | Sources tab | "Added from apps" lists the playing one: "Added today HH:mm · removed when it stops" in phone time |
| ST13 | A stopped runtime source: `POST /inputs` `{"inputId":"fulltest-tmp","displayName":"Full test temp","uri":"https://online.kissfm.ua/KissFM","autoRemove":false}` | Trash | Gone at once, no dialog; not in `GET /inputs` |
| ST14 | `LINK` playing | Trash → dialog → "Remove" | Dialog names the room; playback stops; row gone |
| ST15 | — | Extensions tab | Every extension of `GET /extensions` with its badge and connection line |
| ST16 | — | Flip a room switch and tap Rooms at once | The change lands on the hub. Put the room back |
| ST17 | **User**: TalkBack on | A switch row, the tabs, the hub row, a trash button, a dialog | Each read once, with its state; no name read twice |
| ST18 | as R10 (`G1` off while `STREAM` plays on `GROUP`) | Settings → Rooms; then Groups → turn `GROUP` off → "Keep playing" | `G1` reads "Off"; the dialog says `STREAM` is playing on `G2` only. Turn `G1` back on |

## X. Connection loss (001, 002, 003, 004)

| # | Do | Expect |
|---|---|---|
| X1 | **User**: cut the phone's Wi-Fi on Rooms, wait 6 s, restore | "Can't reach the hub · showing last known state", controls disabled; live again within ~6 s of restoring. adb may need re-pairing |
| X2 | **User**: the same on Now Playing with the move sheet open, on Start Playback with selections, and on Settings | Sheet's Move disabled; selections kept and Play disabled; Settings "Not connected", switches and trash disabled, "Test connection" still answers. All re-enable after restoring |

Never stop the hub for these checks unless the user offers to: it stops every playback in the home.

## T. Teardown

1. Stop every route the run started (`DELETE /routes/{id}`), **before** touching volumes.
2. Restore every output's volume and mute, master mute, and every output, group and input's on/off
   to the P4 snapshot. Diff a fresh snapshot against it; only runtime inputs the hub has since
   removed may differ.
3. Restore the screen timeout and the Do Not Disturb mode (`zen_mode` from P3; 0 =
   `cmd notification set_dnd off`). Leave the build under test installed unless the user asks otherwise.

## Report

Write `docs/testing/runs/YYYY-MM-DD.md` (add `-2` for a second run that day), give the user its
summary, and commit it on the current branch as a commit of its own (if that branch is already
merged, on a new `docs/…` branch from an up-to-date `main` instead), e.g.
`docs(testing): record the full app test of <version> on <device>`, with no other changes in it.
Do not push unless asked. Reports are kept: the next run compares its test count (P1) and results
with the last one.

```markdown
# Full app test YYYY-MM-DD

**Build**: <version>, <CI build n | local debug>, commit <sha> · **Device**: <model>, <how driven>
**Hub**: API <version> · contract unchanged | changed (P2) · **Script**: last updated <date>
**Fixture**: GROUP = …, SOLO = …, STREAM = …, …
**Hub before / after**: volumes …, off items …; restored: yes | exceptions

Status: ✅ passed · ❌ failed · n/a not checkable (say why)

| # | Status | Evidence |
|---|---|---|
| P1 | ✅ | exit 0, 604 host tests |
| … | | |

## Issues found
App defects first (with the row, steps and screenshot path), then hub observations.
```

Each defect is a candidate for `docs/backlog/` (or a fix on the current branch if the user says so).
Hub-side findings belong in the `multiroom-ai` backlog: list them for the user, do not file them.

## Keeping this script current

A feature is not finished until this script covers what it delivered (Constitution, "Development
Workflow & Quality Gates"). In the feature's branch, before its pull request:

- **Add** rows for every user-visible behaviour in the feature's `quickstart.md` §2, in the section
  of the screen they belong to (a new screen gets a new section). Name the feature in the section
  heading.
- **Change** rows whose expectation the feature changes, and **delete** rows for behaviour it
  removes. The old feature's quickstart stays as history; this script describes the app as it is.
- Update the fixture when a row needs a new kind of source, room or link.
- Update the header line: features covered, version, hub API, date.
- When a hub upgrade changes behaviour (AGENTS.md "API"), update the affected rows in the same
  change that records the new API version.
