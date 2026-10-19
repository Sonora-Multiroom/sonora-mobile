# Scroll long source names

- **Status:** Proposed 2026-10-19. Specified 2026-10-20 as
  [007-marquee-source-names](../../specs/007-marquee-source-names/spec.md) (full workflow).
- **Origin:** The user, 2026-10-19. Since hub API 0.1.28 a link's input is named after the title it
  resolved to (`PlayResponse.displayName`, then `InputResponse.displayName`), for example
  "Flickermood — Forss". Such titles are often longer than a Rooms card line, and the "…" cuts
  off the part that tells tracks apart.
- **Depends on:** Nothing. No hub call or dependency: `Modifier.basicMarquee()` is in Compose
  foundation (Compose Multiplatform 1.12.1 has it). The name already arrives as the input's
  `displayName`.
- **Effort:** Small. One shared composable used in two places, plus a full app test row and an
  on-device check. Fits the tinyspec path (bumps `0.6.0-alpha` to `0.6.1-alpha`).

## Problem

The source name under the room title on a Rooms card
([NowPlayingCard.kt](../../composeApp/src/commonMain/kotlin/sonora/multiroom/mobile/ui/rooms/NowPlayingCard.kt))
is one line, cut off with "…". Now Playing
([NowPlayingScreen.kt](../../composeApp/src/commonMain/kotlin/sonora/multiroom/mobile/ui/nowplaying/NowPlayingScreen.kt))
wraps the title to two lines, then cuts it off too. A long resolved title is unreadable in both.

## Proposal

A source name that does not fit scrolls by itself from right to left, as a marquee; the user does
not scroll it.

- One composable (e.g. `MarqueeText`) built on `Modifier.basicMarquee()`: one line, no ellipsis,
  start after ~1.2 s, slow (~30 dp/s), pause, repeat. Fade the left and right edges instead of a hard
  cut.
- Text that fits stays still, without "…".
- It restarts only when the name changes (the next track), not on the 2.5 s poll.
- It stops while the app is not focused (`basicMarquee` does this itself), and stays still on a
  stale (dimmed) card so a frozen state does not look alive.
- Where: the Rooms card source line and the Now Playing title. Room titles, status lines, the move
  sheet and Settings rows keep their ellipsis.

## Open questions

1. Now Playing: one scrolling line like the Rooms card, or keep the two-line wrap and scroll only
   when two lines are not enough?
2. Does it stand still when Android's "Remove animations" is on? Check on the POCO; if not, read
   the setting and turn the marquee off.
3. With several long names on Rooms at once, do all of them scrolling together look too busy?
   If so, start each after a different delay.
4. Should the Sources tab in Settings scroll too? Runtime inputs carry the same resolved titles.
5. Related: hub 0.1.28 also adds `PUT /api/v2/inputs/{id}/display-name` (rename a source). A rename
   action in Settings would be a separate item.
