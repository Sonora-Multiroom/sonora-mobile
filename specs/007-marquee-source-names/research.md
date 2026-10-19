# Research: Scroll Long Source Names

**Feature**: [spec.md](spec.md) | **Date**: 2026-10-20

Facts below were read from the sources in the Gradle cache:
`androidx.compose.foundation:foundation-android:1.12.1` (`BasicMarquee.kt`, `BasicMarqueeSamples.kt`)
and `androidx.compose.ui:ui-android:1.12.1` (`WindowRecomposer.android.kt`). Compose Multiplatform
1.12.1 (`gradle/libs.versions.toml`) maps to these.

## R1. The marquee itself

**Decision**: Use `Modifier.basicMarquee` from Compose foundation with
`iterations = Int.MAX_VALUE`, `repeatDelayMillis = 1_200`, `initialDelayMillis` per card (R5),
`velocity = 30.dp`, default `spacing` (a third of the line), `animationMode = Immediately`.

**Rationale**: It is in `commonMain` of foundation, already a dependency, and stable in 1.12.1 (no
opt-in annotation). It measures its content with unbounded width, so the text is never ellipsised
while it scrolls, and it does not animate when the content fits (`contentWidth <= containerWidth`).
It restarts on any parameter change and on content or container size change, and otherwise keeps
running across recompositions, so a 2.5 s refresh that brings the same name does not disturb it
(FR-005). Its defaults are Android TextView's marquee: 1.2 s delay, 30 dp/s.

**Watch out**: the default `iterations` is **3**, after which it stops. The spec wants it to repeat
for as long as it is shown, hence `Int.MAX_VALUE`.

**Alternatives considered**: a hand-written `Animatable` + `horizontalScroll` (more code, same
result); an Android `TextView` with `ellipsize=marquee` through `AndroidView` (Android-only, breaks
Principle III).

## R2. Window focus and background (FR-007)

**Decision**: Read `LocalWindowInfo.current.isWindowFocused` (Compose UI, common) and apply the
marquee only while it is true. The background case needs nothing more.

**Rationale**: The backlog item assumed `basicMarquee` pauses on lost window focus. In 1.12.1 it
does not: its only focus handling is the `WhileFocused` mode, which means *the node* has input
focus, not the window. Background is covered by the platform: `WindowRecomposer` pauses the
composition frame clock on `ON_STOP`, which suspends every `withFrameNanos` animation. Removing the
modifier on lost focus resets the name to its beginning; when focus returns it starts again with
the initial pause, which is what the spec now says (User Story 3, scenario 2).

**Alternatives considered**: `WhileFocused` mode with a focusable text (wrong meaning, and would
put a focus stop on a non-interactive text for keyboard and TalkBack users).

## R3. "Remove animations" (FR-008)

**Decision**: Read `MotionDurationScale.scaleFactor` from the composition's coroutine context
(`androidx.compose.ui.MotionDurationScale`, common API) and treat `0f` as "animations off": the
name then stands still with "…".

**Rationale**: `basicMarquee` deliberately ignores the setting: it runs inside its own
`FixedMotionDurationScale` (scale 1) with the comment "Marquee animations should not be affected by
motion accessibility settings". So without our own check the name would scroll with animations
off. On Android, `WindowRecomposer` installs a `MotionDurationScaleImpl` whose `scaleFactor` is
backed by `Settings.Global.ANIMATOR_DURATION_SCALE` through a content observer and a snapshot state,
so reading it inside composition (via a `produceState` / `LaunchedEffect` that reads
`coroutineContext[MotionDurationScale]`) follows a change of the setting live. This keeps the
check in `commonMain` with no `expect`/`actual`. Where the context has no scale (iOS later, tests),
the default is 1 (animations on).

**Alternatives considered**: `expect`/`actual` reading `Settings.Global` on Android (duplicates what
Compose already does, adds a platform file); ignoring the setting as `basicMarquee` does (the spec
chose to honour it, FR-008).

## R4. Deciding whether a name fits, and the Now Playing two-line rule

**Decision**: One composable `MarqueeText(text, style, color, maxLines, stale, startDelayMillis)`
works out `canScroll` itself (R2, R3) and measures the text with `rememberTextMeasurer()` inside `BoxWithConstraints` at the available width
and `maxLines` (1 on Rooms, 2 on Now Playing), keyed on text, style and width. A pure function
turns the result into one of three layouts:

