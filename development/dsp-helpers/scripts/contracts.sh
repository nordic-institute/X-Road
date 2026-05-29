#!/usr/bin/env bash
# contracts.sh — list contract definitions for a participant context
# Usage: contracts.sh <ss|host> <ctx>
set -euo pipefail

# shellcheck source=_common.sh
# shellcheck disable=SC1091
source "$(dirname "${BASH_SOURCE[0]}")/_common.sh"

ss="${1:?Usage: contracts <ss0|ss1|host> <ctx>}"
ctx="${2:?Usage: contracts <ss0|ss1|host> <ctx>}"

host="$(dsp_resolve_host "${ss}")"
token="$(dsp_participant_jwt)"
path="$(printf '/api/management/v5beta/participants/%s/contractdefinitions/request' "${ctx}")"

DSP_TOKEN="${token}" dsp_curl POST "http://${host}:${DSP_MGMT_PORT}${path}" '{}' \
    | dsp_pretty
