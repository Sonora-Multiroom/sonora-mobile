# Backlog index

Every item in `docs/backlog/`, highest priority first. Each one is a proposal, not a requirement (see
the Backlog section of [AGENTS.md](../../AGENTS.md)). When you add, ship or delete an item, update
this list.

| Priority | Meaning |
|---|---|
| **P1** | A crash, data loss or other user-visible fault. Do it next. |
| **P2** | Wrong or confusing behaviour, or something users need now. |
| **P3** | A worthwhile feature or cleanup with no pressure on it. |
| **P4** | Only worth doing once a concrete need appears. |

| Priority | Item | Kind | Effort | Why this priority |
|---|---|---|---|---|
| P2 | [Rooms with several playbacks](rooms-with-several-playbacks.md) | Feature / fix | Medium | Since hub 0.1.21 a room can play several routes. Rooms then shows it on several cards, and the move sheet misnames what will stop. Planned for a feature after MOB-004 (Settings took that number) |
| P3 | [Share a link to a room](share-link-to-room.md) | Feature | Medium | The old app could do this, so users lost it in the rewrite. Pasting a link into Start Playback (MOB-003) still works, only with more steps |
| P3 | [Automate the full app test](automated-full-app-test.md) | Tooling | Medium | A full on-device run takes ~35 min of agent turns and caused test mistakes; Maestro flows with gates could cut it to minutes. Nothing breaks without it |
| P3 | [Shortcuts: one-tap playback](playback-shortcuts.md) | Feature | Medium | Saves the four-tap Start Playback flow for the source/room pairs used every day. Start Playback (MOB-003) does the job today, only with more taps. Needs a design pass first |
| P3 | [Change what plays from Now Playing](change-source-from-now-playing.md) | Feature | Small | Swapping the station in a room takes a stop and a trip back to Rooms. A button that opens Start Playback with this room preselected reuses the screen and the "will stop" line; a design pass for the button and the back stack come first |
| P3 | [Adopt HUB-034: a playback's own volume](adopt-output-volume.md) | API adoption / UX | Medium | A playback with its own volume (music or an announcement) shows the room's volume on its pill, which doesn't change what you hear. Rare until a client (Home Assistant, TTS, the CLI) sends route volumes; UX proposal written, design pass next |
