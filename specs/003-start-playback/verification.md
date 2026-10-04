# Verification: Start Playback

Results of the on-device checks in [quickstart.md](quickstart.md) §2, to be run by the user against
the real hub. §1 (build and tests) passes in the cloud session that implemented the feature.

**Build under test**: `0.3.0-alpha`, CI build ⏳, commit ⏳ (Settings footer "Sonora 0.3.0 - Alpha - Build N (sha)").

Status: ✅ passed · ❌ failed · ⏳ not checked yet · n/a not checkable on device

Rows 5 and 13 check hub assumptions. If either fails, record it here and ask before changing
behaviour (consequence line for row 5, timeout match for row 13).

| # | Check | Status | Date | Notes |
|---|---|---|---|---|
| 1 | Rooms → "Play something": nothing selected, Play reads "Play" and is disabled | ⏳ | | |
| 2 | Idle room → "Play something in <room>": that room is selected | ⏳ | | |
| 3 | Stream + idle room → Play: Now Playing within ~3 s; Back → Rooms shows it | ⏳ | | |
| 4 | A different source on that busy room: "<first> will stop in <room>" shown before Play; only it stops | ⏳ | | |
| 5 | ⚑ hub assumption: with a group playing, select one of its rooms: warning names the group, and after Play the whole group playback stops | ⏳ | | |
| 6 | The source already playing on exactly that target: "<source> is already playing in <target>"; Play opens Now Playing for the same playback | ⏳ | | |
| 7 | Paste `soundcloud.com/<artist>/<track>` → target → Play: "Starting…" while the hub resolves, then Now Playing | ⏳ | | |
| 8 | A link the hub cannot reach or a non-media page: screen stays, link and target kept, message per FR-016 | ⏳ | | |
| 9 | Type `hello world`: no message while typing; leave the field → "Enter a web address (https://…)"; Play disabled | ⏳ | | |
| 10 | Turn a group member off, select the group: "Won't play in <room> (turned off)" | ⏳ | | |
| 11 | Cut the phone's Wi-Fi with the screen open: stale banner, selections kept, Play disabled; restore → Play re-enables | ⏳ | | |
| 12 | Play on a link and Close at once: Rooms; the playback appears, or a failure message shows there | ⏳ | | |
| 13 | ⚑ hub assumption: link timeout recovery with a slow link. When the 30 s answer is lost but the hub plays it, Now Playing opens. Does the runtime input keep the exact address sent? | ⏳ | | |

## Open points

- A source that already plays as an announcement on or overlapping the chosen target: whether the
  hub returns that playback or refuses cannot be checked until a configured source declares
  `DUCK_OTHERS` as its default. Record it here when one does.

## Issues found

None so far.
