#!/usr/bin/env bash
# Fake `docker` stub used by the shell-test harness.
#
# Records every invocation to $FAKE_DOCKER_LOG (default: /tmp/fake-docker.log),
# then exits 0.  No real Docker daemon is touched.
#
# Sub-commands that scripts commonly call with `if docker ...; then`:
#   pull   → exits 1 (simulates cache miss; scripts fall through to build path)
#   push   → exits 0
#   build  → exits 0
#   buildx → exits 0
#   start  → exits 0
#   run    → exits 0
#   ps     → prints nothing (no containers running)
#   inspect → exits 1 (buildx builder does not exist yet)
#
# Usage:
#   Put the directory containing this file first on PATH before sourcing
#   the script under test.  The file must be named `docker`.

LOG="${FAKE_DOCKER_LOG:-/tmp/fake-docker.log}"
printf '%s\n' "docker $*" >> "$LOG"

CMD="${1:-}"
case "$CMD" in
  pull)
    echo "[fake-docker] pull: $*" >&2
    exit 1
    ;;
  ps)
    # Return empty — no containers running
    exit 0
    ;;
  inspect)
    echo "[fake-docker] inspect: not found" >&2
    exit 1
    ;;
  *)
    exit 0
    ;;
esac
