#!/usr/bin/env bash
set -euo pipefail

# --- Configuration ---
# Artifactory URL for X-Road generic repository
# This can be overridden by setting the ARTIFACTORY_URL environment variable
ARTIFACTORY_URL="${ARTIFACTORY_URL:-https://artifactory.nordic-institute.eu/artifactory/xroad-generic}"
PACKAGE_NAME="xroad-installer.tar.gz"
DOWNLOAD_URL="${ARTIFACTORY_URL%/}/${PACKAGE_NAME}"

# Create a temporary directory for extraction
TMP_DIR=$(mktemp -d -t xroad-installer-XXXXXX)

# Ensure cleanup on exit todo?
#cleanup() {
#    local exit_code=$?
#    rm -rf "$TMP_DIR"
#    exit $exit_code
#}
#trap cleanup EXIT

echo "----------------------------------------------------------"
echo " X-Road Security Server Installer Bootstrapper"
echo "----------------------------------------------------------"
echo "Downloading full installer package..."

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

echo "Starting installer..."
echo ""

# Capture the current working directory to save the log file there
CALL_DIR="$(pwd)"

# Change directory to the extracted files so it can find its local 'tasks/' and 'lib/'
cd "${TMP_DIR}"

# Execute the main installer. 
# We use sudo here as the installer requires root privileges for setup and repo configuration.
# Forward all arguments (like --config-file or --skip-requirements-check) to the actual script.
TIMESTAMP=$(date '+%Y-%m-%d_%H%M%S')
sudo XROAD_INSTALLER_LOG_FILE="${CALL_DIR}/xroad-installer-${TIMESTAMP}.log" bash xroad-installer.sh "$@"
