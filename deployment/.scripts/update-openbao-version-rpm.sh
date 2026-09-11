#!/bin/bash

# Refresh OpenBao's Version/Variant Gate on an Already-Configured DNF
# Repository (Container Side)
#
# Patches includepkgs/excludepkgs in place on an existing openbao.repo,
# leaving baseurl/username/password untouched. Safe to run regardless of
# whether the repo points at the official repo or a custom mirror, since
# these two lines never depend on which repo is configured.
#
# Usage:
#   ./update-mirror-openbao-rpm.sh
#
# No-ops if the repo file doesn't exist yet — callers should run
# configure-mirror-openbao-rpm.sh instead in that case.

update_openbao_version() {
  local OPENBAO_REPO_FILE="/etc/yum.repos.d/openbao.repo"

  if [ ! -f "$OPENBAO_REPO_FILE" ]; then
    echo "OpenBao repository not configured yet; nothing to update."
    return 0
  fi

  # Keep these values in sync with configure-mirror-openbao-rpm.sh.
  if grep -q '^includepkgs=' "$OPENBAO_REPO_FILE"; then
    sed -i 's/^includepkgs=.*/includepkgs=openbao-2.6.*/' "$OPENBAO_REPO_FILE"
  else
    echo 'includepkgs=openbao-2.6.*' >> "$OPENBAO_REPO_FILE"
  fi
  if grep -q '^excludepkgs=' "$OPENBAO_REPO_FILE"; then
    sed -i 's/^excludepkgs=.*/excludepkgs=openbao-hsm*/' "$OPENBAO_REPO_FILE"
  else
    echo 'excludepkgs=openbao-hsm*' >> "$OPENBAO_REPO_FILE"
  fi

  echo "OpenBao repository gate refreshed."
}

# EXECUTION GUARD
update_openbao_version
