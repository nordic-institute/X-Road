#!/usr/bin/env bash
set -euo pipefail

# --- Configuration ---
# URL for X-Road upgrade package
# This can be overridden by setting the INSTALLER_URL environment variable
INSTALLER_URL="${INSTALLER_URL:-https://artifactory.niis.org/xroad-scripts/0.0.1-beta/}"
PACKAGE_NAME="xroad-installer.tar.gz"
DOWNLOAD_URL="${INSTALLER_URL%/}/${PACKAGE_NAME}"

# Create a temporary directory for extraction
TMP_DIR=$(mktemp -d -t xroad-upgrade-XXXXXX)

echo "----------------------------------------------------------"
echo " X-Road Security Server Upgrade Bootstrapper"
echo "----------------------------------------------------------"

if ! command -v tar >/dev/null; then
    echo "Error: 'tar' is not installed."
    echo "Please install it using one of the following commands:"
    echo "  - RHEL/CentOS/Fedora: sudo dnf install tar"
    echo "  - Debian/Ubuntu:      sudo apt-get install tar"
    exit 1
fi

echo "Downloading X-Road upgrade package..."

if command -v curl >/dev/null; then
    curl -fsSL -o "${TMP_DIR}/${PACKAGE_NAME}" "${DOWNLOAD_URL}"
elif command -v wget >/dev/null; then
    wget -qO "${TMP_DIR}/${PACKAGE_NAME}" "${DOWNLOAD_URL}"
else
    echo "Error: Neither curl nor wget found. Please install one of them."
    exit 1
fi

echo "Extracting package..."
tar -xzf "${TMP_DIR}/${PACKAGE_NAME}" -C "${TMP_DIR}" --strip-components=1

echo "Starting upgrade wizard..."
echo ""

# Capture the current working directory to save the log file there
CALL_DIR="$(pwd)"

# Change directory to the extracted files so it can find its local 'tasks/' and 'lib/'
cd "${TMP_DIR}"

# Execute the main upgrade script.
# We use sudo here as the upgrade requires root privileges for setup and repo configuration.
# Forward all arguments (like --config-file) to the upgrade script.
TIMESTAMP=$(date '+%Y-%m-%d_%H%M%S')
sudo XROAD_INSTALLER_LOG_FILE="${CALL_DIR}/xroad-upgrade-${TIMESTAMP}.log" bash xroad-upgrade.sh "$@"
