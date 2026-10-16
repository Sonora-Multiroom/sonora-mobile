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
| P2 | [Turning a source off stops its playback](source-off-stops-playback.md) | Fix | Small | Since hub 0.1.25 a turned-off source stops playing everywhere, yet the app asks nothing and then says it keeps playing |
| P2 | [Rooms with several playbacks](rooms-with-several-playbacks.md) | Feature / fix | Medium | Since hub 0.1.21 a room can play several routes. Rooms then shows it on several cards, and the move sheet misnames what will stop. Planned for a feature after 004 (Settings took that number) |
| P3 | [Refusal reasons and the rooms a playback is on](refusal-reasons-and-route-outputs.md) | Fix | Small to medium | A group playback is still counted on members that left it, and refusals since hub 0.1.22 show a generic message; both rare in practice |
| P3 | [Share a link to a room](share-link-to-room.md) | Feature | Medium | The old app could do this, so users lost it in the rewrite. Pasting a link into Start Playback (003) still works, only with more steps |
| P3 | [Shortcuts: one-tap playback](playback-shortcuts.md) | Feature | Medium | Saves the four-tap Start Playback flow for the source/room pairs used every day. Start Playback (003) does the job today, only with more taps. Needs a design pass first |
