---

description: "Task list for 007 Scroll Long Source Names"
---

# Tasks: Scroll Long Source Names

**Input**: Design documents from `specs/007-marquee-source-names/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md),
[data-model.md](data-model.md), [contracts/ui-behaviour.md](contracts/ui-behaviour.md),
[quickstart.md](quickstart.md)

**Tests**: REQUIRED for the pure functions (Constitution IV, research R8). Every test task comes
before its implementation task and MUST be seen failing first. Drawing, measuring and timing are
checked on the device (quickstart §2); no Compose UI test setup is added.

**Organization**: grouped by user story. Spec priorities:
- US1 P1: a long source name scrolls on the Rooms card
- US2 P1: a name longer than two lines scrolls on Now Playing
- US3 P2: scrolling stays calm and truthful (stale, window focus, "Remove animations", staggered starts)

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on an unfinished task)
- **[Story]**: US1–US3 from spec.md

## Path Conventions

- Shared code: `composeApp/src/commonMain/kotlin/sonora/multiroom/mobile/` (abbreviated `main/` below)
- Shared tests: `composeApp/src/commonTest/kotlin/sonora/multiroom/mobile/` (abbreviated `test/` below)

## General rules for every task

- No hub call, no domain or repository change, no new dependency. Only Compose foundation/UI APIs
  already on the classpath (Compose Multiplatform 1.12.1).
- Nothing Android-only in `commonMain`/`commonTest`: no `android.provider.Settings`, no
  `expect`/`actual`. Colours and type only through `SonoraTheme` tokens, exactly as the replaced
  `Text` calls used them.
- The scrolling behaviour is defined once, in main/ui/rooms/MarqueeText.kt. Screens only call
  `MarqueeText`.
- Only two texts change: the Rooms card source line and the Now Playing title. Every other text
  keeps its `maxLines` and `TextOverflow.Ellipsis`.
- Code comments must not cite `FR-`, `SC-`, `US` or research numbers of this feature.

---

## Phase 1: Setup

- [X] T001 Set `sonora.versionName=0.7.0-alpha` and `sonora.versionCode=7` in gradle.properties as
  the first implementation commit (AGENTS.md "Bump the app version first").

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: the shared `MarqueeText` composable and its layout rule. Nothing uses it yet, so
nothing user-visible changes.

- [X] T002 Create test/ui/rooms/MarqueeTextTest.kt with failing tests for
  `marqueeLayout(overflows: Boolean, canScroll: Boolean): MarqueeLayout` (data-model.md
  "MarqueeLayout"): `(false, true)` → `Fits`; `(false, false)` → `Fits`; `(true, false)` →
  `Truncated`; `(true, true)` → `Scrolling`.
- [X] T003 Create main/ui/rooms/MarqueeText.kt (depends on T002) with:
  - `enum class MarqueeLayout { Fits, Truncated, Scrolling }` and `internal fun marqueeLayout(overflows, canScroll)`
    making T002 pass.
  - `@Composable fun MarqueeText(text: String, style: TextStyle, color: Color, maxLines: Int, stale: Boolean, modifier: Modifier = Modifier, startDelayMillis: Int = 1_200)`.
    For now `canScroll = !stale` (T009 extends it).
  - Measuring (research R4): inside `BoxWithConstraints`, `rememberTextMeasurer()` and
    `remember(text, style, maxWidth, maxLines) { measurer.measure(text, style, maxLines = maxLines, constraints = Constraints(maxWidth = …)).hasVisualOverflow }`.
    Do not decide from `onTextLayout`.
  - Drawing per layout (contracts/ui-behaviour.md):
    `Fits` → `Text(text, style = style, color = color, maxLines = maxLines)`;
    `Truncated` → the same with `overflow = TextOverflow.Ellipsis`;
    `Scrolling` → `Text(text, style, color, maxLines = 1, softWrap = false)` with the modifier
    chain `graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }` →
    `drawWithContent { drawContent(); fade both edges }` (12 dp `Brush.horizontalGradient`,
    `BlendMode.DstIn`, as foundation's `BasicMarqueeWithFadedEdgesSample`) →
    `basicMarquee(iterations = Int.MAX_VALUE, repeatDelayMillis = 1_200, initialDelayMillis = startDelayMillis, velocity = 30.dp)`.
    The fade modifiers come **before** `basicMarquee` so the fade stays put while the text moves.
    `iterations` MUST be `Int.MAX_VALUE`: the default (3) stops after three cycles.
  - Wrap the content in `key(text) { … }` so a new name always starts from its beginning, even at
    the same width.
  - KDoc: what the three layouts are, that the full text stays in the semantics (TalkBack reads it
    once), and that a refresh with the same text leaves the marquee running.

**Checkpoint**: build and all tests green, nothing user-visible changed.

---

## Phase 3: User Story 1 - Reading a long source name on a Rooms card (Priority: P1) 🎯 MVP

**Goal**: a source name too long for its line on a Rooms card scrolls by itself; one that fits
stays still with no "…".

**Independent Test**: quickstart §2 rows 1–3: a long name scrolls, a short one is still, a refresh
does not restart it, a rename restarts it.

- [X] T004 [US1] In main/ui/rooms/NowPlayingCard.kt replace the source line
  `Text(card.sourceName, style = type.body14, color = primary, maxLines = 1, overflow = TextOverflow.Ellipsis)`
  with `MarqueeText(card.sourceName, style = type.body14, color = primary, maxLines = 1, stale = stale)`
  (depends on T003). Leave the room title and the status line as they are. Check that the source
  line's column keeps its weight, so a scrolling name never runs under the action button.

**Checkpoint**: build green; on the device a long name scrolls on Rooms (quickstart §2 rows 1–3).

---

## Phase 4: User Story 2 - Reading a long source name on Now Playing (Priority: P1)

**Goal**: the Now Playing title still wraps to two lines; only a name longer than two lines
becomes one scrolling line.

**Independent Test**: quickstart §2 row 4.

- [X] T005 [US2] In main/ui/nowplaying/NowPlayingScreen.kt (`PlaybackView`) replace the title
  `Text(content.sourceName, style = type.cardTitle.copy(fontSize = 26.sp, letterSpacing = (-0.52).sp), color = primary, maxLines = 2, overflow = TextOverflow.Ellipsis)`
  with `MarqueeText(content.sourceName, style = <the same style>, color = primary, maxLines = 2, stale = state.stale)`
  (depends on T003). Leave the subtitle and the target line as they are. Independent of T004.

**Checkpoint**: build green; on the device row 4 passes.

---

## Phase 5: User Story 3 - Scrolling stays calm and truthful (Priority: P2)

**Goal**: nothing moves while stale, unfocused or with "Remove animations" on; several long names
on Rooms start at different moments.

**Independent Test**: quickstart §2 rows 5–9.

### Tests for User Story 3 (write first, see them fail)

- [X] T006 [US3] In test/ui/rooms/MarqueeTextTest.kt add failing tests for
  `canScroll(stale: Boolean, windowFocused: Boolean, animationScale: Float): Boolean`
  (data-model.md "canScroll"): `(false, true, 1f)` → true; `(false, true, 0.5f)` → true;
  `(true, true, 1f)` → false; `(false, false, 1f)` → false; `(false, true, 0f)` → false.
- [X] T007 [US3] In the same file add failing tests for `marqueeStartDelay(index: Int): Int`
  (`1_200 + (index % 4) * 600`): 0 → 1 200, 1 → 1 800, 2 → 2 400, 3 → 3 000, 4 → 1 200.

### Implementation for User Story 3

- [X] T008 [US3] In main/ui/rooms/MarqueeText.kt add `internal fun canScroll(...)` and
  `internal fun marqueeStartDelay(index)` making T006 and T007 pass (depends on T003, T006, T007).
- [X] T009 [US3] In `MarqueeText` (main/ui/rooms/MarqueeText.kt) compute
  `canScroll(stale, windowFocused, animationScale)` instead of `!stale` (depends on T008), where
  (research R2, R3):
  - `windowFocused = LocalWindowInfo.current.isWindowFocused`. `basicMarquee` 1.12.1 does not
    pause on lost window focus by itself.
  - `animationScale` comes from `produceState(1f) { coroutineContext[MotionDurationScale]?.let { s -> snapshotFlow { s.scaleFactor }.collect { value = it } } }`
    (`androidx.compose.ui.MotionDurationScale`), so a change of the phone's setting applies
    live. `basicMarquee` runs with its own fixed scale of 1 and ignores the setting, so this check
    is what honours "Remove animations". Without a scale in the context, the default 1 applies.
- [X] T010 [US3] Staggered starts (depends on T008, T004). In main/ui/rooms/RoomsScreen.kt switch the
  now-playing `items(content.cards, key = { it.key })` to `itemsIndexed(…, key = { _, card -> card.key })`
  and pass `startDelayMillis = remember(card.sourceName) { marqueeStartDelay(index) }` to
  `NowPlayingCard`. Remembering it inside the keyed item keeps a reorder from changing the delay,
  since a changed `basicMarquee` parameter restarts the animation. In main/ui/rooms/NowPlayingCard.kt add the
  parameter `startDelayMillis: Int = 1_200` and pass it to `MarqueeText`. Now Playing keeps the
  default.

**Checkpoint**: build and all tests green; quickstart §2 rows 5–9 pass on the device.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [X] T011 Run the local checks and read each exit code (`cmd > log; echo $?`):
  `./gradlew :androidApp:assembleDebug`, `:composeApp:testAndroidHostTest`, `:composeApp:allTests`,
  `:composeApp:check`. Record the commands, date and test counts in
  specs/007-marquee-source-names/verification.md, created in 006's format: build under test,
  device, status legend, one row per quickstart §2 check, "Issues found".
- [X] T012 [P] Add a `## [0.7.0-alpha] - Unreleased` entry to CHANGELOG.md above 0.6.0-alpha
  (spec folder link, "pull request to follow"). Under Changed: a source name too long for its line
  scrolls by itself on the Rooms card and, when longer than two lines, on the Now Playing title; it
  stands still while the app cannot reach the hub and when the phone's animations are off.
