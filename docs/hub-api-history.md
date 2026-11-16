# Hub API history

What changed in the hub's REST API from one version to the next, as far as it matters to this app,
and what the app did about it. [api/openapi.json](../api/openapi.json) is the contract; the rules
the app follows today are in [AGENTS.md](../AGENTS.md) ("API"). This file is the record behind them:
read it when a rule's origin matters or when the app must tolerate an older hub.

When `./gradlew refreshOpenApi` brings a new API version, add an entry here (newest last), update
the version in AGENTS.md, and move any behaviour the UI depends on into AGENTS.md as a
present-tense rule.

| API | Hub feature | Change | In the app |
|---|---|---|---|
| 0.1.21 | HUB-023 | Join modes (`RouteResponse.joinMode`, `InputResponse.defaultJoinMode`): an output may carry several routes. Admission refusals on errors (`ErrorResponse.reason`, `outputId`) | Never sends a `joinMode`. Maps `ROUTE_LIMIT_REACHED` and `INPUT_ALREADY_ON_OUTPUT`. Rooms and Now Playing still show one route per room ([backlog](backlog/rooms-with-several-playbacks.md)) |
| 0.1.22 | | Deployed 2026-10-06. Route IDs are opaque, and a transfer keeps the route's id (before, it answered with a new route). `RouteResponse.outputs` lists the outputs a route plays on right now. Starting, playing or moving onto a disabled output, group or input answers 409 with a `reason` (`OUTPUT_DISABLED`, `GROUP_DISABLED`, `INPUT_DISABLED`, `ALL_MEMBERS_DISABLED`) instead of 400 | Since 0.6.0-alpha: maps the four reasons ("<room> is turned off" and so on, then a refresh) and reads `outputs`, which closed the hub gap of MOB-004 (a group route was counted on every member even when one was off) |
| 0.1.23 | HUB-025 | Master mute is a layer above each output's own mute and no longer changes any output's `muted`; turning it off restores each output's own mute | |
| 0.1.24 | | REST v1 removed | Never used v1 |
| 0.1.25 | HUB-027, HUB-028 | Turning off a source stops every route playing it (paused ones too), and an auto-remove source is then removed. Turning off a group stops the routes addressed to it. Turning a room back on puts it back into every live group route of an enabled group containing it, under the same route id; while it rejoins, a transfer answers 409 `ROUTE_CHANGING_OUTPUTS`, which clears by itself within moments. HUB-028 is internal (sound-card channel use) | Since 0.6.0-alpha: retries a move refused as `ROUTE_CHANGING_OUTPUTS` once after 1 s |
| 0.1.28 | | `PUT /api/v2/inputs/{id}/display-name` `{displayName}` renames an input: at most 64 characters (longer answers 400); null or blank restores the original name; lasts until a restart for a configured input, until removal for a runtime one. `POST /api/v2/play` answers with the input's `displayName`: the name sent, the title the link resolved to, or a generated placeholder; resolved titles can be long. The contract's description of `ErrorResponse.reason` lists every reason and says to treat an unknown one as a generic conflict | Calls neither the rename nor reads the new field |
| 0.1.29 | HUB-034 | A route may carry its own volume (`routeVolume`, `defaultRouteVolume`) | Not adopted ([backlog](backlog/adopt-output-volume.md)) |
| 0.1.33 | | Contract refreshed 2026-11-06 with MOB-008 | Builds green |
| 0.1.34 | | Chimes: `GET /api/v2/chimes` lists them; outputs and groups carry `chime`, inputs `defaultChime`, and a start (`POST /api/v2/play`, `POST /api/v2/routes`) may send `chime`. All optional and additive. The contract also gained the radio extension's paths (`/api/radio/**`, RADIO-002) | Reads and sends no chime. Radio is allowed by constitution 1.6.0 and used since 0.9.0-alpha (MOB-009): the generator's tag filter includes `Radio`, and the app calls only the four read-only radio requests. Contract refreshed 2026-11-15 |
