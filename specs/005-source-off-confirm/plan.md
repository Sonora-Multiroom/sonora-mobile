# Implementation Plan: Confirm Before Turning Off a Playing Source

**Branch**: `005-source-off-confirm` | **Date**: 2026-10-16 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/005-source-off-confirm/spec.md`

## Summary

Since hub API 0.1.25, turning a configured source off stops every playback of it. The app still
turns it off without asking and then says it keeps playing. This feature:
- asks first, with the room and group dialog's look: "Turn off <source>?" naming where it plays;
- shows "Playing in <where>" in amber on configured source rows, exactly when their switch will ask;
- removes the "keeps playing" message.

Technically:
- **Contract and repository**: no change. `api/openapi.json` is 0.1.25, and
  `setSourceEnabled` already exists ([R1](research.md#r1-contract-and-the-hub-behaviour)).
- **Domain**: one internal rule, `sourcePlaces`, decides where a source plays. The removal dialog,
  the new `Confirmation.TurnOffSource` and the new `ConfiguredSourceRow.playingIn` all use it
  ([R2](research.md#r2-one-rule-for-where-a-source-plays)–[R4](research.md#r4-the-playing-in-line-on-configured-source-rows)).
- **View model**: the open dialog is refreshed by the rule of its own type, not by the item's kind
  ([R5](research.md#r5-the-dialog-follows-the-hub-while-open)). `keepsPlaying` and its message are
  deleted ([R6](research.md#r6-removing-keeps-playing)).
- **Design first**: the canvas's Sources tab gets the playing line and the dialog before
  implementation ([R7](research.md#r7-design-update-first)).

## Technical Context

**Language/Version**: Kotlin 2.4.20 (KMP), JDK 17+, Gradle 9.8.0, AGP 9.4.1 (unchanged)

**Primary Dependencies**: unchanged from 004 (Compose Multiplatform, Ktor 3.6.0,
kotlinx.serialization, coroutines, DataStore, kotlinx-datetime 0.8.0, OpenAPI Generator 7.14.0).
No additions.

**Storage**: none new.

**Testing**: kotlin-test, kotlinx-coroutines-test, `FakeHub`, all in `commonTest`. No new
repository calls, so no new `MockEngine` tests.

**Target Platform**: Android (minSdk 26). The iOS targets are declared but not built.

**Project Type**: mobile app (KMP: `composeApp` + `androidApp` + `iosApp` stub)

**Performance Goals**: none new. The dialog decision is made from the snapshot already in memory.
A confirmed turn-off shows on Rooms at the next refresh, as in 004.

**Constraints**:
- The app stops nothing itself: the hub stops the playbacks.
- One poll loop app-wide.
- Nothing Android-only in `commonMain`.
- 004's spec stays unedited.

**Scale/Scope**: ~5–30 configured sources. The work touches 6 production files in
`commonMain` and 5 test suites, plus one design screen.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | How |
|---|---|---|
| I. API Contract Fidelity | ✅ | Contract refreshed to 0.1.25 and reconciled: the build and 587 host tests passed on it (PR #10). The only call is the existing `setSourceEnabled`, through `HubRepository`. The behaviour relied on is in the 0.1.25 contract text ([R1](research.md#r1-contract-and-the-hub-behaviour)) |
| II. Truthful UI | ✅ | Fixes a false message. "Playing in" and the dialog come from hub state through one rule, so the line, the dialog and the hub agree ([R2](research.md#r2-one-rule-for-where-a-source-plays)). The switch stays on until confirmed, then follows 004's fenced flow |
| III. Shared-First | ✅ | All changes are in `commonMain`. No platform source-set changes |
| IV. Test-First | ✅ | `sourcePlaces`, `TurnOffSource`, `playingIn`, the copy, the actions and the view-model flow each get failing tests first ([quickstart](quickstart.md) §1) |
| V. Resilient LAN Networking | ✅ | No new requests. Stale or unreachable states keep 004's rule: switches are disabled, so no dialog opens |
| VI. Design Fidelity & Accessibility | ✅ | The canvas is updated first (FR-010, [R7](research.md#r7-design-update-first)). The dialog reuses the existing component with its 44 dp buttons. Amber `#F2A541` on surface `#17191E` is ≈ 8.6:1, which passes 4.5:1. The line is text, so TalkBack reads it |
| VII. Minimal Dependencies | ✅ | None added |

No violations, so Complexity Tracking stays empty.

**Post-design re-check (after Phase 1)**: unchanged, all ✅. The data model adds one field and one
confirmation case. The UI contract delta reuses existing components and tokens only.

## Project Structure

### Documentation (this feature)

```text
specs/005-source-off-confirm/
├── plan.md              # This file
├── research.md          # Phase 0
├── data-model.md        # Phase 1
├── quickstart.md        # Phase 1
├── contracts/
│   └── settings-ui.md   # UI delta to 004's Settings contract
└── tasks.md             # Phase 2 (/speckit-tasks)
```

### Source Code (repository root)

```text
gradle.properties                                   # 0.5.0-alpha / 5
design/screens/Settings.dc.html                     # copy of the updated canvas (before code)
composeApp/src/commonMain/kotlin/sonora/multiroom/mobile/
├── domain/
│   ├── SettingsConfirm.kt                          # + sourcePlaces, + TurnOffSource,
│   │                                               #   source case of turnOffConfirmation,
│   │                                               #   removeConfirmation via sourcePlaces, − keepsPlaying
│   └── SettingsBuilder.kt                          # ConfiguredSourceRow.playingIn
├── ui/session/SettingsActions.kt                   # − keepsPlaying parameter and message
└── ui/settings/
    ├── SettingsText.kt                             # TurnOffSource copy, "Playing in", − keepsPlayingMessage
    ├── SettingRows.kt                              # configured row: accent colour while playing
    └── SettingsViewModel.kt                        # refresh by confirmation type; − keepsPlaying
composeApp/src/commonTest/kotlin/sonora/multiroom/mobile/
├── domain/SettingsConfirmTest.kt, SettingsBuilderTest.kt
└── ui/session/SettingsActionsTest.kt, ui/settings/SettingsTextTest.kt, SettingsViewModelTest.kt
```

**Structure Decision**: the existing KMP layout. The feature changes 004's Settings files in place
and adds no new file in `commonMain`.

## Complexity Tracking

None.
