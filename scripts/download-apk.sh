#!/usr/bin/env bash
# Downloads the debug APK from the latest "Android APK" workflow run on a branch, but only if that
# run succeeded. Optionally installs it on the connected device with adb.
#
# Usage: scripts/download-apk.sh [-b BRANCH] [-o DIR] [--install]
#   -b BRANCH   branch whose latest run to use (default: the current git branch, else main)
#   -o DIR      where to put the APK (default: build/apk/<run id> in the repository)
#   --install   adb install -r the APK afterwards
#
# Needs the GitHub CLI, logged in (gh auth login).
set -euo pipefail

REPO="Sonora-Multiroom/sonora-mobile"
WORKFLOW="Android APK"
ARTIFACT="sonora-debug-apk"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

branch=""
out=""
install=false
while [[ $# -gt 0 ]]; do
    case "$1" in
        -b) branch="$2"; shift 2 ;;
        -o) out="$2"; shift 2 ;;
        --install) install=true; shift ;;
        -h|--help) sed -n '2,10p' "$0"; exit 0 ;;
        *) echo "Unknown argument: $1" >&2; exit 2 ;;
    esac
done

if [[ -z "$branch" ]]; then
    branch="$(git branch --show-current 2>/dev/null || true)"
    branch="${branch:-main}"
fi

# Latest run of the workflow on the branch, whatever its state; conclusion is empty while running.
run="$(gh run list -R "$REPO" -w "$WORKFLOW" -b "$branch" -L 1 \
    --json databaseId,status,conclusion,headSha,url \
    --jq '.[0] | [.databaseId, .status, .conclusion, .headSha, .url] | map(. // "") | join("|")')"
IFS='|' read -r id status conclusion sha url <<<"$run"
if [[ -z "$id" ]]; then
    echo "No \"$WORKFLOW\" runs on branch $branch." >&2
    exit 1
fi

echo "Latest run on $branch: $url (commit ${sha:0:7})"
if [[ "$status" != "completed" ]]; then
    echo "The run is still $status. Wait for it: gh run watch -R $REPO $id" >&2
    exit 1
fi
if [[ "$conclusion" != "success" ]]; then
    echo "The run finished with \"$conclusion\", not success; not downloading." >&2
    exit 1
fi

local_sha="$(git rev-parse "$branch" 2>/dev/null || true)"
if [[ -n "$local_sha" && "$local_sha" != "$sha" ]]; then
    echo "Note: local $branch is at ${local_sha:0:7}, the APK is built from ${sha:0:7}."
fi

out="${out:-$ROOT/build/apk/$id}"
rm -rf "$out"
gh run download -R "$REPO" "$id" -n "$ARTIFACT" -D "$out"

# A glob rather than find: from cmd, Windows' find.exe can come first on PATH.
shopt -s nullglob globstar
apks=("$out"/**/*.apk)
apk="${apks[0]:-}"
if [[ -z "$apk" ]]; then
    echo "The artifact has no APK." >&2
    exit 1
fi
echo "APK: $apk"

if $install; then
    adb="$(command -v adb || true)"
    for sdk in "${ANDROID_HOME:-}" "${LOCALAPPDATA:-}/Android/Sdk" "$HOME/Library/Android/sdk" "$HOME/Android/Sdk"; do
        [[ -n "$adb" ]] && break
        for candidate in "$sdk/platform-tools/adb" "$sdk/platform-tools/adb.exe"; do
            [[ -n "$sdk" && -x "$candidate" ]] && { adb="$candidate"; break; }
        done
    done
    if [[ -z "$adb" ]]; then
        echo "adb not found; install the APK by hand." >&2
        exit 1
    fi
    "$adb" install -r "$apk"
fi
