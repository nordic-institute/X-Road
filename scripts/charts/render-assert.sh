#!/usr/bin/env bash
# Helm template render assertions for the k8s/charts tree.
#
# Fast, offline sanity gate: fails if `helm template` cannot render a chart,
# or if a chart's default-values output stops containing resource kinds it
# is always expected to produce. It does not validate manifests against the
# Kubernetes API schema — that happens when the charts are actually
# deployed, in the full E2E lane.
#
# Usage: ./render-assert.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../../" && pwd)"
source "${ROOT_DIR}/scripts/lib/base-script.sh"

CHARTS_DIR="${ROOT_DIR}/deployment/security-server/k8s/charts"

WORK_DIR="$(mktemp -d)"
trap 'rm -rf "${WORK_DIR}"' EXIT

STATUS=0

assert_kind() {
    local out_file="$1" kind="$2"
    if grep -qE "^kind: ${kind}\$" "${out_file}"; then
        log_success "  Found expected kind: ${kind}"
    else
        log_error "  Expected at least one '${kind}' resource, found none"
        return 1
    fi
}

render_and_assert() {
    local chart="$1"
    shift
    local expected_kinds=("$@")
    local chart_path="${CHARTS_DIR}/${chart}"
    local out_file="${WORK_DIR}/${chart}.yaml"
    local err_file="${WORK_DIR}/${chart}.err"

    log_info "=== ${chart} ==="
    log_info "Rendering: helm template test-${chart} ${chart_path}"

    if ! helm template "test-${chart}" "${chart_path}" > "${out_file}" 2> "${err_file}"; then
        log_error "helm template failed for ${chart}:"
        cat "${err_file}" >&2
        return 1
    fi

    if [[ ! -s "${out_file}" ]]; then
        log_error "helm template produced empty output for ${chart}"
        return 1
    fi

    log_success "Rendered $(grep -cE '^kind: ' "${out_file}") resource(s)"

    local chart_failed=0
    for kind in "${expected_kinds[@]}"; do
        assert_kind "${out_file}" "${kind}" || chart_failed=1
    done

    return "${chart_failed}"
}

echo "========================================"
log_info "Helm template render assertions"
echo "========================================"
echo ""

render_and_assert "central-server" Deployment Service ConfigMap ServiceAccount || STATUS=1
echo ""

render_and_assert "e2e-fixtures" Deployment Job ConfigMap ServiceAccount || STATUS=1
echo ""

echo "========================================"
if [[ "${STATUS}" -eq 0 ]]; then
    log_success "All render assertions passed"
else
    log_error "One or more render assertions failed"
fi
echo "========================================"

exit "${STATUS}"