| Overflows `maxLines`? | `canScroll` | Layout |
|---|---|---|
| no | any | **Fits**: plain text, `maxLines` lines, no "…", no fade (looks as before, SC-005) |
| yes | false | **Truncated**: `maxLines` lines ending in "…" (as before this feature) |
| yes | true | **Scrolling**: one line, `softWrap = false`, faded edges (R6), `basicMarquee` |

`canScroll = !stale && windowFocused && animationScale > 0` is a second pure function.

**Rationale**: Measuring up front avoids a flicker loop that `onTextLayout` would cause (lay out in
two lines, see overflow, switch to one line, lose the overflow signal). On Rooms
(`maxLines = 1`), "overflows" is exactly `basicMarquee`'s own condition, so the two agree. Now
Playing gets the clarified rule: wrap to two lines, scroll as one line only when two are not
enough. The truncated fallback is today's rendering, so stale and animations-off look exactly as
before this feature.

**Alternatives considered**: always applying `basicMarquee` and relying on its own fit check (no
way to keep two lines on Now Playing, and the fade would be drawn on text that fits).

## R5. Not moving in step (FR-009)

**Decision**: Rooms passes each card an initial delay of `1_200 + (index % 4) * 600` ms, where
`index` is the card's position among the now-playing cards, remembered per card key and name so a
reorder does not change it (a changed parameter would restart the marquee). Now Playing uses
1 200 ms. The rule is one pure function, tested.

**Rationale**: Different start moments break the lockstep at once. After the first cycle, names of
different lengths drift apart anyway, since each cycle lasts as long as its own name takes to pass.
The cap keeps the longest initial wait at 3 s.

**Alternatives considered**: a delay derived from a hash of the room id (two rooms can collide,
harder to reason about); only one card scrolling at a time (needs coordination across cards, more
state for little gain; can be revisited if the on-device check finds it still too busy).

## R6. Faded edges (FR-004)

**Decision**: Only in the Scrolling layout: `graphicsLayer { compositingStrategy = Offscreen }` +
`drawWithContent` drawing a 12 dp `DstIn` gradient at each edge, placed **before** `basicMarquee`
so the fade stays fixed while the text moves (the foundation sample `BasicMarqueeWithFadedEdgesSample`).

**Rationale**: Offscreen compositing applies the fade to the text only, not to the card behind it.
The fade is not drawn on text that fits. At rest, the left fade lightly touches the first letter;
12 dp (the sample uses 32 dp on a much wider line) keeps that small. The on-device check confirms
it; the fallback, if the first letter looks cut, is to fade the right edge only.

**Alternatives considered**: no fade (the backlog asked for one); a fade drawn in the card colour
(wrong over the stale/dimmed card and over the selected-state background).

## R7. Where it lives, accessibility and the design

**Decision**:
- `ui/rooms/MarqueeText.kt` holds the composable and the two pure functions (`marqueeLayout`,
  `canScroll`) plus `marqueeStartDelay`. It sits next to `VolumePill.kt`, which Now Playing already
  shares from the same package.
- The `Text` always carries the full string, so TalkBack reads the whole name once (FR-011); the
  marquee changes only drawing.
- Each name is wrapped in `key(text)`, so a new name always starts from its beginning (FR-005), even
  if it happens to have the same width.
- Design: the departure is called out in the spec (FR-013); the canvas is not changed. AGENTS.md's
  "Agreed details" gains one line on shipping, so later work keeps the behaviour.

**Rationale**: One definition used in both places (FR-010).

## R8. Testing

**Decision**: Test-first in `commonTest` (`ui/rooms/MarqueeTextTest.kt`) for the pure functions:
`marqueeLayout` (fits / truncated / scrolling for one and two lines), `canScroll` (stale, unfocused,
scale 0, scale between 0 and 1, all clear), `marqueeStartDelay` (1 200, 1 800, 2 400, 3 000, then
wraps). The composable's drawing, the measuring and the timing are checked on the device
([quickstart](quickstart.md) §2). Compose UI tests stay optional (Constitution IV); the project has
no Compose UI test setup and this feature does not add one.

## R9. Version and the full app test

**Decision**: First implementation commit sets `sonora.versionName=0.7.0-alpha`,
`sonora.versionCode=7`. The last task updates `docs/testing/full-app-test.md` (rows for quickstart
§2, header to 007 / 0.7.0-alpha / API 0.1.28). The backlog item is deleted and its `INDEX.md` row
removed when the feature ships.

**Rationale**: AGENTS.md "Bump the app version first" and the constitution's full app test gate.
