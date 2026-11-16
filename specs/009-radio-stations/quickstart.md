# Quickstart: validate Find and Play Radio Stations

Feature: [spec.md](spec.md) · Expected results: [contracts/radio-ui.md](contracts/radio-ui.md),
[contracts/hub-repository.md](contracts/hub-repository.md) · Model: [data-model.md](data-model.md)

## 1. Cloud / CI checks (no hub, no emulator)

Prerequisites: JDK 17+ and `ANDROID_HOME` set. `gradle.properties` shows
`sonora.versionName=0.9.0-alpha` and `sonora.versionCode=9`. `api/openapi.json` is 0.1.34.

```bash
./gradlew :androidApp:assembleDebug
./gradlew :composeApp:testAndroidHostTest
./gradlew :composeApp:allTests
./gradlew :composeApp:check
```

Expected: all green. `check` includes `verifyLayering`: no file under `ui/` or `domain/` names a
generated radio type. The suites and what they prove are listed in
[research R14](research.md#r14-testing).

## 2. On-device checks (local session, real hub)

Follow AGENTS.md "On-device verification": the POCO (phone width), volumes noted and set to 1
before the first start, Do Not Disturb on, Sonora in the foreground before every input, test
routes stopped before volumes are restored. Say before row 3 that the run replaces what plays in
`A` and `B`.

Needs: a hub at API 0.1.34 or later with the `radio` extension `ACTIVE`
(`curl $HUB/api/v2/extensions`), reachable internet on the hub, two rooms `A` and `B`, a
known-good configured stream `STREAM` (KissFM). Stations: MANGORADIO started in 2.4 s on
2026-11-16; if it does not start, take another well-voted MP3 station and note which. Start from a
fresh install or cleared app data for rows 1 and 2 (`adb shell pm clear sonora.multiroom.mobile`
removes the hub address too: enter it again).

Timings ("within 2 s", "after 30 s") are read from `screencap` frames with timestamps, never from
`uiautomator dump`. A row marked **User** needs the user's hands; mark it `n/a` if they decline.

| # | Setup | Action in the app | Expected |
|---|---|---|---|
| 1 | fresh app data, hub address set | Rooms → "Play something" | "Or a radio station" with "Find a radio station" between the link field and the sources, within one round trip of opening; no recent rows; everything else as before |
| 2 | as 1, phone region = a country the catalogue lists | "Find a radio station" | "Radio stations"; placeholders, then the Country chip shows that country and the heading "STATIONS IN <COUNTRY>" with "Most voted"; no "Recent" |
| 3 | — | type `mango` | no search button; within ~2 s of the last letter the list shows matching stations (remove the country chip if MANGORADIO is not from that country); "Recent" is not shown while text is typed |
| 4 | as 3 | look at rows; tap MANGORADIO | rows: name, "DE · MP3 · 128 kbps"-style line with only known parts, no votes or images. Tap: back on "Play something", MANGORADIO in the selected style with "Change", second line starts with "Radio"; nothing plays (`GET /routes` unchanged) |
| 5 | `STREAM` on `A` | select room `A` | "<STREAM> will stop in A"; Play reads "Play MANGORADIO in A" |
| 6 | as 5 | Play | "Starting…", then Now Playing within a few seconds: title MANGORADIO, subtitle "Radio" and nothing after it, Stop without Pause, "Live streams can't be paused". `GET /inputs`: an input with `uri` `radiobrowser://…` |
| 7 | as 6 | Back to Rooms | card: MANGORADIO, "Radio · Live stream", the stream tile |
| 8 | MANGORADIO on `A` for more than 30 s | "Play something" | MANGORADIO is the first row under "Find a radio station". "Find a radio station": "RECENT" lists it first |
| 9 | as 8 | select the recent MANGORADIO row, room `A` | "MANGORADIO is already playing in A"; Play dimmed and sends nothing. Room `B`: Play enabled |
| 10 | — | a second station on `B`; `DELETE` its route by `curl` after ~10 s; open the radio screen | the second station is **not** under "Recent" |
| 11 | — | a third station on `B`; press Home within 10 s; wait 40 s; reopen the app | the station still plays and is now first under "Recent" |
| 12 | a station playing less than 30 s on `B` | Now Playing → "Move to room…" → `A` (when the hub marks it transferable); wait until 30 s after the start | it enters "Recent" at 30 s from the start, not from the move |
| 13 | two or more recent stations | radio screen → ✕ on one "Recent" row | gone at once, no question asked; back on "Play something" it is gone there too and the next one took its place; a playing station keeps playing |
| 14 | — | Country chip → sheet; type `ger`; choose Germany | every country with a count, largest first; the field narrows it; the chip reads "Germany" with ✕; the list reloads; heading "STATIONS IN GERMANY" |
| 15 | as 14 | Tag chip → type `jazz` → choose "jazz"; then Language → choose "german" | the tag sheet finds tags by part of the name and loads more on scroll; three chips fit the width; the list shows only matching stations (compare with `curl "$HUB/api/radio/stations?countryCode=DE&tag=jazz&language=german&limit=5"`) |
| 16 | as 15 | order control → "Trending"; then "Name A–Z" | the control names the order; the list reloads in that order (compare the first rows with `curl … &order=clickCount` / `&order=name`) |
| 17 | as 16 | reopen a sheet with a value chosen | that row is in view and marked |
| 18 | as 16 | type `qzxvqzxv` | "No stations match", "Clear filters"; tapping it removes the three chips, keeps the order and the name |
| 19 | filters and an order chosen | `adb shell am force-stop sonora.multiroom.mobile`; reopen; open the radio screen | the same filters and order; the name field is empty; "Recent" still there |
| 20 | no filter, empty name | scroll the list down several pages | "Loading more stations…" appears and rows are added; no station appears twice (compare names in two dumps) |
| 21 | a chosen country with a long name (The United Kingdom Of Great Britain And Northern Ireland), tag and language chosen | look at the chips and the sheet | the country value ends in "…", the other chips stay in sight or the row scrolls sideways; in the sheet the long name ends in "…" and its count stays in place |
| 22 | a station with a long name selected (search `Old Fashioned Radio`) | look at Start Playback and the Play button | the selected name scrolls as a marquee; unselected station rows end in "…"; Play shows "Play ", the cut name and " in <room>" in full |
| 23 | type `100%` and 120 characters | look at the field and `GET` nothing by hand | `%` does not appear, the field stops at 100 characters, no error message appears |
| 24 | radio screen open, list loading or loaded | rotate; press Home and return | name, filters, list and position kept; no reload flash |
| 25 | MANGORADIO started by `curl -X POST $HUB/api/v2/play -d '{"uri":"radiobrowser://<uuid>","targetId":"A","targetType":"SINGLE_OUTPUT"}'` | Rooms, Now Playing, "Play something", Settings → Sources | "Radio · Live stream", "Radio" subtitle, kind "Radio" with the stream tile in the source list and under "Added at run time"; it does not enter "Recent" (not started from this phone) |
| 26 | — | select a station, a room, Play; Close at once | Rooms; the playback appears, or a failure notice shows there; nothing else appears later |
| 27 | a listed station the hub cannot start (try a few low-voted ones; `n/a` when every one starts) | Play | Start Playback stays, the station stays selected, a temporary notice "Couldn't start <station>" (read it from `screencap` frames), no cause; it is not under "Recent"; the room it was aimed at has fallen silent (the hub stops first) |
| 28 | **User**: block the hub's internet access (or `n/a`) | open the radio screen; Retry; restore access; Retry | "The station catalogue isn't answering" with Retry within ~6 s, never "Can't reach the hub"; after restoring, Retry loads the list |
| 29 | **User**: cut the phone's Wi-Fi on the radio screen | look; restore Wi-Fi | the stale banner, rows stay, no catalogue card; the list loads again by itself after reconnecting (adb may need re-pairing) |
| 30 | **User**, TalkBack on | focus a station row, a chosen chip's ✕, the order control, a "Recent" ✕, the selected station row in Start Playback | "<name>, <details>"; "Remove the country Germany"; "Order: Most voted. Change the order"; "Remove <name> from Recent"; "<name>, selected. Choose another station" |
| 31 | a hub without the `radio` extension, if one is at hand (else `n/a`; covered by `StartPlaybackViewModelTest`) | "Play something" | no "Or a radio station" section, no error, recent stations not listed |

At the end: stop the test routes, restore volumes, restore screen timeout and Do Not Disturb.
Record each row's evidence in `verification.md`.
