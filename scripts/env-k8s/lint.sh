#!/bin/bash
set -e
# shellcheck source=./_common.sh
source "${BASH_SOURCE%/*}/_common.sh"

cd "${K8S_ROOT}"

ensure_k8s_deps || exit 1

require_bin yamllint "python3 -m pip install yamllint" || exit 1
require_bin ansible-playbook "python3 -m pip install ansible-core" || exit 1
require_bin helm "brew install helm" || exit 1

log_info "Running yamllint"
yamllint .

# Informational only, same convention as render-assert.sh's "helm lint (informational)"
# step: ansible-lint's production profile currently reports ~145 pre-existing findings
# (var-naming, schema, meta-no-tags, ...) across the tree, unrelated to this change and
# out of scope to fix here — tracked separately. --offline skips ansible-lint's own
# `ansible-galaxy collection install` (broken for the git-pinned kubernetes.core ref —
# see ensure_k8s_deps's --force comment in _common.sh); ensure_k8s_deps above already
# installed the collections this run needs.
require_bin ansible-lint "python3 -m pip install ansible-lint" || exit 1
log_info "Running ansible-lint (informational; pre-existing findings not gated)"
ansible-lint --offline || log_warn "ansible-lint reported findings (non-blocking, see above)"

# Structural syntax validation, all four inventories: catches YAML/Jinja parse errors
# and undefined role/task references without executing any task.
log_info "Running ansible-playbook --syntax-check (all inventories)"
for syntax_env in dev e2e test eks; do
  log_info "  syntax-check: ${syntax_env}"
  run_ansible_playbook playbooks/site.yml -i "inventory/${syntax_env}" --syntax-check
  run_ansible_playbook playbooks/render_check.yml -i "inventory/${syntax_env}" --syntax-check
done

# Cluster-free render validation, all four inventories: renders each inventory's Helm
# values (playbooks/render_check.yml — genuine role/inventory variable precedence, no
# live cluster) and feeds the result through `helm template` against this checkout's
# chart sources, catching both Jinja/YAML render breakage and chart-level required-value
# errors before a real deploy. See render_check.yml for why it does not reuse
# playbooks/site.yml's per-instance loop construct.
CHART_SECURITY_SERVER="${CORE_ROOT}/deployment/security-server/k8s/charts/security-server"
CHART_CENTRAL_SERVER="${CORE_ROOT}/deployment/central-server/k8s/charts/central-server"

log_info "Running cluster-free Helm values render + template checks (all inventories)"
for render_env in dev e2e test eks; do
  log_info "  render-check: ${render_env}"
  render_dir="${STATE_DIR}/render-check/${render_env}"
  rm -rf "${render_dir}"
  run_ansible_playbook playbooks/render_check.yml \
    -i "inventory/${render_env}" \
    -e "render_output_dir=${render_dir}"

  shopt -s nullglob
  for values_file in "${render_dir}"/*.yaml; do
    case "$(basename "${values_file}")" in
      central-server-values.yaml) chart="${CHART_CENTRAL_SERVER}" ;;
      *) chart="${CHART_SECURITY_SERVER}" ;;
    esac
    log_info "    helm template: $(basename "${values_file}")"
    helm template "render-check-${render_env}" "${chart}" -f "${values_file}" > /dev/null
  done
  shopt -u nullglob
done

log_success "Lint clean."
