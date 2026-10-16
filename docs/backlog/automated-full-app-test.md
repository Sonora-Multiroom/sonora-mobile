# Automate the full app test

- **Status:** Proof of concept done 2026-10-16: the Settings section (ST01–ST16) runs as Maestro
  flows in [scripts/full-test/](../../scripts/full-test/); the other sections are still manual.
- **Origin:** The user, 2026-10-16, after the first [full app test](../testing/full-app-test.md)
  ([report](../testing/runs/2026-10-16.md)) took ~35 minutes and over 100 agent tool calls.
- **Depends on:** The full app test script. Maestro CLI (Java 17) on the local machine; a test phone
  that lets Maestro's driver app run (the Pixel 5; the POCO is unchecked, it refuses adb input).
  No app or hub change.
- **Effort:** Medium. A runner script with gates and teardown, one Maestro flow per section of the
  script (about 70 checks), hub helpers for setup and assertions, and a rule for keeping flows and
  script in step.

## Problem

The full app test is driven step by step by an agent: dump the screen (~2.5 s), read it, decide,
tap. Almost all of the run time is those turns, not the phone or the hub. The same loop caused the
run's mistakes: taps from stale coordinates after the keyboard opened, button labels that change
with the selection, short-lived messages missed between dumps, and a tap that landed in another app
when a notification dropped over Sonora.

## Proposal

Run the routine checks as **Maestro** flows, black box, against the real app and hub; keep the agent
for failures, the **User** checks (TalkBack, Wi-Fi cuts) and the report.

- **Runner** `scripts/full-test/run.sh`, with gates in order:
  1. *Preflight*: build and tests green, `api/openapi.json` unchanged after `refreshOpenApi`, the
     named device connected and unlocked, Do Not Disturb on, hub snapshot saved, volumes at 1.
     Any failure stops the run before the phone is touched.
  2. *Flows*: one per section (F, R, N, S, ST), tagged so a run can take all of them or only the
     sections a change touches. Each flow sets up the hub through `runScript` HTTP calls, checks
     that the setup took (skipping itself otherwise, so one hub hiccup does not cascade), acts with
     `tapOn` by text or accessibility label, and asserts with `assertVisible` /
     `extendedWaitUntil` on screen and HTTP reads of `/api/v2` on the hub.
  3. *Teardown* from a shell `trap`, so it runs after failures and Ctrl-C too: stop routes first,
     then restore volumes, on/off states and the phone's settings, and diff the hub against the
     snapshot.
- **Output**: Maestro's JUnit report plus screenshots of failures. The agent reads it, rechecks
  each failure by hand, asks the user for the User checks and writes `docs/testing/runs/…md`.
- **Single source of truth**: once a section is automated, its rows in the script point to the
  flow instead of repeating the steps; the constitution's full app test rule then covers the flows.

## Proof of concept (2026-10-16)

`scripts/full-test/run.sh --device <serial> --tags settings --skip-build` on the Pixel 5 over
wireless adb, Maestro CLI 2.11.0, Sonora `0.5.0-alpha+33`, hub API 0.1.25:

- **Result**: 16 of 16 flows passed (second full run; the first had 14, both failures a flow
  mistake: `centerElement` on the last row of a list, which cannot be scrolled to the middle).
  Teardown restored the hub to the snapshot every time, including after failed and aborted runs.
- **Time**: 491 s for the 16 flows (12–46 s each), about the same wall time as the agent-driven ST
  section of the first run (~8 min). The gain is not speed but the agent's time and tokens: no turns
  during the run, no screenshots to read, none of the mechanical mistakes. Most of each flow's time
  is restarting the app (`launchApp`) and the cleanup hook, which runs before and after every flow.
- **Windows lessons**, built into the runner: `ANDROID_HOME` may hold an unexpanded
  `%LOCALAPPDATA%\…`; Windows programs need `D:/…` paths (`pwd -W`) when `MSYS_NO_PATHCONV` is set;
  Maestro makes a folder per flow name, so names must not contain `:` (or other characters Windows
  forbids in paths), and YAML needs flow names with `: ` quoted.

## Open questions

1. ~~Does Maestro run on this Windows machine, and over wireless adb?~~ Yes (proof of concept).
2. Can the POCO run Maestro's driver, or only the Pixel 5?
3. Timing checks ("within ~3 s"): assert with timeouts in the flow, or leave them to the agent?
4. Speed: worth trimming the per-flow cost (one cleanup per flow instead of two, no app restart
   where a flow can start from where the previous one left off), or is unattended enough?
5. Convert the other sections (F, R, N, S). F needs `pm clear`, R3 counts polls in Loki, N11
   kills the process; each needs a step outside Maestro (`runScript` cannot run adb).
6. Commit the helpers from the first run (guarded input, dump, tap by label, hub calls, Loki poll
   count) as `scripts/device/` for the checks that stay manual?
