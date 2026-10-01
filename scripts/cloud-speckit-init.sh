#!/bin/bash
# SessionStart hook: generates the Spec Kit tooling (sh scripts) in cloud sessions.
#
# The tooling is not committed (see .gitignore): locally it is generated with PowerShell
# scripts, in the Linux cloud with sh. The `specify` CLI itself is installed by the cloud
# environment's setup script. Does nothing on Windows or when the tooling already exists.
set -u

case "$(uname -s)" in MINGW* | MSYS* | CYGWIN*) exit 0 ;; esac
cd "${CLAUDE_PROJECT_DIR:-$(dirname "$0")/..}" || exit 0
[ -d .specify/scripts ] && exit 0

export PATH="$HOME/.local/bin:$PATH"
if ! command -v specify >/dev/null; then
  echo "Spec Kit not initialised: the specify CLI is missing (check the cloud setup script)."
  exit 0
fi

if specify init --here --integration claude --script sh --force --non-interactive \
     --ignore-agent-tools >/tmp/speckit-init.log 2>&1; then
  # Keep committed files init may touch (constitution, this hook's settings).
  git checkout -- .specify/memory .claude/settings.json 2>/dev/null || true
  echo "Spec Kit tooling generated (sh scripts)."
else
  echo "Spec Kit init failed, see /tmp/speckit-init.log"
fi
exit 0
