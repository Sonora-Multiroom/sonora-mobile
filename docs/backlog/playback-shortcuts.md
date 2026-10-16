# Shortcuts: start a saved source on a saved room in one tap

- **Status:** Proposed, not started. Written 2026-10-10.
- **Origin:** The user, 2026-10-10. Starting the same thing in the same place (e.g. KissFM in the
  Kitchen every morning) takes the full Start Playback flow every time.
- **Depends on:** Start Playback (003: both start requests, the "will stop" line, errors and the
  30 s link timeout, all shipped). A design update for where shortcuts live and how they are made
  and edited. No hub change.
- **Effort:** Medium. A stored list of shortcuts on the device (each with an optional volume), a
  place on the main screen to show and tap them, a way to create, edit, reorder and delete them,
  the start itself (reusing 003's requests and errors, plus the volume step), and tests.

## Problem

To play something today the user taps **Play something**, picks a configured source or pastes a
link, picks a room or group, and taps Play: at least four taps and a screen change, even when it is
the same source in the same room as yesterday. Most listening is a handful of such pairs.

## Proposal

A **shortcut** (name still open, see Open question 1) is a saved pair of *what* and *where*, shown
on the main screen; one tap starts it.

- **What a shortcut holds.**
  - *What*: either a configured source (`inputId` of a `STATIC` input) or a link (the URI as typed
    in 003). A link is stored as the link, never as the `EPHEMERAL` input the hub created for it,
    because the hub removes that input when its playback ends.
  - *Where*: one output or one group (`targetId` + `targetType`).
  - *Volume* (optional, 0–100, decided by the user, 2026-10-10): set while creating or editing
    the shortcut. When set, the shortcut starts at that volume; when not set, the target keeps
    the volume it has, as a 003 start does.
  - *Label*: defaults to "<source> in <target>" (or "Link in <target>"), editable.
- **Starting.** The same requests as 003: `POST /api/v2/routes` for a configured source,
  `POST /api/v2/play` for a link, with no name. On success, behave as 003 does (open Now Playing
  for the returned route), or stay on Rooms (Open question 4).
- **Applying the volume.** Only `POST /api/v2/play` has a `volume` field (described as "set before
  playback starts"); `POST /api/v2/routes` has none. So the app applies a saved volume itself,
  the same way for both kinds: `PUT /api/v2/outputs/{id}/volume` **before** the start request, so
  the room never plays at its old volume first, even for a moment. For a group it never calls the
  group volume endpoint (AGENTS.md, Hub gaps); how a group volume is spread over its members is
  Open question 7. If the start then fails, the volume has already changed; 003's error message
  shows, and the volume is not rolled back (Open question 8).
- **Busy target.** Tapping a shortcut whose target is busy must not silently replace what plays
  there (the 003 "will stop" rule). Either ask first ("Radio Paradise will stop in Bedroom") or open
  Start Playback with source and target prefilled, as the share item does for busy targets. Already
  playing the same source on the same target: open Now Playing for it, start nothing.
- **Unavailable shortcuts.** A shortcut whose source or target is Off, unavailable or no longer
  exists (deleted or renamed in the hub config) shows as disabled with the reason, and is not
  removed automatically: the room may come back. Tapping it explains why instead of calling the
  hub.
- **Stored on the device**, as the hub address is, keyed to the hub address so switching hubs does
  not show another hub's shortcuts. The hub has nowhere to store them (and this repo makes no hub
  changes), so they are not shared between phones; a hub-side store would be a `multiroom-ai`
  backlog item.
- **Creating one.** Options, not exclusive: a "Save as shortcut" action on Start Playback after
  picking a source and a target; a "Save as shortcut" action on Now Playing for what is playing;
  an "Add shortcut" form wherever shortcuts are listed.
- **Where the code goes.** The shortcut model, the label default, the availability check against
  the session's outputs, groups and inputs, and the idle-or-busy decision go in `commonMain` as
  pure functions, written test-first, sharing the "what plays on output X" logic with Start
  Playback and the share item. Storage uses the same multiplatform store as the hub address.

## Open questions

1. **Name.** Candidates: *Shortcuts* (clear, but clashes with Android launcher shortcuts if those
   come later, see 5), *Favourites*, *Presets* (as on a car radio: one button, one station),
   *Quick play*, *Scenes* (suggests several rooms at once, see 6).
2. **Where on the main screen.** Options:
   - a horizontal row of chips or tiles at the top of Rooms, above the cards;
   - next to or replacing **Play something** (e.g. tiles above it, or a split button);
   - its own bottom-navigation tab. The bottom bar already has a **Sources** tab that leads nowhere
     (`design/screens/Main.dc.html`), which could become this screen or be renamed for it.
   Needs a design pass on the Claude Design canvas first.
3. **How many and in what order.** A cap (one row of chips fits about 3–4), manual reordering, or
   most-used first.
4. **After a start:** open Now Playing (as 003 does) or stay on the main screen with a short
   message, so several shortcuts can be tapped in a row?
5. **Android launcher shortcuts.** Also publish them as home-screen / long-press shortcuts
   (`ShortcutManagerCompat`), so a start does not even need the app open? Shares the publishing
   code with [Share a link to a room](share-link-to-room.md).
6. **Several rooms.** Should a shortcut be able to start on several targets at once?
7. **Volume on a group.** The group volume pill shows the loudest member, and dragging it scales
   every member by the same ratio. A saved group volume could work the same way: the loudest
   member is set to the saved value and the others keep their balance. Alternatively, save one
   volume per member, or set every member to the same value (which loses the balance). Which one?
8. **Volume details.** Should a start that fails roll the volume back? Should a shortcut with a
   saved volume also unmute a muted room, or leave mute alone (and master mute)? How does the
   creation form show "not set" next to a value: a switch plus the volume pill, or a pill with a
   "Keep current" state?

Related: [Share a link to a room](share-link-to-room.md) (same busy-target rule, possible shared
launcher-shortcut code), [Rooms with several playbacks](rooms-with-several-playbacks.md) (what
"busy" means once a room can carry several routes).
