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
        e2e-fixtures) echo "${ROOT_DIR}/development/k8s/charts/$1" ;;
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

# The op-monitor service is off by default; render it enabled and check its ports
# (gRPC port 2081 declared, port 2080 absent), that its Deployment has no
# wait-config-seed init container, and that no xroad.op-monitor.scheme key is referenced.
render_and_assert_op_monitor_ports() {
    local chart="security-server"
    local chart_path
    chart_path="$(chart_dir "${chart}")"
    local out_file="${WORK_DIR}/${chart}-op-monitor.yaml"
    local err_file="${WORK_DIR}/${chart}-op-monitor.err"

    log_info "=== ${chart} with op-monitor enabled ==="
    if ! helm template "test-${chart}" "${chart_path}" --set services.op-monitor.enabled=true \
            > "${out_file}" 2> "${err_file}"; then
        log_error "helm template failed for ${chart} with op-monitor enabled:"
        cat "${err_file}" >&2
        return 1
    fi

    local failed=0
    if grep -qE '^\s+- port: 2081$' "${out_file}"; then
        log_success "  op-monitor declares port 2081"
    else
        log_error "  op-monitor does not declare port 2081"
        failed=1
    fi
    if grep -qE '(^|[^0-9])2080([^0-9]|$)' "${out_file}"; then
        log_error "  the render still contains port 2080"
        failed=1
    else
        log_success "  port 2080 is absent"
    fi

    local op_monitor_deployment
    op_monitor_deployment="$(awk '
        function flush() {
            if (is_deployment && is_op_monitor) {
                found = 1
                if (has_wait) { wait = 1 }
            }
            is_deployment = is_op_monitor = has_wait = 0
        }
        /^---$/ { flush(); next }
        /^kind: Deployment$/ { is_deployment = 1 }
        /^  name: op-monitor$/ { is_op_monitor = 1 }
        /^ *- name: wait-config-seed$/ { has_wait = 1 }
        END { flush(); print (found ? (wait ? "has-wait" : "clean") : "missing") }
    ' "${out_file}")"
    case "${op_monitor_deployment}" in
        clean) log_success "  op-monitor Deployment has no wait-config-seed init container" ;;
        has-wait)
            log_error "  op-monitor Deployment still has a wait-config-seed init container"
            failed=1 ;;
        *)
            log_error "  op-monitor Deployment not found in the render"
            failed=1 ;;
    esac
    if grep -q 'xroad\.op-monitor\.scheme' "${out_file}"; then
        log_error "  the render still references xroad.op-monitor.scheme"
        failed=1
    else
        log_success "  xroad.op-monitor.scheme is absent"
    fi
    return "${failed}"
}

echo "========================================"
log_info "Helm template render assertions"
echo "========================================"
echo ""

render_and_assert "central-server" Deployment Service ConfigMap ServiceAccount || STATUS=1
echo ""

render_and_assert "e2e-fixtures" Deployment Job ConfigMap || STATUS=1
echo ""

render_and_assert_snapshot "security-server" \
    ConfigMap=7 Deployment=6 Job=3 PersistentVolumeClaim=4 \
    Role=7 RoleBinding=7 Service=6 ServiceAccount=7 || STATUS=1
echo ""

render_and_assert_op_monitor_ports || STATUS=1
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
