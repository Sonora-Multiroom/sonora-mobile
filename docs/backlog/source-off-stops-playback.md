# Turning a source off stops its playback: confirm first, and drop "keeps playing"

- **Status:** Specified as [005-source-off-confirm](../../specs/005-source-off-confirm/spec.md)
  (2026-10-16). Written 2026-10-16 after checking the hub's changes since API 0.1.21.
- **Origin:** The hub's `027-device-disable-stops-routes` (API 0.1.25) makes `PUT
  /api/v2/inputs/{id}/enabled` `{enabled: false}` stop every route playing that input, paused ones
  and routes sharing a hardware input included, and then removes an input registered with
  auto-remove. [004's spec](../../specs/004-settings-sources/spec.md) (Clarifications,
  FR-014) was written for the earlier hub, on which a turned-off source kept playing.
- **Depends on:** `api/openapi.json` at 0.1.25 (refreshed 2026-10-16). No hub change.
- **Effort:** Small. One confirmation case, its copy, the removal of one message, and tests.

## Problem

Turning off a source that is playing stops it at once, without a question, and the app then says
*"<name> is off. What's playing from it keeps playing."*
([SettingsText.kt](../../composeApp/src/commonMain/kotlin/sonora/multiroom/mobile/ui/settings/SettingsText.kt),
`keepsPlayingMessage`). Both are wrong now. For a link the user played, the source also vanishes
from the Added list, which the app does not announce either.

Rooms and groups already get a confirmation ("Turning the room off stops playback there").

## Proposal

- Add a `Confirmation.TurnOffSource(source, where)` built like `Confirmation.Remove`: shown only
  when the source plays somewhere, listing the rooms. Body: "<source> is playing in <rooms>.
  Turning it off stops playback there." Confirm label "Turn off", dismiss "Keep playing".
- For an auto-remove source, add: "It is then removed from the hub."
- Delete `keepsPlayingMessage` and the `keepsPlaying` parameter of `SettingsActions.setEnabled`.
- Update 004's spec text (FR-014 and the Clarifications answer) or note the change in the new
  feature's spec.

## Open questions

1. Does a turned-off auto-remove source need its own wording, or is "Remove" the better action to
   offer for it (it ends the same way)?
2. Should the confirmation also cover a paused playback of the source (the hub stops it too)?
