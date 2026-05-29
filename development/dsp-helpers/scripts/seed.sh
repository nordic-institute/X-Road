#!/usr/bin/env bash
# seed.sh — run setup_dsp.hurl seeding then verify with list commands
# Usage: seed.sh [ss0-host-override] [ss1-host-override]
set -euo pipefail

# shellcheck source=_common.sh
# shellcheck disable=SC1091
source "$(dirname "${BASH_SOURCE[0]}")/_common.sh"

SCRIPTS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

ss0_host="$(dsp_resolve_host "${1:-ss0}")"
ss1_host="$(dsp_resolve_host "${2:-ss1}")"

hurl_file="$(_dsp_hurl_file)"

echo "==> Running setup_dsp.hurl (ss0=${ss0_host} ss1=${ss1_host})" >&2
hurl --variable "ss0_host=${ss0_host}" \
     --variable "ss1_host=${ss1_host}" \
     --variable "ds_cp_mgmt_port=${DSP_MGMT_PORT}" \
     "${hurl_file}"

echo "" >&2
echo "==> Verifying ss0 participants" >&2
"${SCRIPTS_DIR}/participants.sh" "${ss0_host}"

echo "" >&2
echo "==> Verifying ss0 assets (test-part-ctx)" >&2
"${SCRIPTS_DIR}/assets.sh" "${ss0_host}" test-part-ctx

echo "" >&2
echo "==> Verifying ss1 participants" >&2
"${SCRIPTS_DIR}/participants.sh" "${ss1_host}"

echo "" >&2
echo "==> Verifying ss1 assets (test-part-ctx)" >&2
"${SCRIPTS_DIR}/assets.sh" "${ss1_host}" test-part-ctx

echo "" >&2
echo "==> Seed + verify complete." >&2
