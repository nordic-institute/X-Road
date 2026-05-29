#!/usr/bin/env bash
# dataplanes.sh — list registered data-planes via control API (no auth)
# Usage: dataplanes.sh <ss|host>
set -euo pipefail

# shellcheck source=_common.sh
# shellcheck disable=SC1091
source "$(dirname "${BASH_SOURCE[0]}")/_common.sh"

ss="${1:?Usage: dataplanes <ss0|ss1|host>}"
host="$(dsp_resolve_host "${ss}")"

raw="$(dsp_curl GET "http://${host}:${DSP_CONTROL_PORT}${DSP_PATH_DATAPLANES}")"

# Pretty summary: @id, state, lastActive (ms → ISO)
if [[ "${DSP_RAW:-0}" == "1" ]] || ! [ -t 1 ]; then
    echo "${raw}"
else
    echo "${raw}" | jq '[.[] | {id: .["@id"], state: .state, lastActive: (.lastActive // "n/a" | if . == "n/a" then . else (. / 1000 | todate) end)}]'
fi
