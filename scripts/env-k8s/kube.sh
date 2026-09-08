#!/bin/bash
# kubectl pinned to a local kind cluster.
#
# Every invocation is forced onto a kind-xroad-<env>-cluster context, so this
# wrapper can only ever act on a throwaway local kind cluster — never a remote
# or production context. The context is derived from --env with no free-form
# override on purpose: that is the safety property that makes it fine to run
# any subcommand (get/logs/exec/delete/apply) against it during dev and e2e
# work.
#
# Usage: kube.sh [--env=dev|test|eks|e2e] <kubectl args...>   (default: dev)
set -euo pipefail

ENV_NAME="dev"
if [[ "${1:-}" == --env=* ]]; then
  ENV_NAME="${1#*=}"
  shift
fi

case "${ENV_NAME}" in
  dev|test|eks|e2e) ;;
  *)
    echo "kube.sh: unknown --env '${ENV_NAME}' — expected dev, test, eks or e2e" >&2
    exit 1
    ;;
esac

CONTEXT="kind-xroad-${ENV_NAME}-cluster"

if ! kubectl config get-contexts -o name 2>/dev/null | grep -qx "${CONTEXT}"; then
  {
    echo "kube.sh: context '${CONTEXT}' not found."
    existing="$(kubectl config get-contexts -o name 2>/dev/null | grep '^kind-xroad-' || true)"
    if [[ -n "${existing}" ]]; then
      echo "Local kind contexts that do exist:"
      echo "${existing}" | sed 's/^/  /'
      echo "Select one with --env=<env> (kube.sh --env=e2e ...)."
    else
      echo "No local kind clusters are up (kind get clusters); start one with start-env.sh --env=${ENV_NAME}."
    fi
  } >&2
  exit 1
fi

exec kubectl --context "${CONTEXT}" "$@"
