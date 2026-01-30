#!/usr/bin/env bash
set -euo pipefail

# !!! NOT FOR PRODUCTION USE!!!

# Helper script to publish X-Road installer to tmpfile.link for easier testing in dev environments.
# Requirements: curl, jq (optional, will fallback to sed)

PACKAGE_DIR="xroad-installer"
PACKAGE_NAME="xroad-installer.tar.gz"
GET_XROAD_SCRIPT="${PACKAGE_DIR}/get-xroad.sh"

echo "Step 1: Preparing files and creating tarball of ${PACKAGE_DIR}..."

# Copy memory scripts from repo root to installer lib
REPO_ROOT="../.."
cp "${REPO_ROOT}/deployment/native-packages/src/xroad/common/base/usr/share/xroad/scripts/_setup_memory.sh" "${PACKAGE_DIR}/lib/"
cp "${REPO_ROOT}/deployment/native-packages/src/xroad/common/proxy/usr/share/xroad/scripts/proxy_memory_helper.sh" "${PACKAGE_DIR}/lib/"

# Exclude get-xroad.sh, macOS metadata (._ files), and extended attributes
COPYFILE_DISABLE=1 tar -czf "$PACKAGE_NAME" --no-xattrs --exclude="get-xroad.sh" --exclude="._*" "$PACKAGE_DIR/"

echo "Step 2: Uploading ${PACKAGE_NAME} to tmpfile.link..."
RESPONSE=$(curl -s -X POST https://tmpfile.link/api/upload -F "file=@$PACKAGE_NAME")

# Extract the download link
if command -v jq >/dev/null; then
    PKG_LINK=$(echo "$RESPONSE" | jq -r '.downloadLink')
else
    PKG_LINK=$(echo "$RESPONSE" | sed -n 's/.*"downloadLink":"\([^"]*\)".*/\1/p')
fi

if [[ -z "$PKG_LINK" ]]; then
    echo "Error: Failed to upload ${PACKAGE_NAME}."
    echo "Response: $RESPONSE"
    exit 1
fi

echo "Package uploaded to: $PKG_LINK"

# The current get-xroad.sh logic is:
# ARTIFACTORY_URL="${ARTIFACTORY_URL:-...}"
# PACKAGE_NAME="xroad-installer.tar.gz"
# DOWNLOAD_URL="${ARTIFACTORY_URL}/${PACKAGE_NAME}"
#
# tmpfile.link gives https://d.tmpfile.link/public/YYYY-MM-DD/UUID/filename
# So we extract the directory part
BASE_URL="${PKG_LINK%/*}/"

echo "Step 3: Updating ${GET_XROAD_SCRIPT} with new ARTIFACTORY_URL..."
# Use sed to update the ARTIFACTORY_URL line
# We look for the line starting with ARTIFACTORY_URL="${ARTIFACTORY_URL:- and replace it
sed -i.bak "s|ARTIFACTORY_URL=\"\${ARTIFACTORY_URL:-.*}\"|ARTIFACTORY_URL=\"\${ARTIFACTORY_URL:-${BASE_URL}}\"|" "$GET_XROAD_SCRIPT"

echo "Step 4: Uploading updated ${GET_XROAD_SCRIPT} to tmpfile.link..."
GET_XROAD_RESPONSE=$(curl -s -X POST https://tmpfile.link/api/upload -F "file=@$GET_XROAD_SCRIPT")

if command -v jq >/dev/null; then
    FINAL_LINK=$(echo "$GET_XROAD_RESPONSE" | jq -r '.downloadLink')
else
    FINAL_LINK=$(echo "$GET_XROAD_RESPONSE" | sed -n 's/.*"downloadLink":"\([^"]*\)".*/\1/p')
fi

if [[ -z "$FINAL_LINK" ]]; then
  echo "Error: Failed to upload ${GET_XROAD_SCRIPT}."
  echo "Response: $GET_XROAD_RESPONSE"
  exit 1
fi

echo "----------------------------------------------------------"
echo " SUCCESS!"
echo "----------------------------------------------------------"
echo "The installer package is at: $PKG_LINK"
echo "The bootstrap script is at:  $FINAL_LINK"
echo ""
echo "Users can now install X-Road using:"
echo "sudo bash -c \"\$(curl -sSfL $FINAL_LINK)\" -- --skip-requirements-check"
echo "----------------------------------------------------------"

# Cleanup
#rm "$PACKAGE_NAME"
rm "${GET_XROAD_SCRIPT}.bak"
#rm "${PACKAGE_DIR}/lib/_setup_memory.sh"
#rm "${PACKAGE_DIR}/lib/proxy_memory_helper.sh"
