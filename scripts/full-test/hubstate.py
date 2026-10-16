"""Snapshot, prepare, restore and compare the hub's state around a full app test run.

    python -I hubstate.py snapshot HUB FILE   save outputs, groups, inputs, routes and master mute
    python -I hubstate.py prepare  HUB        set every output's volume to 1
    python -I hubstate.py restore  HUB FILE   stop every route, then put the saved state back
    python -I hubstate.py diff     HUB FILE   exit 1 if the hub differs from the snapshot

HUB is the base address, e.g. http://multiroom.lan:8080. Routes are always stopped before volumes
are restored, so nothing plays loud.
"""
import json
import sys
import urllib.request

sys.stdout.reconfigure(encoding="utf-8")


def call(method, url, body=None):
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(url, data=data, method=method,
                                 headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=15) as resp:
        raw = resp.read()
        return json.loads(raw) if raw else None


def snapshot(api):
    q = "?includeDisabled=true"
    return {
        "outputs": call("GET", f"{api}/outputs{q}"),
        "groups": call("GET", f"{api}/groups{q}"),
        "inputs": call("GET", f"{api}/inputs{q}"),
        "routes": call("GET", f"{api}/routes"),
        "masterMute": call("GET", f"{api}/master-mute"),
    }


def stop_routes(api):
    for r in call("GET", f"{api}/routes"):
        try:
            call("DELETE", f"{api}/routes/{r['routeId']}")
            print(f"stopped {r['routeId']} ({r['inputId']} on {r['targetId']})")
        except urllib.error.HTTPError as e:
            if e.code != 404:
                raise


def restore(api, saved):
    stop_routes(api)
    now = snapshot(api)
    saved_inputs = {i["inputId"]: i for i in saved["inputs"]}
    for i in now["inputs"]:
        if i["inputId"] not in saved_inputs and i["source"] == "EPHEMERAL":
            call("DELETE", f"{api}/inputs/{i['inputId']}")
            print(f"removed runtime input {i['inputId']}")
        elif i["inputId"] in saved_inputs and i["enabled"] != saved_inputs[i["inputId"]]["enabled"]:
            call("PUT", f"{api}/inputs/{i['inputId']}/enabled",
                 {"enabled": saved_inputs[i["inputId"]]["enabled"]})
    for g in saved["groups"]:
        call("PUT", f"{api}/groups/{g['groupId']}/enabled", {"enabled": g["enabled"]})
        call("PUT", f"{api}/groups/{g['groupId']}/mute", {"muted": g["muted"]})
    for o in saved["outputs"]:
        call("PUT", f"{api}/outputs/{o['outputId']}/enabled", {"enabled": o["enabled"]})
        call("PUT", f"{api}/outputs/{o['outputId']}/mute", {"muted": o["muted"]})
        call("PUT", f"{api}/outputs/{o['outputId']}/volume", {"volume": o["volume"]})
    call("PUT", f"{api}/master-mute", {"muted": saved["masterMute"]["muted"]})


def comparable(state):
    drop = {"updatedAt"}
    return {k: ([{f: v for f, v in x.items() if f not in drop} for x in vals]
                if isinstance(vals, list) else vals)
            for k, vals in state.items()}


def main():
    action, hub = sys.argv[1], sys.argv[2].rstrip("/")
    api = f"{hub}/api/v2"
    if action == "snapshot":
        with open(sys.argv[3], "w", encoding="utf-8", newline="\n") as f:
            json.dump(snapshot(api), f, indent=1, ensure_ascii=False)
        print(f"snapshot saved to {sys.argv[3]}")
    elif action == "prepare":
        for o in call("GET", f"{api}/outputs?includeDisabled=true"):
            call("PUT", f"{api}/outputs/{o['outputId']}/volume", {"volume": 1})
        print("every output at volume 1")
    elif action in ("restore", "diff"):
        with open(sys.argv[3], encoding="utf-8") as f:
            saved = json.load(f)
        if action == "restore":
            restore(api, saved)
        a, b = comparable(saved), comparable(snapshot(api))
        diffs = [k for k in a if a[k] != b[k]]
        print("hub matches the snapshot" if not diffs else f"hub differs in: {', '.join(diffs)}")
        sys.exit(1 if diffs else 0)
    else:
        sys.exit(f"unknown action {action}")


if __name__ == "__main__":
    main()
