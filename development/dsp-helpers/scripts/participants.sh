#!/usr/bin/env bash
# participants.sh — list all participant contexts on a control plane
# Usage: participants.sh <ss|host>
set -euo pipefail

# shellcheck source=_common.sh
# shellcheck disable=SC1091
source "$(dirname "${BASH_SOURCE[0]}")/_common.sh"

ss="${1:?Usage: participants <ss0|ss1|host>}"
host="$(dsp_resolve_host "${ss}")"
token="$(dsp_provisioner_jwt)"

DSP_TOKEN="${token}" dsp_curl GET "http://${host}:${DSP_MGMT_PORT}${DSP_PATH_PARTICIPANTS}" \
    | dsp_pretty
