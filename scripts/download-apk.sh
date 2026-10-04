#!/usr/bin/env bash
# Downloads the debug APK from the latest "Android APK" workflow run on a branch, but only if that
# run succeeded. Optionally installs it on a connected device with adb.
#
# Usage: scripts/download-apk.sh [-b BRANCH] [-o DIR] [--install] [-s SERIAL]
#   -b BRANCH   branch whose latest run to use (default: the current git branch, else main)
#   -o DIR      where to put the APK (default: build/apk/<run id> in the repository)
#   --install   adb install -r the APK afterwards
#   -s SERIAL   device to install on (implies --install; default: $ANDROID_SERIAL, else the
#               only connected device): a serial from adb devices, or for wireless debugging
#               just its hardware part (Q87XDQ8LMZINK7KB in adb-Q87XDQ8LMZINK7KB-F5L7ZE._adb-…),
#               which survives re-pairing. With several devices connected and none chosen,
#               lists them and stops before downloading.
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
serial="${ANDROID_SERIAL:-}"
while [[ $# -gt 0 ]]; do
    case "$1" in
        -b) branch="$2"; shift 2 ;;
        -o) out="$2"; shift 2 ;;
        --install) install=true; shift ;;
        -s|--device) serial="$2"; install=true; shift 2 ;;
        -h|--help) sed -n '2,15p' "$0"; exit 0 ;;
        *) echo "Unknown argument: $1" >&2; exit 2 ;;
    esac
done

# Device checks run before the download, so a missing or ambiguous device costs nothing.
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

    # Wireless debugging serials look like adb-<hardware serial>-<suffix>._adb-tls-connect._tcp, and
    # the suffix can change on re-pairing; the hardware part is what identifies the phone. Other
    # serials (USB, ip:port) are returned unchanged.
    device_id() {
        local s="$1"
        if [[ "$s" == adb-*._adb-tls-connect._tcp ]]; then
            s="${s#adb-}"
            s="${s%%._adb-tls-connect._tcp}"
            s="${s%-*}"
        fi
        echo "$s"
    }

    # "serial<TAB>state" per line; states other than "device" (unauthorized, offline) can't install.
    mapfile -t devices < <("$adb" devices | tr -d '\r' | awk -F'\t' 'NR > 1 && NF == 2 { print $1 "\t" $2 }')
    ready=()
    for line in "${devices[@]}"; do
        [[ "${line#*$'\t'}" == "device" ]] && ready+=("${line%%$'\t'*}")
    done

    if [[ -n "$serial" ]]; then
        # Accept the full serial or its stable hardware part; the first match will do, since two
        # matches are the same phone over USB and Wi-Fi.
        match=""
        for s in "${ready[@]}"; do
            if [[ "$s" == "$serial" || "$(device_id "$s")" == "$serial" ]]; then match="$s"; break; fi
        done
        if [[ -z "$match" ]]; then
            echo "Device $serial is not connected and ready (adb devices)." >&2
            exit 1
        fi
        serial="$match"
    elif [[ ${#ready[@]} -eq 1 ]]; then
        serial="${ready[0]}"
    elif [[ ${#ready[@]} -eq 0 ]]; then
        echo "No device ready for install (adb devices)." >&2
        exit 1
    else
        echo "Several devices are connected and none was chosen for --install:" >&2
        for line in "${devices[@]}"; do
            s="${line%%$'\t'*}" state="${line#*$'\t'}"
            if [[ "$state" == "device" ]]; then
                # Marketing name when the vendor sets one (Xiaomi: "POCO C85"), else the model.
                mapfile -t props < <("$adb" -s "$s" shell \
                    'getprop ro.product.manufacturer; getprop ro.product.marketname; getprop ro.product.vendor.marketname; getprop ro.product.model; getprop ro.build.version.release' \
                    | tr -d '\r')
                brand="${props[0]:-}" market="${props[1]:-}" vmarket="${props[2]:-}" model="${props[3]:-}" release="${props[4]:-?}"
                name="${market:-${vmarket:-$model}}"
                echo "  $(device_id "$s")  $brand $name, Android $release" >&2
            else
                echo "  $(device_id "$s")  ($state)" >&2
            fi
        done
        echo "Choose one with -s SERIAL, e.g. $0 -s $(device_id "${ready[0]}")" >&2
        exit 2
    fi
    echo "Install target: $serial"
fi

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
    "$adb" -s "$serial" install -r "$apk"
fi
