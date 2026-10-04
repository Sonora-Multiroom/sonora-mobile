# Verification: Now Playing and "Move to room…"

Results of the on-device checks in [quickstart.md](quickstart.md) §2, run by the user against the
real hub. §1 (build and tests) passed in CI (T040).

**Build under test**: `0.2.0-alpha`, CI build 13, commit `5e0df92` (Settings footer
"Sonora 0.2.0 - Alpha - Build 13 (5e0df92)").

Status: ✅ passed · ❌ failed · ⏳ not checked yet · n/a not checkable on device

| # | Check | Status | Date | Notes |
|---|---|---|---|---|
| 1 | Install, footer shows `0.2.0-alpha` | ✅ | 2026-10-04 | Footer also shows CI build and commit |
| 2 | Open each card: title, kind · detail, chip, "on …" | ✅ | 2026-10-04 | |
| 3 | Live stream vs line-in | ✅ | 2026-10-04 | Line-in (Chromecast on line-in): "Line-in", green icon, Stop only, no live line. LoungeFM: "Stream · <host>" (no scheme or port), Stop only, "Live streams can't be paused", "LIVE STREAM" badge on the panel |
| 4 | Pause / resume / Stop | ✅ | 2026-10-04 | Stop returns to Rooms, the room becomes idle, no "ended" message |
| 5 | Ended elsewhere → "Playback on <target> ended" | ✅ | 2026-10-04 | Stopped via `DELETE /api/v2/routes/{id}` with Now Playing open |
| 6 | Volume: group drag keeps ratio, member drag, group mute, master mute | ✅ | 2026-10-04 | |
| 7 | Move sheet notes | ✅ | 2026-10-04 | |
| 8 | Moves | ✅ | 2026-10-04 | See below |
| 9 | "Move to room…" hidden while paused | ✅ | 2026-10-04 | Disappears on pause, returns on resume |
| 10 | Hub down with the sheet open; Cancel / swipe / Back | ✅ | 2026-10-04 | Hub stopped: sheet stays open, Move disabled, stale banner on Now Playing. Hub restarted: it came back with no routes, so the sheet closed and the app returned to Rooms with the "ended" message (FR-002). Message read "Playback on <room> ended". Wi-Fi off for a few seconds with the sheet open instead (the hub keeps its routes): Move disabled while offline; when the hub was reachable again the sheet was still open and Move became active again. Cancel, swipe down and Back each close the sheet with no change. |
| 11 | Tab round trip keeps the dragged volume | n/a | 2026-10-04 | Not reproducible on device: returning to Rooms refreshes at once and the hub answers fast, so the old value never shows. Covered by VolumeDragControllerTest and AppBackStackTest |
| 12 | Restore after a move and `am kill` | ✅ | 2026-10-04 | Pixel 5 (redfin): Home, `am kill` (pid 25934 gone), reopened from recents (new pid 26617). Now Playing showed the moved playback on its new room, no "ended" message |

## Step 8 details

- ✅ Group → one member via "<Room> only": Now Playing follows the moved playback, with no
  "ended" message and no return to Rooms.
- ✅ Onto a busy room: KissFM 2.0 Deep moved from Office to Kitchen. The Kitchen's KissFM
  stopped as announced, and Office became idle.
- ✅ Back to an idle room: Kitchen → Office.
- ✅ Onto a busy room again: Office → Living Room while Lounge FM played there as a single-room
  route. The sheet said "Lounge FM will stop", and afterwards only the moved route was left.
- ✅ Room → group.

## Issues found

None so far.
