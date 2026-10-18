# UI Contract: Rooms a Playback Is Really On, and Plain Refusal Messages

Feature: [spec.md](spec.md) · Model: [data-model.md](../data-model.md)

The app exposes no interface to other systems. This contract fixes what each screen shows for the
cases the tests (FR-011, FR-012) and the on-device checks ([quickstart](../quickstart.md) §2)
must reproduce. Layout, colours and components are unchanged, except for the one new Move sheet
note.

## Fixture "2026-10-17"

| Rooms (outputs) | id | enabled | available | volume |
|---|---|---|---|---|
| Office Speaker | `office` | **false** | true | 40 |
| Kitchen Speakers | `kitchen` | true | true | 30 |
| Bedroom | `bedroom` | true | true | 20 |
| Bathroom | `bathroom` | true | true | 10 |

| Group | id | members |
|---|---|---|
| Living Room | `living` | `office`, `kitchen` |

| Route | input | target | status | outputs |
|---|---|---|---|---|
| `r1` | KissFM (`http://…`, not pauseable, transferable) | group `living` | ACTIVE | `["kitchen"]` |

Variants: **A** = as above. **B** = A with `outputs` missing. **C** = A after Office Speaker is
turned back on: `office` enabled, `outputs: ["kitchen", "office"]`.

## Expected per screen

| Screen | A (list says kitchen) | B (list missing) | C (rejoined) |
|---|---|---|---|
| Rooms: card | "Living Room", rooms line "Kitchen Speakers", status "Live stream", pill 30 % | "Office Speaker + Kitchen Speakers", pill 40 % (as 005) | "Office Speaker + Kitchen Speakers" (group order, not join order), pill 40 % |
| Rooms: count | "1 of 4 rooms in use" | "2 of 4 rooms in use" | "2 of 4 rooms in use" |
| Rooms: Idle / Off | Bathroom, Bedroom idle; **Office Speaker under "Off"** | Office Speaker not listed (as 005) | Bathroom, Bedroom idle |
| Rooms: group pill dragged to 60 | only `kitchen` is set: 60 | `office` 60, `kitchen` 45 (as 005) | `office` 60, `kitchen` 45 |
| Now Playing heading | "on Living Room · Kitchen Speakers" | "… · Office Speaker + Kitchen Speakers" | "… · Office Speaker + Kitchen Speakers" |
| Now Playing pills | main "Living Room" + member "Kitchen Speakers" | main + Office + Kitchen | main + Office + Kitchen |
| Move sheet: "Kitchen Speakers only" | note **"Nothing stops"** (not a warning) | "Office Speaker stops" | "Office Speaker stops" |
| Move sheet: "Office Speaker only" | "Turned off", unselectable | "Turned off", unselectable | "Kitchen Speakers stops" |
| Move sheet: plain "Office Speaker" row | absent (no duplicate) | absent | absent |
| Start Playback on Office Speaker | Office Speaker is turned off, so it cannot be chosen (unchanged) | same | the notice reads "KissFM will stop in Living Room" (unchanged) |
| Start Playback on Kitchen Speakers | "KissFM will stop in Living Room" (unchanged) | same | same |
| Settings → Rooms: Office Speaker row | "Off" | "Off" | "Playing · KissFM" |
| Settings → Rooms: turn off Kitchen Speakers | dialog names KissFM | same | same |
| Settings → Groups: turn off Living Room | dialog "KissFM is playing on Kitchen Speakers." | "… on Office Speaker, Kitchen Speakers." | "… on Office Speaker, Kitchen Speakers." |

An extra case for the Start Playback notice (FR-001, spec Edge Cases): Bedroom is on, Living Room
= [`bedroom`, `kitchen`], and the hub lists `r1` on `["kitchen"]` only. Start on Bedroom: no
"will stop" line for KissFM. With the list missing, it shows "KissFM will stop in Living Room" as
in 005.

## Copy table (for `MessagesTest` and `DestinationTextTest`)

Starts (`startFailure` + `startFailureMessage`, both kinds unless noted). The target is "Office
Speaker" or "Living Room", the source "KissFM", and `roomName("office")` = "Office Speaker":

| Reason | `outputId` | Kind | Message |
|---|---|---|---|
| `OUTPUT_DISABLED` | `office` | source, link | "Office Speaker is turned off" |
| `OUTPUT_DISABLED` | `ghost` (unknown) | source | "<chosen target> is turned off" |
| `OUTPUT_DISABLED` | none | link | "<chosen target> is turned off" |
| `GROUP_DISABLED` | — | source, link | "Living Room is turned off" |
| `ALL_MEMBERS_DISABLED` | — | source, link | "All rooms in Living Room are turned off" |
| `INPUT_DISABLED` | — | source | "KissFM is turned off" |
| `INPUT_DISABLED` | — | link | "Couldn't start playback" (no source name) |
| `SOMETHING_NEW` (409) | — | source | "Couldn't start playback" |
| `SOMETHING_NEW` (409) | — | link | "Couldn't start playback" |

Every `TurnedOff` start failure has `needsRefresh = true`. No other failure has it.

Moves (`actionErrorMessage(UserAction.Move("Bedroom"), "KissFM", error, roomName)`):

| Reason | `outputId` | Message |
|---|---|---|
| `OUTPUT_DISABLED` | `office` | "Office Speaker is turned off" |
| `OUTPUT_DISABLED` | `ghost` | "Bedroom is turned off" |
| `GROUP_DISABLED` | — | "Bedroom is turned off" (the destination is named whatever it is) |
| `ALL_MEMBERS_DISABLED` | — | "All rooms in Bedroom are turned off" |
| `INPUT_DISABLED` | — | "KissFM is turned off" (defensive: since API 0.1.25 turning a source off stops its playback, so a move normally gets the existing "no longer on the hub" answer) |
| `ROUTE_CHANGING_OUTPUTS` | — | "Couldn't move KissFM to Bedroom. Try again in a moment." |
| `SOMETHING_NEW` | — | "Couldn't move KissFM to Bedroom." |
| (unreachable) | — | "Couldn't move KissFM to Bedroom. Can't reach the hub." |

No message contains the hub's `title` or `detail`.

Move sheet note: `destinationNoteText(NothingStops)` = "Nothing stops", and
`NothingStops.warning` = false.

## Move retry (Now Playing)

| First answer | Second answer | User sees | Transfers sent |
|---|---|---|---|
| `ROUTE_CHANGING_OUTPUTS` | Ok | no message, the sheet closes, the new id is followed | 2, ≥ 1 s apart, same route id and target |
| `ROUTE_CHANGING_OUTPUTS` | `ROUTE_CHANGING_OUTPUTS` | "Couldn't move … Try again in a moment." | 2 |
| `ROUTE_CHANGING_OUTPUTS` | `OUTPUT_DISABLED` | "<room> is turned off" | 2 |
| `ROUTE_CHANGING_OUTPUTS` | unreachable | "… Can't reach the hub." | 2 |
| `ROUTE_CHANGING_OUTPUTS`, screen closed during the wait | — | nothing, on any screen | 1 |
| `OUTPUT_DISABLED` | — | "<room> is turned off" (no retry) | 1 |

While the retry waits, the Move control stays busy, and a refresh that lacks the route does not end
the screen.
