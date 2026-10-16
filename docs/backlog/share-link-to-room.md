# Share a link to a room from YouTube, SoundCloud or any app

- **Status:** Proposed, not started. Written 2026-10-05.
- **Origin:** The previous Android app (`D:\projects-sonora\mobileapp`, plan
  `specs/plan-dynamicShareTargetsWithSettings.prompt.md`) let the user share a link from the
  YouTube or SoundCloud app straight to a speaker. This app can play a link only by copying it and
  pasting it into Start Playback (feature 003), so that capability was lost in the rewrite.
- **Depends on:** Link playback from 003 (`POST /api/v2/play`, its error messages and the 30 s
  timeout recovery, all shipped). Settings tabs from 004, for the share selection. A design update
  for that selection. No hub change.
- **Effort:** Medium. Android manifest and share shortcuts, an entry route into Start Playback,
  URL extraction in `commonMain`, the share selection in Settings (design first, stored on the
  device), and tests.

## Problem

To play a YouTube video or a SoundCloud track on a speaker today, the user has to tap Share →
Copy link in that app, switch to Sonora, open Start Playback, paste, pick a room and tap Play. The
old app reduced this to Share → *Kitchen*: the hub started playing in one step.

## How the old app did it

Read from its code (`ShareTargetSupport.kt`, `ShareShortcutPublisher.kt`, `MainActivity.kt`,
`res/xml/shortcuts.xml`):

1. **Share target.** `MainActivity` had an `ACTION_SEND` intent filter for `text/*`, ran with
   `launchMode="singleTop"` so a second share arrives through `onNewIntent`, and carried
   `<meta-data android:name="android.app.shortcuts" android:resource="@xml/shortcuts"/>` inside
   the `<activity>` element.
2. **One share shortcut per room and group.** `shortcuts.xml` declared a single `<share-target>`
   (`text/plain`, category `sonora.multiroom.mobile.SHARE_TARGET`). On every launch the app
   fetched outputs and groups and published them with
   `ShortcutManagerCompat.setDynamicShortcuts` as long-lived shortcuts in that category: ids
   `output_<id>` / `group_<id>`, label = display name, launcher icon, intent
   `Intent.ACTION_DEFAULT` (a send action keeps them out of the share sheet). When the fetch
   failed it published nothing, so the last good set stayed.
3. **Receiving a share.** It took `EXTRA_TEXT` and the first `https?://\S+` match, with trailing
   `.,;:)]}` trimmed (YouTube and SoundCloud put a title before the link). Android does not pass
   the shortcut's extras on to the send intent, so it recovered the target from
   `Intent.EXTRA_SHORTCUT_ID` by removing the `output_` / `group_` prefix. Then it called
   `POST /api/play` at once, named after the link's host name, and showed the result as text.
4. **Choosing targets.** A Settings screen listed outputs and groups with switches, all on by
   default and new ones turned on as they appeared, with a cap of 15 ("Selected N / 15", Select all
   / Deselect all). The share sheet shows only about 4–8 direct-share targets anyway, and puts the
   rest behind "More".

## Proposal

Bring the same flow to this app, adapted to its rules (Constitution II, III, V and the 003
behaviour):

- **Two entries in the share sheet.**
  - *Sonora* (the plain `ACTION_SEND` target) opens **Start Playback** with the link already in
    the field and no target selected. Every existing rule then applies: the "will stop" line,
    "Play link in <target>", errors and timeout recovery.
  - *Direct-share shortcuts*, one per turned-on room and group (e.g. "Kitchen", "Downstairs"),
    **play at once when the target is idle**, and open Start Playback with both the link and the
    target filled in **when it is busy**, so the "will stop" line shows and nothing is replaced
    without a tap on Play (decided by the user, 2026-10-05). How an immediate start is confirmed
    is Open question 1.
