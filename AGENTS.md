# sonora-mobile

Mobile remote control for the **Multiroom Audio Hub**, a Java/Spring Boot server on the home LAN
that routes audio from inputs (internet streams, files, line-in, SoundCloud/YouTube links) to
outputs (speakers) and groups of outputs. The app talks only to the hub's REST API v2.

The hub lives in another repository (`multiroom-ai`) that is **not reachable from cloud sessions**,
and neither is the hub itself (`http://multiroom.lan:8080`). Everything you need from it is in this
repo: [api/openapi.json](api/openapi.json) and this file.

## Decisions (made 2026-10-01, do not revisit without asking)

- **Kotlin Multiplatform + Compose Multiplatform**, UI shared in `commonMain`.
- **Android first.** Keep an iOS target in the project but do not build it; iOS builds will come
  later on a GitHub Actions macOS runner. Nothing Android-only goes in `commonMain`.
- **No backend changes from this repo.** If the API lacks something, note it under
  "Hub gaps" below and work around it.
- Package / applicationId: `sonora.multiroom.mobile`, after the product name Sonora Multiroom
  (renamed from `ai.sonora.mobile` on 2026-10-03; change only if the user asks).

## Project layout (target)

Standard JetBrains KMP wizard layout:

```
settings.gradle.kts, build.gradle.kts, gradle/libs.versions.toml, gradlew
api/openapi.json                       # hub contract, input to code generation
composeApp/
  src/commonMain/kotlin/               # theme, API client wrapper, repository, screens
  src/commonMain/composeResources/font # sora.ttf, dm_sans.ttf (already committed, variable fonts)
  src/commonTest/kotlin/
  src/androidMain/                     # platform drivers only (HTTP engine, storage path)
  src/iosMain/                         # stub
androidApp/                            # MainActivity, AndroidManifest, network_security_config
iosApp/                                # Xcode project stub (not built)
design/screens/*.dc.html               # design source, see "Design"
docs/licenses/                         # OFL licences for the bundled fonts
```

Use the current stable versions of Kotlin, Compose Multiplatform, AGP and Ktor; look them up, do
not rely on memory. The cloud environment provides JDK 17+ and the Android SDK via `ANDROID_HOME`.

## Commands

```bash
./gradlew :androidApp:assembleDebug         # build the Android APK
./gradlew :composeApp:testAndroidHostTest   # Android host tests
./gradlew :composeApp:allTests              # all KMP tests (iOS targets skipped on Linux)
```

There is no emulator and no hub in the cloud. Verification here = the build passes and unit tests
pass. The user installs the APK and tries it against the real hub locally.

## API

- [api/openapi.json](api/openapi.json) was fetched from the production hub on 2026-10-01
  (API version **0.1.20**). It also contains v1 paths (`/api/*` without `v2`) and TTS extension
  paths (`/api/tts/*`): **use only `/api/v2/**`**.
- Generate a Kotlin multiplatform client (Ktor + kotlinx.serialization) from it at build time,
  e.g. OpenAPI Generator `kotlin` with `library=multiplatform`. Do not commit generated code. Wrap
  it behind a small hand-written repository interface so screens never touch generated types.
- No authentication. Plain HTTP on the LAN. Base URL comes from the user (see Settings).
- Errors on v2 are RFC 7807 problem details.

### Endpoints per screen

| Screen | Calls |
|---|---|
| Rooms | `GET /api/v2/outputs`, `/groups`, `/routes`, `/inputs`, `GET/PUT /api/v2/master-mute`. Outputs, groups and inputs are requested with `includeDisabled=true`; the hub omits disabled ones by default, and Rooms must show them as "Off" |
| Volume / mute | `PUT /api/v2/outputs/{id}/volume` `{volume}`, `.../mute` `{muted}`; group mute under `/groups/{id}/mute`. Group volume = per-member output PUTs (see Hub gaps) |
| Stop | `DELETE /api/v2/routes/{routeId}` |
| Pause / resume | `PUT /api/v2/routes/{routeId}/pause` `{paused}`, **only when `RouteResponse.pauseable`** |
| Start playback (link) | `POST /api/v2/play` `{uri, targetId, targetType, displayName?, volume?}` |
| Start playback (configured source) | `POST /api/v2/routes` `{inputId, targetId, targetType}` |
| Move to room | `POST /api/v2/routes/{routeId}/transfer` `{targetId, targetType}`, only when `transferable` |
| Settings | `PUT /api/v2/{outputs,groups,inputs}/{id}/enabled` `{enabled}`, `DELETE /api/v2/inputs/{id}`, `GET /api/v2/extensions` |

`targetType` is `SINGLE_OUTPUT` or `OUTPUT_GROUP`.

### Domain rules the UI depends on

- An output plays **at most one route**. A group route occupies every member output. A route's
  `targetId` names only what was addressed, so to find "what plays on output X" check both
  single-output routes on X and group routes whose group contains X.
- `enabled = false` on an output/group/input means "cannot start new playback"; show it as "Off".
- `OutputResponse.available = false` means hardware not connected.
- Inputs with `source = EPHEMERAL` were added at runtime (links played, DLNA); `STATIC` come from
  configuration. Only ephemeral ones get a delete action.
- There is **no "kind" field** on inputs (stream / line-in / file). Infer it from `uri`
  (`http(s)` → stream, `file:` or a path → file, otherwise line-in) and keep that in one function.
- There is **no track metadata or artwork** and **no playback position**. Never draw a progress
  bar; the only sliders are volume.

### Hub gaps (known, not fixable here)

