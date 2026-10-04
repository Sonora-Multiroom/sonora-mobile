# Research: Settings Tabs and Sources (004)

Phase 0 of [plan.md](plan.md). Each item gives a Decision, its Rationale and the Alternatives
considered. Items from 001–003 still apply and are referenced rather than repeated: the one
`HubSession` poll loop, the fence rule on `startedSeq`, `AppMessages`, error copy only in
`ui/Messages.kt`, `inferSourceKind` as the only kind rule, and the app-scoped runner pattern of
`PlaybackStarter`.

## R1. Contract and the hub dependency

**Decision**: No contract change. `api/openapi.json` was re-fetched from the hub's `/api-docs` on
2026-10-04 with `./gradlew refreshOpenApi` and is identical (0.1.21). Every call this feature adds
is already documented and already generated (the `Extensions` tag is in the generator's `FILTER`):

| Call | Generated | Documented answers |
|---|---|---|
| `PUT /api/v2/outputs/{id}/enabled` `{enabled}` | `OutputsApi.setOutputEnabled` | 200 `OutputResponse`, 400, 404 |
| `PUT /api/v2/groups/{id}/enabled` `{enabled}` | `GroupsApi.setGroupEnabled` | 200 `GroupResponse`, 400, 404 |
| `PUT /api/v2/inputs/{id}/enabled` `{enabled}` | `InputsApi.setInputEnabled` | 200 `InputResponse`, 400, 404 |
| `DELETE /api/v2/inputs/{id}` | `InputsApi.deleteInput` | 204, 400 (a configured source), 404 |
| `GET /api/v2/extensions` | `ExtensionsApi.listExtensions` | 200 `ExtensionInventory` |
| `GET /api/v2/outputs?includeDisabled=true` (connection test) | `OutputsApi.listOutputs` | 200 array of `OutputResponse` |

The three `/enabled` descriptions still read "Existing active routes are unaffected". The spec
targets the changed hub (turning off a room or group stops its playback; Clarifications
2026-10-04). The app does not depend on this to build: it never stops anything itself and only
reflects the hub's state. **Before on-device verification**, `./gradlew refreshOpenApi` runs
again. If the contract changed, every affected call is reconciled first (Constitution I).

**Rationale**: Constitution I (documented calls only, reconcile before use). The hub change alters
behaviour, not the wire format, so the generated code is the same either way.

**Alternatives considered**: version-gated wording (rejected in the spec clarification: the hub
has no capability flag).

## R2. One Settings destination, tab memory and the bottom bar

**Decision**:
- `Destination.Sources` and its placeholder entry are removed. The bottom bar keeps three items,
  but its Sources item calls `onOpenSources`, which selects the Sources tab and then
  `backStack.selectTab(Destination.Settings)`, so Settings is highlighted (FR-003). The saver
  decodes an old `"sources"` entry as `Settings`.
- An app-scoped **`SettingsNavigator`** (in `AppGraph`) holds `tab: StateFlow<SettingsTab>`
  (Rooms at start, FR-002) and a one-shot `openSheetRequested` flag. The tab outlives the
  Settings entry, which `selectTab` pops whenever the user switches tabs, and lasts for as long as
  the process runs. It is not saved across process death, because FR-002 says "for as long as the
  app runs".
- Rooms' and Start Playback's "Set your hub address" action calls `settingsNavigator.openSheet()`
  and then selects the Settings tab. The Settings screen consumes the flag when it first shows.

**Rationale**: the tab state has to survive the view model being cleared on a tab switch, and an
object in `AppGraph` is the existing way to do that (`PlaybackStarter`, `AppMessages`).

**Alternatives considered**: a `Destination.Settings(tab)` data class (rejected: Settings is a
tab root, `selectTab` compares by equality, and the bottom bar would then need to know the tab);
`SavedStateHandle` (rejected: cleared together with the entry).

## R3. Settings content: one pure builder

**Decision**: `domain/SettingsBuilder.kt` turns a `HubSnapshot` (plus `now` and a time zone for
R10) into `SettingsContent` with four lists: `roomRows`, `groupRows`, `configuredSources` and
`runtimeSources`. Every rule of FR-009–FR-011 and FR-015 lives here, written test-first:
- **Room status**, in order: `Off` (`!enabled`), `NotConnected` (`!available`),
  `Playing(sources)` (any live route covering the room, from `routesByRoom()`, so its own and
  group routes, in any state except Stopped), `InGroups(names)` (every group whose `memberIds`
  contains it, A→Z), `Speaker`. Several playing sources are listed in hub order without
  duplicates.
- **Group rows**: members in the group's `memberIds` order, unknown ids skipped. The second line
  is `playing` = the sources of live routes whose `target` is `Target.Group(id)`, only when the
  group is on.
- **Source split**: `origin == Runtime` → runtime. `Configured` and `Unknown` → configured (spec
  Edge Cases). Configured sources are sorted A→Z case-insensitive with the id as the tie-break.
  Runtime sources are sorted newest first by `createdAt`, with undated ones last and A→Z
  ([R10](#r10-added-when-and-the-time-zone)).
- Rows carry ids, names, flags and structured status values. The strings are made in
  `ui/settings/SettingsText.kt`, as in 003 (`StartPlaybackText`).

**Rationale**: Constitution II (each derived value is decided in one documented place) and IV
(tests first). `routesByRoom()` (003) already sees several playbacks per room. `occupancy()` would
hide a second source.

**Alternatives considered**: computing status lines in the view model (rejected: they could not
be unit-tested without the view model, and they would drift from Rooms).

## R4. When to ask, and the dialog text

**Decision**: `domain/SettingsConfirm.kt` has pure functions over the latest snapshot:
- `turnOffConfirmation(item, snapshot): Confirmation?`. A room that is on and covered by any live
  route (R3), or a group that is on and has its own live route → `Confirmation(title, sources,
  where)`. Otherwise `null`, so the change is sent at once. Turning on always gives `null`.
- `removeConfirmation(sourceId, snapshot): Confirmation?`. Non-null when any live route has
  `inputId == sourceId`. `where` lists each such route's room or group name (via
  `describeTarget`), in hub order and without duplicates.
- `keepsPlaying(sourceId, snapshot): Boolean`. Used when a source is turned off (FR-014, last
  sentence).

The dialog's state in the view model holds only the item's key and kind. Its text is rebuilt from
each new snapshot. When the item no longer plays, the last text stays (spec Edge Cases: "the
dialog stays"). "Turn off"/"Remove" sends the request whatever the snapshot says at that moment,
because that is what the user asked for. The check that decides whether to ask uses the snapshot
at the time of the tap (FR-017).

**Rationale**: SC-004 (never stop playback without asking) needs the decision to be testable on
its own, apart from the UI.

**Alternatives considered**: freezing the text when the dialog opens (rejected by the spec: "the
dialog text follows at the next refresh").

## R5. Switch flow: app-scoped, optimistic, fenced

**Decision**: An app-scoped **`SettingsActions`** (`ui/session/`, in `AppGraph`, `appScope`) runs
every switch change and removal, so a request outlives the screen (spec Edge Cases):
- `pending: StateFlow<Map<ItemKey, Pending>>`, with `ItemKey(kind: Room|Group|Source, id)`
  and `Pending(value: Boolean, phase)`, where `phase` is `InFlight` or `AwaitingRefresh(fence)`.
- `setEnabled(key, name, value)`: ignored while `key` is pending (FR-012, one request at a time).
  Otherwise it records `InFlight`, so the switch shows `value` at once, and calls the repository.
  - **Success**: the phase becomes `AwaitingRefresh(session.startedSeq)` and `requestRefresh()` is
    called. The override is dropped by the first snapshot whose `refreshSeq > fence` (the 002
    fence rule). From then on the hub's state decides (FR-012: "the hub's answer then decides").
    A refresh already in flight cannot overwrite the user's value, which avoids a flicker.
  - **Failure**: the override is dropped at once and a message is reported (R7, FR-013). A 404
    also calls `requestRefresh()`.
- The view model merges `pending` over the builder's rows. A row is shown as `inFlight`, and
  cannot be flipped, while it is `InFlight`.
- The "keeps playing" message (FR-014) is decided at the tap with `keepsPlaying()` and shown only
  if the request succeeds.
- Overrides and in-flight state are cleared when the hub address changes (as `VolumeDragController`
  does).

**Rationale**: the same optimistic-then-confirm rule as volume and mute (Constitution II: "while
the user is interacting… its local value wins; after… the next refresh reconciles it"). It is
app-scoped because Settings is popped on every tab switch.

**Alternatives considered**: using the `PUT` response body (`OutputResponse.enabled`) as the
answer (rejected: one source of truth, the snapshot; the body would need a second mapping path);
keeping it in the view model (rejected: the request and its message would be lost on a tab
switch).

## R6. Removal flow

**Decision**: `SettingsActions.remove(sourceId, name)`. The row is `Removing` (trash disabled,
row dimmed with a "Removing…" line) until the request completes:
- 204 **or 404** → success: `removed += id`, which hides the row at once, then `requestRefresh()`.
  The id stays hidden until a fenced refresh no longer lists it, so an old snapshot cannot bring
  the row back.
- 400 → "<name> comes from the hub's configuration and can't be removed". `Unreachable` →
  "Couldn't reach the hub". Anything else → "Couldn't remove <name>" (FR-018).

The confirmation check (R4) runs before `remove`. A removal does not wait for the playback to
stop, because the hub stops it (spec Assumptions).

**Rationale**: FR-018 and the spec Edge Case "a trash tap on it then is treated as success".

**Alternatives considered**: hiding the row only after the next refresh (rejected: FR-018 says the
row disappears on success).

## R7. Where messages appear

**Decision**: `SettingsActions` has its own one-shot `messages` channel and an `attached` flag
that the Settings view model sets while the screen is visible (`onVisible`/`onHidden`, as for
polling). When attached, messages go to the Settings snackbar. When detached, they are posted to
`AppMessages`, which Rooms shows (the `PlaybackStarter.detach` pattern). The only way out of
Settings is a tab switch, so "detached" in practice means "on Rooms". The rare case of opening Now
Playing within the 3 s of a request shows the message when Rooms is next visible. This is
recorded in the UI contract.

All copy is in `ui/Messages.kt` as new `SettingsFailure` cases and `settingsFailureMessage()`, with
no hub wording (Constitution V). The new strings follow the spec exactly, with no trailing full
stop, as in 003.

**Rationale**: reuses the existing one-consumer `AppMessages` without moving every screen to an
app-level snackbar host.

**Alternatives considered**: one app-level `SnackbarHost` in `AppNavigation` for every screen
(rejected for now: it would refactor 001–003 screens and their tests for a corner case).

## R8. Extensions: piggybacking on the session's refreshes

**Decision**: A new repository method `extensions(): HubResult<ExtensionInventory>` (a domain
type). The Settings view model calls it **once when the Extensions tab is shown and then after
each successful session refresh while that tab is visible**. It is never called on any other tab
or screen. A failure keeps the last inventory, since the hub row already shows the connection
state. Before the first answer, the tab shows only its read-only note. Mapping:

| Wire | Domain |
|---|---|
| `status` `ACTIVE`/`DISABLED`/`REJECTED`/`INERT` | `Active`/`Disabled`/`Rejected`/`Inactive`. Missing or unrecognised (coerced to `null` by `HubJson`) → `Unknown` |
| `connectionState` `CONNECTED`/`DISCONNECTED`/`NOT_APPLICABLE` | `Connected`/`Disconnected`/`NotApplicable`. Missing or unrecognised → `Unknown` |
| `name` | blank → `id`. Both blank → the row is dropped |
| `rejectionReason`, `version`, `requiredApiVersion`, `extensionsDirectory` | not mapped (FR-019: never shown) |
| `loadingEnabled` | missing → `true` (show the list) |

The badge and connection line (FR-019) are decided in `extensionRow()` in
`domain/SettingsBuilder.kt`. Rejected/Disabled/Inactive override the connection line.

**Rationale**: AGENTS.md says "Screens never run their own loop". Tying the call to the session's
refreshes keeps one schedule (FR-004) without adding a sixth GET to every Rooms poll, and leaves
`HubSnapshot`'s all-or-nothing rule untouched. The list is fixed at hub start-up, and only the
connection states change.

**Alternatives considered**: adding extensions to `snapshot()` while a flag is set (rejected: it
changes `HubSession`, `FakeRepository` and the all-or-nothing rule for every screen);
fetching once per visit (rejected: FR-019 scenario 2 needs connection changes to show up).

## R9. Hub row and connection test

**Decision**:
- **Hub row status** (`hubRowStatus(SessionState)` in `SettingsText`): `NoAddress` → "Not set" with
  the address line "Set the hub address to start". `Connected` + `Loading` → "Connecting…".
  `Live` → "Connected" (teal). `Unreachable` → "Not connected" (red). `Initial` → the row is drawn
  without a status. The address is `HubAddress.baseUrl` (already normalised), on one line with an
  ellipsis.
- **Connection test**: a new repository method `countRooms(): HubResult<Int>` =
  `GET /api/v2/outputs?includeDisabled=true` with the shared 3 s client. N = the number of
  entries that map to a `Room` (FR-007: every room, on or off). The draft is parsed with
  `HubAddress.parse` (001). Invalid → the 001 message under the field, with no test. Valid →
  `repositoryFactory.create(draft).countRooms()`. `Ok(n)` → "Hub found · n room(s)". Any `Err`,
  including a 200 body that does not decode as a list of outputs (`Unexpected`) → "Can't reach the
  hub at this address".
- The test job lives in the Settings view model. Editing the field, Save, Close or Back cancel it
  and clear the result. A new test cancels the previous one. Nothing is saved.
- **Save** keeps 001's behaviour (`store.save(normalised)`). `HubSession` already reacts to the
  address flow. The sheet closes on Save, and Close/Back discard the draft (FR-008).

**Rationale**: FR-007 defines "found" as the hub's room list answering. An HTML page or another
JSON API fails to decode as `List<OutputResponse>`. A JSON array of unrelated objects maps to 0
rooms. This is the only false positive left, and it is accepted as harmless.

**Alternatives considered**: `GET /api-docs` and checking the title (rejected: not a v2 path,
Constitution I); a full `snapshot()` (rejected: five requests, and the count is all it needs).

## R10. "Added <when>" and the time zone

**Decision**:
- Mapping: `InputResponse.createdAt` (a string, since the generator uses `dateLibrary=string`) →
  `Source.createdAt: kotlin.time.Instant?` via `Instant.parse`. Missing or unparseable → `null`.
  `autoRemove` → `Source.autoRemove: Boolean` (missing → `false`).
- Wording (`addedLine` in the builder, test-first, with injected `now` and `TimeZone`): same local
  date as now → "Added today HH:mm"; the day before → "Added yesterday HH:mm"; otherwise "Added
  d MMM HH:mm" with English month abbreviations ("2 Oct 14:30"), 24-hour time and no year. It then
  appends " · removed when it stops" when `autoRemove`, and prepends "Off · " when turned off.
- **Undated runtime source** (`createdAt == null`, not covered by the spec): the "Added <when>"
  part is dropped and the other parts stay ("Off · removed when it stops"). With nothing left,
  the line is omitted. Sorted after the dated ones, A→Z. Listed under plan "Open points".
- Local time comes from **kotlinx-datetime 0.8.0** (`TimeZone.currentSystemDefault()`,
  `Instant.toLocalDateTime`), which works on Android and iOS. The month names are a fixed English
  table, because the app is English-only.

**Rationale**: FR-015 asks for the phone's time zone. Common code has no time-zone access without
this library or platform code.

**Alternatives considered**: an `expect fun localOffset()` in `androidMain`/`iosMain` (rejected:
it duplicates what a JetBrains multiplatform library does, and DST rules are easy to get wrong).
Relative wording such as "3 h ago" (rejected: the spec gives the format).

## R11. Source detail line

**Decision**: A new `sourceDetail(kind, uri): String?` in `domain/SourceKind.kt`, next to
`addressDetail`. Stream and File delegate to `addressDetail` (host, file name). **Line-in** gives
the address without its scheme (`alsa://plughw:CARD=X,DEV=0` → `plughw:CARD=X,DEV=0`). An address
with no scheme is shown as typed. A blank address gives `null`, and the row then shows only the
kind label. Now Playing keeps `addressDetail`, where line-in has no detail.

**Rationale**: FR-011 wants a detail for line-ins, which Now Playing deliberately omits (002 R7).
Two named functions in one file keep both rules visible.

**Alternatives considered**: changing `addressDetail` (rejected: it would change Now Playing).

## R12. UI building blocks

**Decision**:
- **Tokens** (`Tokens.kt`; the spec's Assumptions list them): `danger #FF8A7A`,
  `dangerContainer #3A1A16`, `onDanger #2A0D08`, `positive #5FD3C4`, `positiveContainer #12302E`
  (same values as the line-in kind, but named for status), `chevron #6E717A`, `rowDivider #22252B`,
  `switchTrackOff #2C3038` (= outline), `switchThumbOff #9A9DA6` (= textMuted). `scrim`
  (`0xB8050608` = rgba(5,6,8,0.72)) and `textSoft #C9CBD1` already exist. `ContrastTest` gains the
  new text pairs: danger on dangerContainer, onDanger on danger, positive on positiveContainer,
  textSoft on surfaceRaised, accent on surface.
- **Icons** (from the design's inline SVG): `Server` (hub row), `Trash`, `Chevron`, `GroupStack`
  (the Settings group tile, which differs from Rooms' `Group`) and `StopOutline` (the dialog
  tile).
- **Switch**: a small `SonoraSwitch` (48×28 track, 22 thumb, design colours). The **whole row** is
  `Modifier.toggleable(role = Role.Switch)` with merged semantics, so TalkBack reads "<name>,
  switch, on/off" (FR-023), and the row is at least 60 dp tall. Material3 `Switch` is 52×32 with
  its own thumb rules.
- **Tabs**: a 4-way segmented row (`Role.Tab`, `selected`), 40 dp buttons inside a 4 dp padded
  48 dp bar. The whole bar meets the 44 dp target.
- **Hub address sheet**: `ModalBottomSheet` with `scrimColor = colors.scrim`, as 002's Move sheet.
- **Dialog**: `androidx.compose.ui.window.Dialog` with custom content (tile, Sora 20 title, body,
  two buttons). It has `semantics { paneTitle = title }`, and the body is merged into the
  announcement. Back and a tap outside dismiss it (FR-014). The window's dim comes from the
  platform. If it differs visibly from the `scrim` token, that is recorded in `verification.md`
  and not worked around with platform code.

**Rationale**: Constitution VI (design fidelity, 44 dp, contrast, announced roles). Constitution
III (nothing Android-only in common code).

**Alternatives considered**: an in-layout overlay for the dialog (rejected: it cannot cover the
bottom bar, which `AppNavigation` draws outside the entry).

## R13. Dependencies

**Decision**: one new library, **`org.jetbrains.kotlinx:kotlinx-datetime:0.8.0`** (latest stable
on Maven Central, 2026-10-04), `commonMain` only, version in `libs.versions.toml` (R10). Nothing
else: the generated client, Ktor, Compose and material3 already cover the rest.

**Rationale**: Constitution VII. It is a JetBrains multiplatform library and blocks no iOS target.

## R14. Housekeeping

**Decision**:
- First commit: `sonora.versionName=0.4.0-alpha`, `sonora.versionCode=4`.
- AGENTS.md sync: add the connection test (`GET /outputs`) and `GET /extensions` to the "Settings"
  row of the endpoint table, list `SettingsActions`/`SettingsNavigator` under `ui/session/` in the
  layout, and remove "Sources" as a placeholder wherever it is mentioned.
- 001's `SettingsViewModelTest` is rewritten for the new view model. Its address rules (parse,
  normalise, "don't overwrite typing") move to the sheet's tests unchanged.
