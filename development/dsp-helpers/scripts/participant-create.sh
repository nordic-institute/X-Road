#!/usr/bin/env bash
# participant-create.sh — create a participant context
# Usage: participant-create.sh <ss|host> <ctx-id> [identity]
set -euo pipefail

# shellcheck source=_common.sh
# shellcheck disable=SC1091
source "$(dirname "${BASH_SOURCE[0]}")/_common.sh"

ss="${1:?Usage: participant-create <ss0|ss1|host> <ctx-id> [identity]}"
ctx="${2:?Usage: participant-create <ss0|ss1|host> <ctx-id> [identity]}"
identity="${3:-${ctx}}"

host="$(dsp_resolve_host "${ss}")"
token="$(dsp_provisioner_jwt)"

body="$(printf '{
  "@context": ["https://w3id.org/edc/connector/management/v2"],
  "@type": "ParticipantContext",
  "identity": "%s",
  "@id": "%s"
}' "${identity}" "${ctx}")"

DSP_TOKEN="${token}" dsp_curl POST "http://${host}:${DSP_MGMT_PORT}${DSP_PATH_PARTICIPANTS}" "${body}" \
    | dsp_pretty
