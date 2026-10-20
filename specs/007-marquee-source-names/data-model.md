# Data Model: Scroll Long Source Names

**Feature**: [spec.md](spec.md) | **Date**: 2026-10-20

No domain or repository change. The source name is already in the view state:
`NowPlayingCard.sourceName` (Rooms) and the Now Playing content's `sourceName`, both from the
input's display name (`ApiMapping.kt`). This feature adds presentation types only, in
`ui/rooms/MarqueeText.kt`.

## MarqueeLayout

How a name is drawn. Decided by `marqueeLayout(overflows, canScroll)`.

| Value | When | Drawn as |
|---|---|---|
| `Fits` | the name fits in `maxLines` | `maxLines` lines, no "…", no fade, still |
| `Truncated` | does not fit, `canScroll` false | `maxLines` lines ending in "…", still |
| `Scrolling` | does not fit, `canScroll` true | one line, faded edges, marquee |

`overflows` is measured by the composable at the available width and `maxLines` (1 on Rooms, 2 on
Now Playing).

## canScroll

`canScroll(stale, windowFocused, animationScale): Boolean` is true only when all hold:

| Input | Source | Blocks scrolling when |
|---|---|---|
| `stale` | Rooms: `stale` passed to the card; Now Playing: `state.stale` | true |
| `windowFocused` | `LocalWindowInfo.current.isWindowFocused` | false |
| `animationScale` | `MotionDurationScale.scaleFactor` from the composition context, default 1 | `<= 0` |

## Start delay

`marqueeStartDelay(index): Int` = `1_200 + (index % 4) * 600` ms. Rooms passes the card's position
among the now-playing cards, remembered per card key and name; Now Playing passes index 0.

## State transitions of one name

```text
            name changes / shown anew
                    │
                    ▼
   ┌──────── measure at width, maxLines ────────┐
   │ fits                                       │ overflows
   ▼                                            ▼
 Fits ◄── width grows ──              canScroll? ── no ──► Truncated
                                                │ yes          ▲  │
                                                ▼              │  │
                                          Scrolling ──────────┘  │
                                   (stale, unfocused, scale 0)    │
                                          ▲                       │
                                          └──── all clear again ──┘
                                     (starts from the beginning, initial delay)
```

A refresh that brings the same name, the same width and the same `canScroll` leaves the current
state and the marquee position untouched.
