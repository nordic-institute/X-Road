#!/bin/bash
set -e
# shellcheck source=./_common.sh
source "${BASH_SOURCE%/*}/_common.sh"

cd "${K8S_ROOT}"

require_bin ansible-lint "python3 -m pip install ansible-lint" || exit 1
require_bin yamllint "python3 -m pip install yamllint" || exit 1

log_info "Running yamllint"
yamllint .

log_info "Running ansible-lint"
ansible-lint

log_success "Lint clean."
