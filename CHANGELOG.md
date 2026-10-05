# Changelog

User-facing changes to the Sonora Multiroom mobile app, one entry per feature. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/). Version numbers come from
[gradle.properties](gradle.properties): feature `NNN` ships as `0.N.0-alpha`. Each entry links
the feature's spec folder and pull request. Repository-only changes (documentation, CI, tooling)
are listed under "Development" when they affect contributors.

## [0.4.0-alpha] - 2026-10-05

Settings tabs and sources ([spec](specs/004-settings-sources/),
[#8](https://github.com/Sonora-Multiroom/sonora-mobile/pull/8)).
Settings becomes the place to manage the hub.

### Added

- Settings shows a Hub row (connection status and address) above four tabs: Rooms, Groups,
  Sources and Extensions. Settings reopens on the last tab used. The bottom bar's Sources item
  opens Settings on the Sources tab.
- Rooms, groups and configured sources each have an on/off switch. If a room or group is playing
  when you turn it off, the app asks first and names what will stop and where. Idle rooms and
  groups, and all sources, switch at once. Turning off a playing source tells you it keeps
  playing. A change keeps going after you leave the screen, and if it fails the error appears on
  Rooms.
- Each row shows its state. Rooms show "Playing · <source>", "In <groups>" or "Off", groups list
  their members, and sources show their kind and address.
- Sources added from apps (played links, DLNA) are listed newest first with the time they were
  added ("Added today 14:30"). Sources the hub removes on its own are marked "removed when it
  stops". Each has a Remove button, and removing a source in use asks first.
- The hub address sheet can test an address before it is saved, showing "Hub found · N rooms" or
  "Can't reach the hub at this address". Only Save stores the address; Close discards it.
- The Extensions tab lists the hub's extensions with their status and connection. It is
  read-only.
- While the hub is unreachable, Settings keeps showing the last lists under a stale banner, with
  the controls disabled.

### Known issues

- When a group is turned off, its confirmation dialog says playback will stop, but it keeps
  playing. Stopping the playback needs a hub change. Until then, Stop on Rooms ends it. Turning a
  room off is not affected.

## [0.3.0-alpha] - 2026-10-04

Start Playback ([spec](specs/003-start-playback/),
[#7](https://github.com/Sonora-Multiroom/sonora-mobile/pull/7)).
The app can now start playback, not just control it.

### Added

- Start Playback opens from Rooms' "Play something", or from an idle room's
  "Play something in <room>" with that room already selected. You can play a pasted link
  (SoundCloud, YouTube or a stream) or an enabled source, listed A to Z, on a room or a group.
- Links typed without a scheme get `https://` added. An invalid link shows "Enter a web address".
- Targets show their state: Idle, Playing, In <group>, Turned off or Not connected.
- Before you press Play, the screen says what will happen:
  - "<source> will stop in <target>"
  - "<source> is already playing in <target>" (Play then opens that playback)
  - "Won't play in <room> (turned off)"
  - a note when the target is muted
- Play shows "Starting…" and locks the selection. When playback starts, Now Playing replaces the
  screen, so Back returns to where you opened it. If it fails, your selection stays and the error
  is explained in plain language.
- A start keeps going after you close the screen, and if it fails the error appears on Rooms.
  Before reporting a failure after a timeout or a lost answer, the app checks whether the
  playback started anyway.

### Changed

- Rooms shows "Play something" even when every room is busy.
- Requires hub API 0.1.21, which adds join modes and the reason a start is refused.

## [0.2.0-alpha] - 2026-10-04

Now Playing and Move to room ([spec](specs/002-now-playing-move-to-room/),
[#3](https://github.com/Sonora-Multiroom/sonora-mobile/pull/3)).

### Added

- Tapping a playing room opens Now Playing, which shows:
  - the source with its kind and address
  - the status, and the target with its member rooms
  - Pause/Resume (only when the source can be paused) and Stop
  - volume and mute for the target and for each group member
- Live streams carry a "Live stream" badge and the line "Live streams can't be paused". The
  volume controls freeze while master mute is on.
- "Move to room…" opens a sheet listing every room and group, each with what the move would
  stop. Rooms and groups that are off or not connected are dimmed, and the action is hidden while
  playback is paused. After a move, Now Playing follows the playback to its new room or group.
- If the playback ends somewhere else, the app returns to Rooms with
  "Playback on <target> ended".
- The Settings footer shows the CI build number and commit.

### Changed

- A volume change you are making carries over when you switch tabs.
- Debug builds from CI install over each other without uninstalling first.

## [0.1.0-alpha] - 2026-10-03

Rooms screen foundation ([spec](specs/001-rooms-screen-foundation/),
[#1](https://github.com/Sonora-Multiroom/sonora-mobile/pull/1)).
The first release: an Android app that shows and controls what plays in every room through the
hub's REST API v2.

### Added

- Rooms has a card for each room or group that is playing, with:
  - a volume pill (a group's pill keeps the balance between its rooms)
  - Stop, and Pause/Resume when the source can be paused
  - a "Live stream" status for live streams
- Below the cards, Rooms lists the Idle and Off rooms, and has a master mute.
- Rooms refreshes every 2.5 seconds while the app is in the foreground. When the hub is
  unreachable, the last state stays on screen with its controls disabled.
- Settings has the hub address, saved on the device, and a footer with the app version.
- A bottom navigation bar. Now Playing, Start Playback and Sources are placeholders.

[0.4.0-alpha]: https://github.com/Sonora-Multiroom/sonora-mobile/pull/8
[0.3.0-alpha]: https://github.com/Sonora-Multiroom/sonora-mobile/pull/7
[0.2.0-alpha]: https://github.com/Sonora-Multiroom/sonora-mobile/pull/3
[0.1.0-alpha]: https://github.com/Sonora-Multiroom/sonora-mobile/pull/1
