#!/bin/bash
#
# Bash wrapper for yaml_helper.py
# Provides convenient YAML manipulation for X-Road configuration files
#
# Usage:
#   yaml_helper.sh get <file> <path>
#   yaml_helper.sh set <file> <path> <value>
#   yaml_helper.sh exists <file> <path>
#

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
YAML_HELPER_PY="${SCRIPT_DIR}/yaml_helper.py"

# If installed in /usr/share/xroad/scripts, use that location
if [ ! -f "$YAML_HELPER_PY" ] && [ -f "/usr/share/xroad/scripts/yaml_helper.py" ]; then
    YAML_HELPER_PY="/usr/share/xroad/scripts/yaml_helper.py"
fi

if [ ! -f "$YAML_HELPER_PY" ]; then
    echo "Error: yaml_helper.py not found" >&2
    echo "Searched locations:" >&2
    echo "  - ${SCRIPT_DIR}/yaml_helper.py" >&2
    echo "  - /usr/share/xroad/scripts/yaml_helper.py" >&2
    exit 1
fi

# Execute the Python script with all arguments
exec python3 "$YAML_HELPER_PY" "$@"