- No push channel: poll every ~2.5 s while the app is in the foreground, stop in background.
- No input kind field (see above).
- No mDNS announcement of the REST API: the hub address is a manual setting.
- No group volume: `GroupResponse` has no `volume`, and `PUT /api/v2/groups/{id}/volume` sets every
  member to the same absolute value, destroying the balance between rooms. **Never call it.** A
  group pill shows the loudest member; dragging scales each member by new/old loudest (rounded,
  clamped 0–100) via `PUT /api/v2/outputs/{id}/volume`, throttled; if all members are 0, each is
  set to the new value.

## Design

Source of truth: Claude Design canvas https://claude.ai/artifact/R7e4yABDYUuytc64XxNNKK
(read it with the Artifact tool if available). Offline copies: [design/screens/](design/screens/)
— each `.dc.html` is one 390×844 phone screen; the inline styles hold exact sizes and colours, the
`<script>` block holds the sample data and interaction.

Screens: `Main` (Rooms), `NowPlaying`, `StartPlayback`, `Transfer` (Move to room bottom sheet),
`Settings` (tabs Rooms / Groups / Sources / Extensions).

Tokens:

| Token | Value | Use |
|---|---|---|
| background | `#0E0F12` | screen |
| surface | `#17191E` | cards, sheets |
| surfaceRaised | `#22252B` | pill track, icon tiles |
| outline | `#2C3038` | inactive switch, borders |
| text | `#F2F1EE` | primary text |
| textMuted | `#9A9DA6` | secondary text |
| accent | `#F2A541` | primary actions, active state |
| onAccent | `#1A1206` | text/icons on accent |
| accentContainer | `#4A3412` | volume pill fill |
| selected | `#1F1A12` bg, `#6B4C1F` outline | selected rows |
| warning text | `#F2D3A4` | "will stop" notes |
| kind: stream | `#3A2A12` / `#F2A541` | source tile bg / icon |
| kind: line-in | `#12302E` / `#5FD3C4` | |
| kind: file | `#261F3A` / `#B49CFF` | |
| kind: link | `#3A1E14` / `#FF8A5B` | |

Type: **Sora** for titles (30 sp screen titles, 17 sp card titles), **DM Sans** for everything
else (16/15/14/13/12 sp). Radii: cards 22, tiles 12–14, pills fully rounded. Touch targets ≥ 44 dp.

Agreed details — keep them:
- Volume is a **pill**: a 44 dp rounded bar that fills with `accentContainer`, speaker icon left,
  percentage right. It must never look like a progress bar.
- Non-pauseable routes show **Stop** only. A live stream = not pauseable + `http(s)` input address
  (any origin, decided in one function). Now Playing adds the line "Live streams can't be paused";
  Rooms cards show the status "Live stream". Non-stream sources (e.g. line-in) are never labelled
  live.
- The transfer action is labelled **"Move to room…"**.
- Starting playback on a busy target shows what will stop ("Radio Paradise will stop in Bedroom").

## Workflow

Specs are written **locally** (where the hub and the `multiroom-ai` source are reachable) with
Spec Kit: `/speckit-specify` → `/speckit-clarify` → `/speckit-plan` → `/speckit-tasks`, committed on
a feature branch `NNN-short-name` and pushed. **Cloud sessions implement**: check out that branch,
run `/speckit-implement`, keep commits on the same branch. If a task turns out to need a decision
the spec does not cover, stop and ask rather than inventing behaviour.

**Bump the app version first.** The first commit when starting to implement feature `NNN` sets, in
[gradle.properties](gradle.properties), `sonora.versionName=0.N.0-alpha` (N = the feature number
without leading zeros, e.g. `002-…` → `0.2.0-alpha`) and `sonora.versionCode=N`, so each feature's
APK installs over the previous one and the Settings footer shows which feature a tester is on. Keep
the `-alpha` suffix until the user says otherwise.

Spec Kit tooling (`.specify/` except `memory/`, and `.claude/skills/speckit-*`) is **not committed**
and is generated per machine: PowerShell scripts locally, sh scripts in the cloud. In cloud sessions
the SessionStart hook [scripts/cloud-speckit-init.sh](scripts/cloud-speckit-init.sh) runs
`specify init --script sh` (pinned CLI v1.0.12, installed by the environment's setup script,
whose reference copy is [scripts/cloud-setup.sh](scripts/cloud-setup.sh)). If
`/speckit-*` skills are missing, run that script by hand and read `/tmp/speckit-init.log`.

## Scope of the first feature (input for `/speckit-specify`)

1. Scaffold the KMP project above (Android app + iOS stub), wire the bundled fonts into a Compose
   theme with the tokens above.
2. Generate the API client from `api/openapi.json`.
3. Settings stub with a "Hub address" field persisted on device; no default value.
   `network_security_config` allowing cleartext (the address is user-entered, so it cannot be
   scoped to a domain) and the `INTERNET` permission.
4. Rooms screen against the real API with polling: now-playing cards (volume pill, Stop,
   Pause/Resume when pauseable), Idle section, Off rooms, master mute, bottom navigation. Other
   screens are placeholders.
5. Unit tests in `commonTest` for the logic that joins outputs + groups + routes + inputs into
   Rooms cards (group routes, members, disabled outputs, input kind inference).
6. Build with `./gradlew :androidApp:assembleDebug` and run the tests
   (`:composeApp:testAndroidHostTest`, `:composeApp:allTests`) before finishing.

## Spec Kit

Sequential feature numbering. Active feature specs live in `specs/NNN-*/`; read the one for the
current branch before writing code.
