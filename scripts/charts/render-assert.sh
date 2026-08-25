#!/usr/bin/env bash
# Helm template render assertions for the k8s charts (deployment/ and development/ trees).
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

# Charts live in three trees: prod/future-prod under deployment/, dev-only
# (test fixtures, stop-gaps) under development/k8s/charts.
chart_dir() {
    case "$1" in
        central-server) echo "${ROOT_DIR}/deployment/central-server/k8s/charts/central-server" ;;
        e2e-fixtures|ds-https-keystore|external-service-bridge) echo "${ROOT_DIR}/development/k8s/charts/$1" ;;
        *) echo "${ROOT_DIR}/deployment/security-server/k8s/charts/$1" ;;
    esac
}

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

assert_kind_count() {
    local out_file="$1" kind="$2" expected="$3"
    local actual
    actual="$(grep -cE "^kind: ${kind}\$" "${out_file}" || true)"
    if [[ "${actual}" -eq "${expected}" ]]; then
        log_success "  ${kind}: ${actual} (expected ${expected})"
    else
        log_error "  ${kind}: found ${actual}, expected ${expected} — the chart's default-values render changed. If deliberate, update this snapshot; if not, a resource was added or dropped unconditionally."
        return 1
    fi
}

render_chart() {
    local chart="$1"
    local chart_path
    chart_path="$(chart_dir "${chart}")"
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
}

render_and_assert() {
    local chart="$1"
    shift
    local expected_kinds=("$@")
    local out_file="${WORK_DIR}/${chart}.yaml"

    render_chart "${chart}" || return 1

    local chart_failed=0
    for kind in "${expected_kinds[@]}"; do
        assert_kind "${out_file}" "${kind}" || chart_failed=1
    done

    return "${chart_failed}"
}

# Exact per-kind resource counts at default values, for charts with non-E2E
# consumers whose default render must not drift by accident. A count change is
# a deliberate act: update the snapshot in the same change that alters the
# render, and say so in the commit message.
render_and_assert_snapshot() {
    local chart="$1"
    shift
    local out_file="${WORK_DIR}/${chart}.yaml"

    render_chart "${chart}" || return 1

    local chart_failed=0
    local pair kind expected
    for pair in "$@"; do
        kind="${pair%%=*}"
        expected="${pair##*=}"
        assert_kind_count "${out_file}" "${kind}" "${expected}" || chart_failed=1
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

render_and_assert "ds-https-keystore" Job ServiceAccount Role RoleBinding || STATUS=1
echo ""

render_and_assert_snapshot "security-server" \
    ConfigMap=7 Deployment=6 Job=3 PersistentVolumeClaim=3 \
    Role=7 RoleBinding=7 Service=6 ServiceAccount=7 || STATUS=1
echo ""

render_and_assert_snapshot "openbao-init" \
    ConfigMap=1 Job=1 Role=1 RoleBinding=1 ServiceAccount=1 || STATUS=1
echo ""

echo "========================================"
if [[ "${STATUS}" -eq 0 ]]; then
    log_success "All render assertions passed"
else
    log_error "One or more render assertions failed"
fi
echo "========================================"

exit "${STATUS}"
