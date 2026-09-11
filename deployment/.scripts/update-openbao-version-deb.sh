#!/bin/bash

# Refresh OpenBao's Version/Pin on an Already-Configured APT Repository
# (Container Side)
#
# Rewrites the APT preferences pin unconditionally. Safe regardless of
# whether the repo points at the official repo or a custom mirror, since the
# pin file carries no mirror-specific content at all.
#
# Usage:
#   ./update-mirror-openbao-deb.sh
#
# No-ops if the repo isn't configured yet — callers should run
# configure-mirror-openbao-deb.sh instead in that case.

update_openbao_version() {
  local OPENBAO_REPO_FILE="/etc/apt/sources.list.d/openbao.list"
  local OPENBAO_PIN_FILE="/etc/apt/preferences.d/openbao.pref"

  if [ ! -f "$OPENBAO_REPO_FILE" ]; then
    echo "OpenBao repository not configured yet; nothing to update."
    return 0
  fi

  # Keep this value in sync with configure-mirror-openbao-deb.sh.
  cat <<'EOF' > "$OPENBAO_PIN_FILE"
Package: openbao
Pin: version 2.6.*
Pin-Priority: 1001
EOF

  echo "OpenBao APT pin refreshed."
}

# EXECUTION GUARD
update_openbao_version
