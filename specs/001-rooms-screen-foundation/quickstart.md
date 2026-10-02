# Quickstart: validate Rooms Screen Foundation

## 1. Cloud / CI checks (no hub, no emulator)

Prerequisites: JDK 17+, `ANDROID_HOME` set.

```bash
./gradlew :androidApp:assembleDebug          # APK at androidApp/build/outputs/apk/debug/
./gradlew :composeApp:testAndroidHostTest    # confirm task name, see research R2
./gradlew :composeApp:allTests               # iOS targets skipped off macOS
./gradlew :composeApp:check                  # includes verifyLayering (research R3)
```

Expected: all green. The OpenAPI client is generated during the build under
`composeApp/build/generated/openapi/`, and `git status` shows no generated files.

Test suites that must exist and pass (see [data-model.md](data-model.md) and
[contracts/hub-repository.md](contracts/hub-repository.md)):

| Suite | Proves |
|---|---|
| `SourceKindTest` | FR-009 inference table, ephemeral → link first |
| `RoomsBuilderTest` | single route, group route (members not idle), stopped ignored, failed counted, missing input/room/group, room in several groups, sorting, idle state precedence, "N of M", no rooms, muted incl. master mute, action per status |
| `GroupVolumeTest` | 70/35 → 35 gives 35/18, all-zero → new value, rounding halves up, clamp 0..100, drag down/up restores balance (FR-013d) |
| `HubAddressTest` | prefixing, default port 8080, explicit 80 kept, https as typed, spaces/empty rejected |
| `KtorHubRepositoryTest` | contract tests 1–7 |
| `RoomsViewModelTest` | no address → no requests; poll every 2.5 s without overlap; 3 s timeout → Unreachable; stale disables controls; drag override beats refresh; ≤ 4 volume sends/s + final send; in-flight blocks repeats; failures produce plain message; group drag never hits `/groups/{id}/volume` |

## 2. On a device against the real hub (user, locally)

Prerequisites: phone on the home LAN, hub running at e.g. `http://multiroom.lan:8080`.

1. `adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk`
2. **First launch (US1-1)**: Rooms says "Set your hub address" with "Open Settings".
3. **Save address (US1-2/4)**: enter `multiroom lan` → refused (spaces). Enter
   `multiroom.lan` → field shows `http://multiroom.lan:8080`, Rooms loads.
4. **Restart (US1-3)**: kill and reopen the app. Rooms loads without asking.
5. **State (US2)**: compare Rooms with the hub (another client or `curl
   http://multiroom.lan:8080/api/v2/routes`). Check the group card's member line, idle/off/not
   connected rows and "N of M rooms in use".
6. **Live updates (US2-7, SC-002)**: change volume or start a route from another client. The
   screen follows within ~3 s.
7. **Background (US2-8, SC-006)**: put the app in the background for 30 s while watching the hub's
   access log. No `/api/v2` requests arrive. Return: refresh is immediate.
8. **Controls (US3)**: drag a single-room pill and a group pill (members keep their balance). Pause
   and resume a pauseable source, stop a live stream, toggle master mute (pills become non-draggable).
9. **Hub down (Edge Cases, SC-005)**: stop the hub. Within 5 s you see the stale banner with
   disabled controls. Start it again: live within 5 s.
10. **Navigation (US4)**: tap each tab, a card, a room's play button and "Play something". Each
    placeholder shows and Back returns.
