#!/usr/bin/env bash
# Automated part of the full app test (docs/testing/full-app-test.md): gates, Maestro flows,
# teardown. Local sessions only: it drives the user's phone and plays on the user's speakers.
#
#   scripts/full-test/run.sh --device SERIAL [--tags settings,...] [--skip-build]
#
# Gate 1 (preflight) stops the run before the phone is touched: build and tests, the hub contract,
# the device, the hub. Then the hub is snapshotted, volumes go to 1 and Do Not Disturb is turned on.
# Gate 2 runs the flows; each flow sets up and checks its own hub state and cleans up after itself.
# Teardown always runs (trap): stop playback first, then restore the hub and the phone, then diff.
# Results: build/full-test/<time>/ (JUnit report, Maestro logs and failure screenshots).
set -u

# Windows-style path (D:/...), since MSYS_NO_PATHCONV below stops Git Bash converting /d/... for
# Windows programs such as Python and Maestro.
ROOT="$(cd "$(dirname "$0")/../.." && (pwd -W 2>/dev/null || pwd))"
HERE="$ROOT/scripts/full-test"
DEVICE="" TAGS="" SKIP_BUILD=0
while [ $# -gt 0 ]; do
  case "$1" in
    --device) DEVICE="$2"; shift 2 ;;
    --tags) TAGS="$2"; shift 2 ;;
    --skip-build) SKIP_BUILD=1; shift ;;
    *) echo "unknown argument $1" >&2; exit 2 ;;
  esac
done
[ -n "$DEVICE" ] || { echo "--device SERIAL is required (see adb devices -l)" >&2; exit 2; }

# ANDROID_HOME may hold an unexpanded "%LOCALAPPDATA%\..." on this machine; fall back when it does.
[ -d "${ANDROID_HOME:-}" ] || export ANDROID_HOME="$LOCALAPPDATA/Android/Sdk"
export PATH="$(cygpath -u "$ANDROID_HOME" 2>/dev/null || echo "$ANDROID_HOME")/platform-tools:$PATH"
export MSYS_NO_PATHCONV=1
MAESTRO="${MAESTRO:-$HOME/.maestro/bin/maestro}"
OUT="$ROOT/build/full-test/$(date +%Y%m%d-%H%M%S)"
mkdir -p "$OUT"
adbs() { adb -s "$DEVICE" shell "$@" | tr -d '\r'; }
fail() { echo "GATE FAILED: $*" >&2; exit 3; }

# Fixture: KEY=value lines, passed to every flow as -e KEY=value.
ENV_ARGS=()
while IFS= read -r line; do
  case "$line" in ''|'#'*) continue ;; esac
  ENV_ARGS+=(-e "$line")
done < "$HERE/fixture.env"
HUB="$(grep '^HUB=' "$HERE/fixture.env" | cut -d= -f2-)"

echo "== Gate 1: preflight"
if [ "$SKIP_BUILD" = 0 ]; then
  (cd "$ROOT" && ./gradlew :androidApp:assembleDebug :composeApp:testAndroidHostTest \
    :composeApp:allTests :composeApp:check > "$OUT/build.log" 2>&1)
  [ $? = 0 ] || fail "build or tests, see $OUT/build.log"
  (cd "$ROOT" && ./gradlew refreshOpenApi -q > "$OUT/refresh.log" 2>&1) || fail "refreshOpenApi"
  (cd "$ROOT" && git diff --quiet api/openapi.json) || fail "the hub contract changed (Constitution I)"
  echo "build, tests and contract ok"
fi
adb devices | grep -q "^$DEVICE[[:space:]]*device" || fail "device $DEVICE not connected"
adbs dumpsys window | grep -q 'isKeyguardShowing=false' || fail "the phone is locked"
VERSION="$(adbs dumpsys package sonora.multiroom.mobile | grep -m1 versionName | cut -d= -f2)"
[ -n "$VERSION" ] || fail "Sonora is not installed"
curl -sf -m 5 "$HUB/api/v2/master-mute" > /dev/null || fail "hub $HUB not reachable"
echo "device $DEVICE, Sonora $VERSION, hub $HUB"

python -I "$HERE/hubstate.py" snapshot "$HUB" "$OUT/hub-before.json" || fail "hub snapshot"
ZEN="$(adbs settings get global zen_mode)"
teardown() {
  echo "== Teardown"
  python -I "$HERE/hubstate.py" restore "$HUB" "$OUT/hub-before.json"
  echo "hub restore exit $?"
  case "$ZEN" in
    0) adbs cmd notification set_dnd off ;;
    1) adbs cmd notification set_dnd priority ;;
    3) adbs cmd notification set_dnd alarms ;;
  esac
  echo "Do Not Disturb back to zen_mode $(adbs settings get global zen_mode)"
}
trap teardown EXIT
trap 'exit 130' INT TERM
python -I "$HERE/hubstate.py" prepare "$HUB" || fail "volumes to 1"
adbs cmd notification set_dnd none
[ "$(adbs settings get global zen_mode)" = 2 ] || fail "Do Not Disturb did not turn on"

echo "== Gate 2: flows${TAGS:+ (tags $TAGS)}"
TAG_ARGS=()
[ -n "$TAGS" ] && TAG_ARGS=(--include-tags "$TAGS")
start=$(date +%s)
(cd "$HERE" && "$MAESTRO" --device "$DEVICE" test "${ENV_ARGS[@]}" "${TAG_ARGS[@]}" \
  --format junit --output "$OUT/report.xml" --test-output-dir "$OUT/maestro" flows/) \
  2>&1 | tee "$OUT/maestro.log"
result=${PIPESTATUS[0]}
echo "flows exit $result in $(( $(date +%s) - start )) s; results in $OUT"
exit "$result"
