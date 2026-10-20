# Quickstart: validate Scroll Long Source Names

Feature: [spec.md](spec.md) · Expected results: [contracts/ui-behaviour.md](contracts/ui-behaviour.md)

## 1. Cloud / CI checks (no hub, no emulator)

Prerequisites: JDK 17+ and `ANDROID_HOME` set. `gradle.properties` shows
`sonora.versionName=0.7.0-alpha` and `sonora.versionCode=7`. `api/openapi.json` is 0.1.28.

```bash
./gradlew :androidApp:assembleDebug
./gradlew :composeApp:testAndroidHostTest
./gradlew :composeApp:allTests
./gradlew :composeApp:check
```

Expected: all green. No existing suite changes, since no domain or view-state logic changes.

| Suite | Proves |
|---|---|
| `MarqueeTextTest` (new) | `marqueeLayout`: fits → `Fits` whatever `canScroll`; overflows + can't scroll → `Truncated`; overflows + can scroll → `Scrolling`. `canScroll`: false when stale, when unfocused, when the scale is 0; true at scale 1 and at 0.5 with everything clear. `marqueeStartDelay`: 0 → 1 200, 1 → 1 800, 2 → 2 400, 3 → 3 000, 4 → 1 200 |

## 2. On-device checks (local session, real hub)

Follow AGENTS.md "On-device verification": the POCO, volumes noted and set to 1 before the first
start, Do Not Disturb on, test routes stopped before volumes are restored, the phone's animation
settings restored at the end.

**Long names without depending on a link's title**: rename a configured stream that starts
reliably (KissFM) with `PUT /api/v2/inputs/{id}/display-name`
`{"displayName": "KissFM — The Deep House Session with a very long title (Live)"}` (64 characters
at most) and restore it at the end with `{"displayName": null}`. For a second long name, rename
Radio NV the same way. A played link may also be named directly:
`POST /api/v2/play {uri, targetId, targetType, displayName: "…"}`.

| # | Setup (`curl` on `/api/v2`) | Action in the app | Expected |
|---|---|---|---|
| 1 | KissFM (long name) on room `A`; Radio NV (original short name) on room `B` | Rooms | `A`'s source line shows its start, still for ~1.2–3 s (depending on the card's position), then scrolls right to left, pauses, repeats (record `screencap` frames with timestamps); no "…"; edges fade; the text never runs under the Stop button. `B`'s name is still, no "…" (SC-005) |
| 2 | as 1 | watch `A` for one minute | the name never jumps back mid-cycle although the app refreshes (SC-003); it keeps cycling after 3 cycles |
| 3 | as 1 | rename KissFM to another long name by `curl` | at the next refresh `A` starts again from the beginning of the new name |
| 4 | as 1; then rename KissFM to 64 characters of short words, e.g. "Up all night to the deep blue sea and back home again by the sea", and first confirm with `animator_duration_scale 0` and a screenshot that it needs three or more lines | open Now Playing for `A` | a name that fits two lines wraps and stands still; with the 64-character name, the title is one scrolling line; the whole name can be read by waiting (SC-001) |
| 5 | Radio NV renamed long too, on `B` | Rooms | both names scroll, and they start at different moments (FR-009). Note whether it looks too busy |
| 6 | as 1 | pull down the notification shade over Rooms, wait, close it | the name stops while the shade is down, then starts again from its beginning |
| 7 | as 1 | home button, wait, reopen | no error and no jump on return; the name scrolls again, from its beginning or from where it was (the background pause itself cannot be observed; Compose pauses its frame clock while the app is stopped, research R2) |
| 8 | as 1 | the user cuts the phone's Wi-Fi (wireless adb drops, the user observes) | the card dims, the name stands still at its start ending in "…"; when the network is back it scrolls again (SC-004) |
| 9 | as 1; `settings put global animator_duration_scale 0` (save the old value first) | Rooms and Now Playing | names stand still with "…", on Rooms one line, on Now Playing two lines. Restore the setting: scrolling resumes without reopening the app (SC-004) |
| 10 | as 1, TalkBack on (the user, if adb cannot) | focus `A`'s source line | the whole name is read once |
| 11 | as 1 | Move to room… sheet, Settings → Sources | names there still end in "…" (FR-012) |

At the end: stop the test routes, restore volumes, restore both names with `{"displayName": null}`,
restore `animator_duration_scale`, screen timeout and Do Not Disturb.
