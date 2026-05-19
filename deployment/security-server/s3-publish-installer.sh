#!/usr/bin/env bash
set -euo pipefail

# Helper script to publish X-Road installer to an AWS S3 bucket for testing.
# Requirements: aws cli, tar
#
# All staging (helper-file copies, sed rewrites, tar creation) happens under build/ so the
# tracked xroad-installer/ source tree stays clean — `git status` is empty after a run.

S3_BUCKET="niis-xroad-development"

usage() {
    echo "Usage: $0 <folder-name>"
    echo ""
    echo "Uploads the X-Road installer archive plus the get-xroad.sh and upgrade-xroad.sh bootstrappers to s3://${S3_BUCKET}/<folder-name>/."
    exit 1
}

if [[ $# -lt 1 ]]; then
    usage
fi

TIMESTAMP=$(date '+%Y%m%d-%H%M%S')
S3_FOLDER="$1/xroad-installer-${TIMESTAMP}"

# Source tree (read-only) and disposable staging tree (under build/).
SRC_PACKAGE_DIR="xroad-installer"
BUILD_DIR="build"
STAGE_DIR="${BUILD_DIR}/xroad-installer"
PACKAGE_DIR="${STAGE_DIR}"
PACKAGE_NAME="xroad-installer.tar.gz"
GET_XROAD_SCRIPT="${PACKAGE_DIR}/get-xroad.sh"
UPGRADE_XROAD_SCRIPT="${PACKAGE_DIR}/upgrade-xroad.sh"

REPO_ROOT="../.."

echo "Step 0: Preparing clean staging area under ${BUILD_DIR}/..."
rm -rf "${BUILD_DIR}"
mkdir -p "${BUILD_DIR}"
cp -R "${SRC_PACKAGE_DIR}" "${STAGE_DIR}"

echo "Step 1: Preparing files and creating tarball of ${PACKAGE_DIR}..."

# Copy required scripts from repo root to staged installer lib
cp "${REPO_ROOT}/deployment/native-packages/src/xroad/common/base/usr/share/xroad/scripts/_setup_memory.sh" "${PACKAGE_DIR}/lib/"
cp "${REPO_ROOT}/deployment/native-packages/src/xroad/common/proxy/usr/share/xroad/scripts/proxy_memory_helper.sh" "${PACKAGE_DIR}/lib/"
cp "${REPO_ROOT}/deployment/native-packages/src/xroad/common/helper-scripts/yaml_helper.sh" "${PACKAGE_DIR}/lib/"
cp "${REPO_ROOT}/deployment/native-packages/src/xroad/common/helper-scripts/yaml_helper.py" "${PACKAGE_DIR}/lib/"
cp "${REPO_ROOT}/deployment/.scripts/configure-mirror-openbao-deb.sh" "${PACKAGE_DIR}/lib/"
cp "${REPO_ROOT}/deployment/.scripts/configure-mirror-openbao-rpm.sh" "${PACKAGE_DIR}/lib/"

# Exclude bootstrap scripts (get-xroad.sh, upgrade-xroad.sh), macOS metadata (._ files), and extended attributes.
# Run tar with -C build so entries inside the archive keep the xroad-installer/ prefix
# (downstream get-xroad.sh / upgrade-xroad.sh extract that directory by name).
COPYFILE_DISABLE=1 tar -C "${BUILD_DIR}" -czf "${BUILD_DIR}/${PACKAGE_NAME}" --no-xattrs --exclude="get-xroad.sh" --exclude="upgrade-xroad.sh" --exclude="._*" "xroad-installer/"

echo "Step 2: Uploading ${PACKAGE_NAME} to s3://${S3_BUCKET}/${S3_FOLDER}/..."
aws s3 cp "${BUILD_DIR}/${PACKAGE_NAME}" "s3://${S3_BUCKET}/${S3_FOLDER}/${PACKAGE_NAME}"

# Build the base URL for the uploaded archive
BASE_URL="https://${S3_BUCKET}.s3.amazonaws.com/${S3_FOLDER}/"

echo "Step 3: Updating ${GET_XROAD_SCRIPT} with new INSTALLER_URL..."
sed -i.bak "s|INSTALLER_URL=\"\${INSTALLER_URL:-.*}\"|INSTALLER_URL=\"\${INSTALLER_URL:-${BASE_URL}}\"|" "$GET_XROAD_SCRIPT"

echo "Step 4: Uploading updated ${GET_XROAD_SCRIPT} to s3://${S3_BUCKET}/${S3_FOLDER}/..."
aws s3 cp "$GET_XROAD_SCRIPT" "s3://${S3_BUCKET}/${S3_FOLDER}/get-xroad.sh"

echo "Step 5: Updating ${UPGRADE_XROAD_SCRIPT} with new INSTALLER_URL..."
sed -i.bak "s|INSTALLER_URL=\"\${INSTALLER_URL:-.*}\"|INSTALLER_URL=\"\${INSTALLER_URL:-${BASE_URL}}\"|" "$UPGRADE_XROAD_SCRIPT"

echo "Step 6: Uploading updated ${UPGRADE_XROAD_SCRIPT} to s3://${S3_BUCKET}/${S3_FOLDER}/..."
aws s3 cp "$UPGRADE_XROAD_SCRIPT" "s3://${S3_BUCKET}/${S3_FOLDER}/upgrade-xroad.sh"

INSTALL_LINK="https://${S3_BUCKET}.s3.amazonaws.com/${S3_FOLDER}/get-xroad.sh"
UPGRADE_LINK="https://${S3_BUCKET}.s3.amazonaws.com/${S3_FOLDER}/upgrade-xroad.sh"

echo "----------------------------------------------------------"
echo " SUCCESS!"
echo "----------------------------------------------------------"
echo "S3 folder:              s3://${S3_BUCKET}/${S3_FOLDER}/"
echo "The installer package:  ${BASE_URL}${PACKAGE_NAME}"
echo "Install bootstrap:      ${INSTALL_LINK}"
echo "Upgrade bootstrap:      ${UPGRADE_LINK}"
echo ""
echo "Users can install X-Road using:"
echo "sudo bash -c \"\$(curl -sSfL ${INSTALL_LINK})\" --"
echo ""
echo "Users can upgrade X-Road (7.8.x -> 8.0) using:"
echo "sudo bash -c \"\$(curl -sSfL ${UPGRADE_LINK})\" --"
echo "----------------------------------------------------------"