- [X] T013 [P] Update AGENTS.md: under Design "Agreed details" add "A source name too long for its
  line scrolls as a marquee on the Rooms card and the Now Playing title (two lines first there); it
  stands still when stale, unfocused or with animations off. Everywhere else names end in '…'."
- [X] T014 [P] Delete docs/backlog/scrolling-long-source-names.md and its row in
  docs/backlog/INDEX.md. Remove or update any links to it (`grep -rn scrolling-long-source-names`),
  except in specs/007-marquee-source-names/.
- [X] T015 **Local**: run quickstart §2 rows 1–11 on the POCO against the real hub, following
  AGENTS.md "On-device verification". Note the volumes and set them to 1 first; save
  `animator_duration_scale` before row 9. At the end stop the test routes, then restore the volumes,
  both source names (`{"displayName": null}`), the animation setting, the screen timeout and Do Not
  Disturb. Record the evidence in specs/007-marquee-source-names/verification.md, including whether
  the 12 dp left fade makes the first letter look cut (fallback: right edge only, research R6) and
  whether several names scrolling looks too busy.
- [X] T016 Update docs/testing/full-app-test.md for this feature. Add rows for quickstart §2 rows 1–9
  in their sections (R for Rooms, N for Now Playing, X for the stale row 8). Extend the fixture with
  a long name given to STREAM by `PUT /api/v2/inputs/{id}/display-name`, and restore it with
  `{"displayName": null}` in Teardown (T). Bump the header (covers `0.7.0-alpha`, hub API 0.1.28,
  date).

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (T001)**: first.
- **Foundational (T002–T003)**: after T001. T003 blocks every story.
- **US1 (T004)** and **US2 (T005)**: after T003, independent of each other (different files).
- **US3 (T006–T010)**: after T003. T006–T008 and T009 touch only MarqueeText and its test, so
  they also apply to whichever of US1/US2 is done. T010 needs T004 (it edits NowPlayingCard.kt).