- **Idle or busy.** A target is busy when anything plays on it: for a room, any route on that
  output (single-output or through a group); for a group, any route on any of its members (the
  AGENTS.md rule "what plays on output X"). The check uses routes fetched after the share arrived,
  not a cached state, since the app may have been in the background. If the hub cannot be reached
  or the fetch fails, the share opens Start Playback prefilled rather than starting blind.
- **Only turned-on rooms and groups.** The share sheet offers only outputs and groups with
  `enabled = true`. A room or group that is Off never appears there, even if it is selected for
  sharing (decided by the user, 2026-10-05).
- **The user chooses which ones appear.** Settings gets a way to select, per room and group,
  whether it is offered in the share sheet, like the old app's switches. How it looks and where it
  sits is a design decision still to make (Open question 2).
- **Shortcuts follow hub state.** `HubSession` already knows the outputs and groups. Republish
  the shortcuts when the set of offered targets (turned on and selected for sharing) or their names
  changes, not on every 2.5 s refresh. Every shortcut uses the app icon, for rooms and groups alike
  (decided by the user, 2026-10-05). Never clear the set because the hub could not be reached.
  Shortcut ids stay namespaced (`output_<id>`, `group_<id>`), and the target is read back from
  `EXTRA_SHORTCUT_ID`.
- **Same request as 003.** `POST /api/v2/play` with `uri`, `targetId` and `targetType` only. Do
  not copy the old app's host-name `displayName`: 003 (FR-013) lets the hub name the link and keeps
  each room's own volume. The link is sent as shared, without rewriting: the hub supports every
  link the YouTube and SoundCloud apps share, including short `youtu.be/…?si=…` and
  `on.soundcloud.com/…` links (confirmed by the user, 2026-10-05).
- **Where the code goes.** URL extraction (first `http(s)` link in shared text, trailing
  punctuation trimmed, then 003's normalisation and validation) and the shortcut id parsing go in
  `commonMain` as pure functions, written test-first, as does the idle-or-busy decision. The intent filter, `shortcuts.xml`, the
  `ShortcutManagerCompat` publisher and the `onNewIntent` handling stay in `androidApp` /
  `androidMain`, and they hand a "pending share" (link + optional target) to the shared navigation.
  An iOS Share Extension is a separate item for later.
- **Edge cases.**
  - Shared text without a web address: Start Playback opens with the text in the field, and 003's
    "Enter a web address (https://…)" message shows.
  - No hub address set: the 003 "Set the hub address" state.
  - A shortcut whose room was deleted or turned off since it was published: Start Playback opens
    with the link and no target selected.
  - A share arriving while Start Playback is already open with a draft: the share replaces the
    draft.
  - An immediate start that the hub refuses (link unusable, unreachable, room refused): Start
    Playback opens prefilled with 003's plain-language error, so the user can retry or pick
    another room.
  - The hub answers only after the 30 s timeout: 003's timeout recovery applies.
- **Design.** Busy targets reuse Start Playback. The confirmation of an immediate start (Open
  question 1) and the share selection in Settings need a design update first (Constitution VI).

## Open questions

1. **Confirming an immediate start.** While the hub resolves the link (up to 30 s), what does the
   user see, and once it starts: open Now Playing for the new route, or show a short message and
   return to the app the link came from?
2. **Design of the share selection in Settings.** The Rooms and Groups tabs already have an on/off
   switch per row (feature 004). Options: a second control on each of those rows (e.g. a share
   icon toggle), a "Show in share sheet" switch on a row's detail, or a separate list (a section or
   tab) like the old app's. Related details: are new rooms and groups selected by default; is there
   a cap (the old app's 15, or Android's own limit per app, usually lower, see
   `ShortcutManagerCompat.getMaxShortcutCountPerActivity`) and how the UI shows it when reached;
   how a selected room that is Off looks (still selected, but not offered while Off). The
   selection is stored on the device, as the hub address is.
3. **Ranking.** Report each use with `ShortcutManagerCompat.pushDynamicShortcut` /
   `reportShortcutUsed`, so the rooms the user shares to most come first in the sheet?
