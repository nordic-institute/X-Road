#!/usr/bin/env bash
# catalog.sh — query full DSP catalog for a participant context
# Usage: catalog.sh <ss|host> <ctx> [provider-dsp-url]
set -euo pipefail

# shellcheck source=_common.sh
# shellcheck disable=SC1091
source "$(dirname "${BASH_SOURCE[0]}")/_common.sh"

ss="${1:?Usage: catalog <ss0|ss1|host> <ctx> [provider-dsp-url]}"
ctx="${2:?Usage: catalog <ss0|ss1|host> <ctx> [provider-dsp-url]}"
provider_url="${3:-}"

host="$(dsp_resolve_host "${ss}")"
token="$(dsp_participant_jwt)"
path="$(printf '/api/management/v5beta/participants/%s/catalog/request' "${ctx}")"

if [[ -n "${provider_url}" ]]; then
    body="$(printf '{"@context":{"@vocab":"https://w3id.org/edc/v0.0.1/ns/"},"counterPartyAddress":"%s","protocol":"dataspace-protocol-http"}' "${provider_url}")"
else
    body='{}'
fi

DSP_TOKEN="${token}" dsp_curl POST "http://${host}:${DSP_MGMT_PORT}${path}" "${body}" \
    | dsp_pretty