- **Polish (T011–T016)**: after the stories. T015 needs an installed build. T016 is last.

### Within Each User Story

- Tests first, seen failing, then the implementation.

### Parallel Opportunities

- T004 and T005 (two screens) once T003 is in.
- T012, T013, T014 alongside T011.
- T006 and T007 are in one file, so write them one after the other.

## Parallel Example: after the foundation

```text
Task: "Rooms card source line in composeApp/src/commonMain/kotlin/sonora/multiroom/mobile/ui/rooms/NowPlayingCard.kt"
Task: "Now Playing title in composeApp/src/commonMain/kotlin/sonora/multiroom/mobile/ui/nowplaying/NowPlayingScreen.kt"
```

---

## Implementation Strategy

### MVP First

1. T001–T003.
2. US1 (T004): long names scroll on Rooms.
3. **Stop and validate**: build, all tests, quickstart §2 rows 1–3.

### Incremental Delivery

1. Setup + Foundational.
2. US1, Rooms (MVP).
3. US2, Now Playing.
4. US3, the stale, focus and animation checks and staggered starts.
5. Polish: verification, changelog, AGENTS.md, backlog, and the full app test last.

---

## Notes

- [P] = different files, no dependency on unfinished tasks.
- Verify each test fails before implementing (Constitution IV).
- If a task needs a decision the spec does not cover, stop and ask.
