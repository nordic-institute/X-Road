#!/usr/bin/env bash
# asset-create.sh — create an asset for a participant context
# Usage: asset-create.sh <ss|host> <ctx> <asset-id> <baseUrl>
set -euo pipefail

# shellcheck source=_common.sh
# shellcheck disable=SC1091
source "$(dirname "${BASH_SOURCE[0]}")/_common.sh"

ss="${1:?Usage: asset-create <ss0|ss1|host> <ctx> <asset-id> <baseUrl>}"
ctx="${2:?Usage: asset-create <ss0|ss1|host> <ctx> <asset-id> <baseUrl>}"
asset_id="${3:?Usage: asset-create <ss0|ss1|host> <ctx> <asset-id> <baseUrl>}"
base_url="${4:?Usage: asset-create <ss0|ss1|host> <ctx> <asset-id> <baseUrl>}"

host="$(dsp_resolve_host "${ss}")"
token="$(dsp_participant_jwt)"
path="$(printf '/api/management/v5beta/participants/%s/assets' "${ctx}")"

body="$(printf '{
  "@context": ["https://w3id.org/edc/connector/management/v2"],
  "@id": "%s",
  "@type": "Asset",
  "properties": {"name": "%s", "contenttype": "application/json"},
  "dataAddress": {
    "@type": "DataAddress",
    "type": "HttpData",
    "name": "%s",
    "baseUrl": "%s",
    "proxyPath": "true"
  }
}' "${asset_id}" "${asset_id}" "${asset_id}" "${base_url}")"

DSP_TOKEN="${token}" dsp_curl POST "http://${host}:${DSP_MGMT_PORT}${path}" "${body}" \
    | dsp_pretty
