#!/usr/bin/env bash
set -euo pipefail

# Helper script to publish X-Road installer (plus migration-cli) to an Artifactory repository.
# Requirements: curl, tar, md5sum, sha1sum, sha256sum, JDK + gradlew (for migration-cli build)

: "${ARTIFACTORY_BASE_URL:=https://artifactory.niis.org}"
: "${ARTIFACTORY_REPO:=xroad-scripts}"

usage() {
    echo "Usage: $0 <folder-name>"
    echo ""
    echo "Uploads the X-Road installer archive plus the get-xroad.sh and upgrade-xroad.sh"
    echo "bootstrappers to ${ARTIFACTORY_BASE_URL}/${ARTIFACTORY_REPO}/<folder-name>/."
    echo ""
    echo "Required env vars:"
    echo "  ARTIFACTORY_USER       Artifactory username"
    echo "  ARTIFACTORY_PASSWORD   Artifactory password or API token"
    echo ""
    echo "Optional env vars:"
    echo "  ARTIFACTORY_BASE_URL   default: https://artifactory.niis.org"
    echo "  ARTIFACTORY_REPO       default: xroad-scripts"
    exit 1
}

if [[ $# -lt 1 ]]; then
    usage
fi

: "${ARTIFACTORY_USER:?ARTIFACTORY_USER is required}"
: "${ARTIFACTORY_PASSWORD:?ARTIFACTORY_PASSWORD is required}"

FOLDER="$1"
TARGET_BASE="${ARTIFACTORY_BASE_URL}/${ARTIFACTORY_REPO}/${FOLDER}"

PACKAGE_DIR="xroad-installer"
PACKAGE_NAME="xroad-installer.tar.gz"
GET_XROAD_SCRIPT="${PACKAGE_DIR}/get-xroad.sh"
UPGRADE_XROAD_SCRIPT="${PACKAGE_DIR}/upgrade-xroad.sh"
DOWNLOAD_MIGRATION_CLI_SCRIPT="${PACKAGE_DIR}/tasks/migration/download_migration_cli.sh"

upload_to_artifactory() {
    local src="$1"
    local dest_name="$2"
    local md5 sha1 sha256
    md5=$(md5sum "$src" | cut -d' ' -f1)
    sha1=$(sha1sum "$src" | cut -d' ' -f1)
    sha256=$(sha256sum "$src" | cut -d' ' -f1)
    curl -fSL --user "${ARTIFACTORY_USER}:${ARTIFACTORY_PASSWORD}" \
        -XPUT -T "$src" \
        -H "X-Checksum-Md5:${md5}" \
        -H "X-Checksum-Sha1:${sha1}" \
        -H "X-Checksum-Sha256:${sha256}" \
        "${TARGET_BASE}/${dest_name}"
    echo
}

REPO_ROOT="../.."
MIGRATION_CLI_BUILD_LIBS="${REPO_ROOT}/src/tool/migration-cli/build/libs"

# Base URL for uploaded artifacts (trailing slash matches existing INSTALLER_URL convention).
BASE_URL="${TARGET_BASE}/"

# ===== Build phase =====

echo "Step 1: Building migration-cli (./gradlew :tool:migration-cli:shadowJar)..."
(cd "${REPO_ROOT}/src" && ./gradlew :tool:migration-cli:shadowJar)

# Gradle includes the project version in the filename (e.g. migration-cli-1.0.jar).
# Resolve at runtime so future version bumps don't break the script.
MIGRATION_CLI_JAR_PATH=$(ls -1 "${MIGRATION_CLI_BUILD_LIBS}"/migration-cli-*.jar 2>/dev/null | head -1 || true)
if [[ -z "$MIGRATION_CLI_JAR_PATH" || ! -f "$MIGRATION_CLI_JAR_PATH" ]]; then
    echo "ERROR: migration-cli build did not produce a jar under ${MIGRATION_CLI_BUILD_LIBS}/" >&2
    exit 1
fi
echo "Resolved migration-cli artifact: $MIGRATION_CLI_JAR_PATH"

MIGRATION_CLI_URL="${BASE_URL}migration-cli.jar"

echo "Step 2: Rewriting XROAD_MIGRATION_CLI_URL in ${DOWNLOAD_MIGRATION_CLI_SCRIPT}..."
# Must run BEFORE the tarball is built — this file ships inside the tarball.
sed -i.bak "s|XROAD_MIGRATION_CLI_URL=\"\${XROAD_MIGRATION_CLI_URL:-.*}\"|XROAD_MIGRATION_CLI_URL=\"\${XROAD_MIGRATION_CLI_URL:-${MIGRATION_CLI_URL}}\"|" "$DOWNLOAD_MIGRATION_CLI_SCRIPT"

echo "Step 3: Preparing installer files and creating tarball of ${PACKAGE_DIR}..."
# Copy required scripts from repo root to installer lib
cp "${REPO_ROOT}/deployment/native-packages/src/xroad/common/base/usr/share/xroad/scripts/_setup_memory.sh" "${PACKAGE_DIR}/lib/"
cp "${REPO_ROOT}/deployment/native-packages/src/xroad/common/proxy/usr/share/xroad/scripts/proxy_memory_helper.sh" "${PACKAGE_DIR}/lib/"
cp "${REPO_ROOT}/deployment/native-packages/src/xroad/common/helper-scripts/yaml_helper.sh" "${PACKAGE_DIR}/lib/"
cp "${REPO_ROOT}/deployment/native-packages/src/xroad/common/helper-scripts/yaml_helper.py" "${PACKAGE_DIR}/lib/"
cp "${REPO_ROOT}/deployment/.scripts/configure-mirror-openbao-deb.sh" "${PACKAGE_DIR}/lib/"
cp "${REPO_ROOT}/deployment/.scripts/configure-mirror-openbao-rpm.sh" "${PACKAGE_DIR}/lib/"

# Exclude bootstrap scripts (uploaded separately), macOS metadata, extended attributes, and sed .bak files.
COPYFILE_DISABLE=1 tar -czf "$PACKAGE_NAME" --no-xattrs \
    --exclude="get-xroad.sh" \
    --exclude="upgrade-xroad.sh" \
    --exclude="._*" \
    --exclude=".gitignore" \
    --exclude="*.bak" \
    "$PACKAGE_DIR/"

echo "Step 4: Rewriting INSTALLER_URL in ${GET_XROAD_SCRIPT}..."
sed -i.bak "s|INSTALLER_URL=\"\${INSTALLER_URL:-.*}\"|INSTALLER_URL=\"\${INSTALLER_URL:-${BASE_URL}}\"|" "$GET_XROAD_SCRIPT"

echo "Step 5: Rewriting INSTALLER_URL in ${UPGRADE_XROAD_SCRIPT}..."
sed -i.bak "s|INSTALLER_URL=\"\${INSTALLER_URL:-.*}\"|INSTALLER_URL=\"\${INSTALLER_URL:-${BASE_URL}}\"|" "$UPGRADE_XROAD_SCRIPT"

# ===== Publish phase =====

echo "Step 6: Uploading ${PACKAGE_NAME} to ${TARGET_BASE}/..."
upload_to_artifactory "$PACKAGE_NAME" "$PACKAGE_NAME"

echo "Step 7: Uploading ${GET_XROAD_SCRIPT} to ${TARGET_BASE}/..."
upload_to_artifactory "$GET_XROAD_SCRIPT" "get-xroad.sh"

echo "Step 8: Uploading ${UPGRADE_XROAD_SCRIPT} to ${TARGET_BASE}/..."
upload_to_artifactory "$UPGRADE_XROAD_SCRIPT" "upgrade-xroad.sh"

echo "Step 9: Uploading migration-cli.jar to ${TARGET_BASE}/..."
upload_to_artifactory "$MIGRATION_CLI_JAR_PATH" "migration-cli.jar"

INSTALL_LINK="${TARGET_BASE}/get-xroad.sh"
UPGRADE_LINK="${TARGET_BASE}/upgrade-xroad.sh"
MIGRATION_CLI_LINK="${TARGET_BASE}/migration-cli.jar"

echo "----------------------------------------------------------"
echo " SUCCESS!"
echo "----------------------------------------------------------"
echo "Artifactory folder:     ${TARGET_BASE}/"
echo "The installer package:  ${BASE_URL}${PACKAGE_NAME}"
echo "Install bootstrap:      ${INSTALL_LINK}"
echo "Upgrade bootstrap:      ${UPGRADE_LINK}"
echo "Migration CLI:          ${MIGRATION_CLI_LINK}"
echo ""
echo "Users can install X-Road using:"
echo "sudo bash -c \"\$(curl -sSfL ${INSTALL_LINK})\" --"
echo ""
echo "Users can upgrade X-Road (7.8.x -> 8.0) using:"
echo "sudo bash -c \"\$(curl -sSfL ${UPGRADE_LINK})\" --"
echo "----------------------------------------------------------"

# Cleanup
rm -f "${GET_XROAD_SCRIPT}.bak" "${UPGRADE_XROAD_SCRIPT}.bak" "${DOWNLOAD_MIGRATION_CLI_SCRIPT}.bak"
