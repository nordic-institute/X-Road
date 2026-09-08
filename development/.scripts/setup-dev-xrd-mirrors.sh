#!/usr/bin/env bash
# Reads XROAD_MIRROR_* properties from ~/.gradle/gradle.properties
# and exports them as environment variables for X-Road build scripts.
#
# Usage:
#   source development/.scripts/setup-dev-xrd-mirrors.sh
#
# Add to ~/.zshrc or ~/.bashrc:
#   source <path-to-x-road>/development/.scripts/setup-dev-xrd-mirrors.sh

GRADLE_PROPS="${GRADLE_PROPERTIES_FILE:-$HOME/.gradle/gradle.properties}"

if [[ ! -f "$GRADLE_PROPS" ]]; then
  return 0 2>/dev/null || exit 0  # silent no-op if file doesn't exist
fi

_xrd_mirror_count=0

while IFS='=' read -r key value; do
  # Skip comments and empty lines
  [[ "$key" =~ ^[[:space:]]*# ]] && continue
  [[ -z "$key" ]] && continue

  # Trim whitespace
  key=$(echo "$key" | xargs)
  value=$(echo "$value" | xargs)

  # Only export XROAD_MIRROR_* properties
  if [[ "$key" == XROAD_MIRROR_* ]]; then
    export "$key=$value"
    _xrd_mirror_count=$((_xrd_mirror_count + 1))
  fi
done < "$GRADLE_PROPS"

if [[ $_xrd_mirror_count -eq 0 ]]; then
  echo "WARNING: No XROAD_MIRROR_* properties found in $GRADLE_PROPS" >&2
  echo "  See development/docs/developer-mirror-setup.md for setup instructions." >&2
fi

unset _xrd_mirror_count
