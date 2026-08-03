#!/bin/bash
# kubectl pinned to the local kind e2e cluster.
#
# Every invocation is forced onto the kind-xroad-e2e-cluster context, so this
# wrapper can only ever act on the throwaway local kind cluster — never a
# remote or production context. The context is hardcoded with no override on
# purpose: that is the safety property that makes it fine to run any
# subcommand (get/logs/exec/delete/apply) against it during dev and e2e work.
set -euo pipefail

CONTEXT="kind-xroad-e2e-cluster"

if ! kubectl config get-contexts -o name 2>/dev/null | grep -qx "${CONTEXT}"; then
  echo "kube.sh: context '${CONTEXT}' not found — is the kind cluster up? (kind get clusters)" >&2
  exit 1
fi

exec kubectl --context "${CONTEXT}" "$@"
