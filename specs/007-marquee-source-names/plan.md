# Implementation Plan: Scroll Long Source Names

**Branch**: `007-marquee-source-names` | **Date**: 2026-10-20 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/007-marquee-source-names/spec.md`

## Summary

Since hub API 0.1.28 a played link is named after its resolved title, which is often too long for
the Rooms card's source line and the Now Playing title, and the "…" hides the part that tells
tracks apart. This feature makes a name that does not fit scroll by itself as a marquee: on the
Rooms card when it is longer than one line, and on Now Playing when it is longer than two.

Technically:
- **One composable**, `MarqueeText` in `ui/rooms/MarqueeText.kt`, measures the name at its width
  and `maxLines` and draws it in one of three layouts: fits, truncated with "…", or scrolling
  ([R4](research.md#r4-deciding-whether-a-name-fits-and-the-now-playing-two-line-rule)).
- **Scrolling** is Compose foundation's `basicMarquee`, set to repeat forever (its default stops
  after 3 cycles), 30 dp/s, 1.2 s pauses, with 12 dp faded edges
  ([R1](research.md#r1-the-marquee-itself), [R6](research.md#r6-faded-edges-fr-004)).
- **When it may scroll** is one pure function: not stale, window focused, animations on. The app
  checks the last two itself, because `basicMarquee` 1.12.1 ignores both window focus and "Remove
  animations" ([R2](research.md#r2-window-focus-and-background-fr-007),
  [R3](research.md#r3-remove-animations-fr-008)).
- **Staggered starts** on Rooms: the initial delay depends on the card's position
  ([R5](research.md#r5-not-moving-in-step-fr-009)).
- **No** domain, repository, hub call or dependency change.

## Technical Context

**Language/Version**: Kotlin 2.4.20 (KMP), JDK 17+, Gradle 9.8.0, AGP 9.4.1 (unchanged)

**Primary Dependencies**: unchanged (Compose Multiplatform 1.12.1, Ktor, kotlinx.serialization,
coroutines, DataStore, OpenAPI Generator). `basicMarquee`, `rememberTextMeasurer`,
`LocalWindowInfo` and `MotionDurationScale` are all in Compose foundation/UI, already
dependencies, all in common code.

**Storage**: none.

**Testing**: kotlin-test in `commonTest` for the pure functions. Drawing, measuring and timing are
checked on the device (Compose UI tests remain optional and are not set up in this project).

**Target Platform**: Android (minSdk 26). The iOS targets are declared but not built.

**Project Type**: mobile app (KMP: `composeApp` + `androidApp` + `iosApp` stub)

**Performance Goals**: smooth scrolling at the display's frame rate. Text is measured once per
name, style and width (remembered), not per frame and not per refresh. The marquee only redraws
(it changes a draw offset, not layout), and the offscreen layer for the fade exists only while a
name scrolls.

**Constraints**:
- One poll loop app-wide; the 2.5 s refresh must not restart a marquee (FR-005).
- Nothing Android-only in `commonMain`: the animation scale is read from Compose's
  `MotionDurationScale`, not from `android.provider.Settings`.
- Names that fit must look exactly as before (SC-005).

**Scale/Scope**: 1 new production file, 2 edited (`NowPlayingCard.kt`, `NowPlayingScreen.kt`) plus
`RoomsScreen.kt` for the card index, 1 new test file, `gradle.properties`, AGENTS.md, the backlog
and the full app test.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | How |
|---|---|---|
| I. API Contract Fidelity | ✅ | No hub call, no new field. The name is the input's `displayName`, already mapped in `ApiMapping.kt` from the 0.1.28 contract, already in `api/openapi.json` |
| II. Truthful UI | ✅ | No progress bar or position: the marquee moves only the name and never suggests playback progress. A stale state does not move (FR-006), so a frozen screen does not look alive. Pause/Move rules are untouched |
| III. Shared-First | ✅ | Everything in `commonMain` / `commonTest`. Window focus and the animation scale come from Compose's common APIs ([R2](research.md#r2-window-focus-and-background-fr-007), [R3](research.md#r3-remove-animations-fr-008)); no `expect`/`actual` |
| IV. Test-First | ✅ | The three pure functions (`marqueeLayout`, `canScroll`, `marqueeStartDelay`) get failing tests first ([R8](research.md#r8-testing)). No hub-data-to-state logic changes. Build and all tests gate the change |
| V. Resilient LAN Networking | ✅ | Unaffected. The stale state is reused as is |
| VI. Design Fidelity & Accessibility | ✅ | The departure (scrolling instead of "…") is called out in the spec (FR-013), as the principle allows; the canvas cannot show movement and stays. Colours and type tokens unchanged, so contrast is unchanged. The full name stays in the text node for TalkBack (FR-011); no focus stop or touch target is added. "Remove animations" is honoured (FR-008) |
| VII. Minimal Dependencies | ✅ | None added |
| Full app test gate | ✅ planned | The final task updates `docs/testing/full-app-test.md`: rows for quickstart §2, header to 007 / 0.7.0-alpha / API 0.1.28 |

No violations, so Complexity Tracking stays empty.

**Post-design re-check (after Phase 1)**: unchanged, all ✅. The design adds presentation types only
([data-model.md](data-model.md)); the UI contract reuses existing styles and colours
([contracts/ui-behaviour.md](contracts/ui-behaviour.md)).

## Project Structure

### Documentation (this feature)

```text
specs/007-marquee-source-names/
├── plan.md              # This file
├── research.md          # Phase 0
├── data-model.md        # Phase 1
├── quickstart.md        # Phase 1
├── contracts/
│   └── ui-behaviour.md  # where it applies; behaviour table; accessibility
├── checklists/requirements.md
└── tasks.md             # Phase 2 (/speckit-tasks)
```

### Source Code (repository root)

```text
gradle.properties                                   # 0.7.0-alpha / 7
composeApp/src/commonMain/kotlin/sonora/multiroom/mobile/ui/
├── rooms/
│   ├── MarqueeText.kt                              # new: MarqueeText, MarqueeLayout, marqueeLayout,
│   │                                               #   canScroll, marqueeStartDelay
│   ├── NowPlayingCard.kt                           # source line → MarqueeText(maxLines = 1)
│   └── RoomsScreen.kt                              # itemsIndexed: pass the card's start delay
└── nowplaying/NowPlayingScreen.kt                  # title → MarqueeText(maxLines = 2)
composeApp/src/commonTest/kotlin/sonora/multiroom/mobile/ui/rooms/
└── MarqueeTextTest.kt                              # new
AGENTS.md                                           # "Agreed details": the marquee line
docs/backlog/ (delete the item + INDEX row), docs/testing/full-app-test.md
```

**Structure Decision**: the existing KMP layout. `MarqueeText` sits in `ui/rooms/` next to
`VolumePill.kt`, which Now Playing already uses from there.

## Complexity Tracking

None.
