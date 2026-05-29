#!/usr/bin/env bash
# _common.sh — shared constants, token extraction, host resolution, curl wrapper
# Source this from individual command scripts.
set -euo pipefail

# ---------------------------------------------------------------------------
# Port constants (from ds-control-plane application.yaml)
# ---------------------------------------------------------------------------
DSP_MGMT_PORT="${DSP_MGMT_PORT:-8182}"
DSP_CONTROL_PORT="${DSP_CONTROL_PORT:-8184}"
DSP_SIGNALING_PORT="${DSP_SIGNALING_PORT:-8185}"
DSP_PROTO_PORT="${DSP_PROTO_PORT:-8183}"

# ---------------------------------------------------------------------------
# Path templates — v5beta (Story 06 canonical paths)
# Exported so sourcing scripts can reference them.
# ---------------------------------------------------------------------------
export DSP_PATH_PARTICIPANTS="/api/management/v5beta/participants"
export DSP_PATH_PARTICIPANT_CTX="/api/management/v5beta/participants/%s"
export DSP_PATH_ASSETS_LIST="/api/management/v5beta/participants/%s/assets/request"
export DSP_PATH_ASSETS_CREATE="/api/management/v5beta/participants/%s/assets"
export DSP_PATH_POLICIES_LIST="/api/management/v5beta/participants/%s/policydefinitions/request"
export DSP_PATH_CONTRACTS_LIST="/api/management/v5beta/participants/%s/contractdefinitions/request"
export DSP_PATH_CATALOG="/api/management/v5beta/participants/%s/catalog/request"
export DSP_PATH_DATAPLANES="/api/v1/control/v1/dataplanes"

# ---------------------------------------------------------------------------
# Locate the canonical hurl file (single source of truth for JWTs)
# ---------------------------------------------------------------------------
_dsp_hurl_file() {
    local here
    here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
    local candidate="${here}/../../hurl/scenarios/setup_dsp.hurl"
    if [[ -f "${candidate}" ]]; then
        echo "${candidate}"
        return 0
    fi
    # Fallback: search upward for development/hurl/scenarios/setup_dsp.hurl
    local dir="${here}"
    while [[ "${dir}" != "/" ]]; do
        local f="${dir}/hurl/scenarios/setup_dsp.hurl"
        if [[ -f "${f}" ]]; then
            echo "${f}"
            return 0
        fi
        dir="$(dirname "${dir}")"
    done
    echo "ERROR: setup_dsp.hurl not found (searched from ${here})" >&2
    return 1
}

# ---------------------------------------------------------------------------
# Token extraction — grep Bearer tokens from setup_dsp.hurl
# provisioner token = role:provisioner (first distinct token)
# participant token  = role:participant (second distinct token)
# ---------------------------------------------------------------------------
dsp_provisioner_jwt() {
    if [[ -n "${DSP_PROVISIONER_JWT:-}" ]]; then
        echo "${DSP_PROVISIONER_JWT}"
        return 0
    fi
    local hurl
    hurl="$(_dsp_hurl_file)"
    # Extract all Bearer tokens, deduplicate, take first (provisioner)
    grep -oE 'Bearer [A-Za-z0-9_\-]+\.[A-Za-z0-9_\-]+\.[A-Za-z0-9_\-]+' "${hurl}" \
        | awk '{print $2}' | awk '!seen[$0]++' | head -1
}

dsp_participant_jwt() {
    if [[ -n "${DSP_PARTICIPANT_JWT:-}" ]]; then
        echo "${DSP_PARTICIPANT_JWT}"
        return 0
    fi
    local hurl
    hurl="$(_dsp_hurl_file)"
    # Provisioner token appears first; participant token is the second distinct token
    grep -oE 'Bearer [A-Za-z0-9_\-]+\.[A-Za-z0-9_\-]+\.[A-Za-z0-9_\-]+' "${hurl}" \
        | awk '{print $2}' | awk '!seen[$0]++' | sed -n '2p'
}

# ---------------------------------------------------------------------------
# Host resolution: ss0/ss1 → IPv4 via lxc list; falls back to literal arg
# ---------------------------------------------------------------------------
dsp_resolve_host() {
    local ss="${1:-}"
    if [[ -z "${ss}" ]]; then
        echo "ERROR: dsp_resolve_host: no host/ss arg supplied" >&2
        return 1
    fi
    case "${ss}" in
        ss0) _dsp_lxc_ip "xrd-ss0" ;;
        ss1) _dsp_lxc_ip "xrd-ss1" ;;
        *)   echo "${ss}" ;;  # literal hostname/IP
    esac
}

_dsp_lxc_ip() {
    local name="${1}"
    local ip
    ip="$(lxc list -f csv -c n,4 2>/dev/null \
        | awk -F',' -v n="${name}" '$1==n {ip=$2; gsub(/ .*/, "", ip); print ip; exit}')"
    if [[ -z "${ip}" ]]; then
        echo "ERROR: could not resolve IPv4 for LXC container '${name}'" >&2
        return 1
    fi
    echo "${ip}"
}

# ---------------------------------------------------------------------------
# curl wrapper: dsp_curl <method> <url> [body_json]
# Prints response body on stdout; fails non-2xx with message on stderr.
# ---------------------------------------------------------------------------
dsp_curl() {
    local method="${1}"
    local url="${2}"
    local body="${3:-}"
    local token="${DSP_TOKEN:-}"

    local -a args=( -sS -X "${method}" )

    if [[ -n "${token}" ]]; then
        args+=( -H "Authorization: Bearer ${token}" )
    fi

    if [[ -n "${body}" ]]; then
        args+=( -H "Content-Type: application/json" -d "${body}" )
    fi

    # Capture body + status code in one call
    local tmpfile
    tmpfile="$(mktemp)"
    local http_code
    http_code="$(curl "${args[@]}" -o "${tmpfile}" -w '%{http_code}' "${url}")"
    local body_out
    body_out="$(cat "${tmpfile}")"
    rm -f "${tmpfile}"

    if [[ "${http_code}" -lt 200 ]] || [[ "${http_code}" -ge 300 ]]; then
        echo "ERROR: HTTP ${http_code} from ${url}" >&2
        echo "${body_out}" >&2
        return 1
    fi

    echo "${body_out}"
}

# ---------------------------------------------------------------------------
# Pretty-print helper: pipe to jq when stdout is a TTY unless DSP_RAW=1
# ---------------------------------------------------------------------------
dsp_pretty() {
    if [[ "${DSP_RAW:-0}" == "1" ]] || ! [ -t 1 ]; then
        cat
    else
        jq .
    fi
}
