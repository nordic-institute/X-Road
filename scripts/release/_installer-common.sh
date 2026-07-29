# shellcheck shell=bash
#
# Shared helpers for publishing the X-Road installer.
# Sourced by installer-s3.sh and installer-artifactory.sh.
#
# Caller contract:
#   - Must `set -euo pipefail` before sourcing.
#   - Must set $REPO_ROOT (relative or absolute) before calling copy_helper_scripts
#     or build_migration_cli.
#   - Must define a shell function named `upload <src> <dest_filename>` that ships
#     a single file to the destination chosen by the caller.

SRC_PACKAGE_DIR="xroad-installer"
BUILD_DIR="build"
STAGE_DIR="${BUILD_DIR}/xroad-installer"
PACKAGE_DIR="${STAGE_DIR}"
PACKAGE_NAME="xroad-installer.tar.gz"
GET_XROAD_SCRIPT="${PACKAGE_DIR}/get-xroad.sh"
UPGRADE_XROAD_SCRIPT="${PACKAGE_DIR}/upgrade-xroad.sh"
DOWNLOAD_MIGRATION_CLI_SCRIPT="${PACKAGE_DIR}/tasks/migration/download_migration_cli.sh"

# Default caller cwd: scripts/release/. Caller may override.
: "${REPO_ROOT:=../..}"

prepare_staging() {
    echo "Preparing clean staging area under ${BUILD_DIR}/..." >&2
    rm -rf "${BUILD_DIR}"
    mkdir -p "${BUILD_DIR}"
    cp -R "${SRC_PACKAGE_DIR}" "${STAGE_DIR}"
}

copy_helper_scripts() {
    echo "Copying helper scripts into ${PACKAGE_DIR}/lib/..." >&2
    cp "${REPO_ROOT}/deployment/native-packages/src/xroad/common/base/usr/share/xroad/scripts/_setup_memory.sh" "${PACKAGE_DIR}/lib/"
    cp "${REPO_ROOT}/deployment/native-packages/src/xroad/common/proxy/usr/share/xroad/scripts/proxy_memory_helper.sh" "${PACKAGE_DIR}/lib/"
    cp "${REPO_ROOT}/deployment/native-packages/src/xroad/common/helper-scripts/yaml_helper.sh" "${PACKAGE_DIR}/lib/"
    cp "${REPO_ROOT}/deployment/native-packages/src/xroad/common/helper-scripts/yaml_helper.py" "${PACKAGE_DIR}/lib/"
    cp "${REPO_ROOT}/deployment/.scripts/configure-mirror-openbao-deb.sh" "${PACKAGE_DIR}/lib/"
    cp "${REPO_ROOT}/deployment/.scripts/configure-mirror-openbao-rpm.sh" "${PACKAGE_DIR}/lib/"
}

# Builds migration-cli via Gradle and echoes the resolved jar path on stdout.
# All progress output goes to stderr so the caller can capture the path via $(...).
build_migration_cli() {
    echo "Building migration-cli (./gradlew :tool:migration-cli:shadowJar)..." >&2
    (cd "${REPO_ROOT}/src" && ./gradlew :tool:migration-cli:shadowJar) >&2

    local libs_dir="${REPO_ROOT}/src/tool/migration-cli/build/libs"
    local jar
    # shellcheck disable=SC2012 # gradle-controlled filename (migration-cli-<version>.jar), safe to use ls
    jar=$(ls -1 "${libs_dir}"/migration-cli-*.jar 2>/dev/null | head -1 || true)
    if [[ -z "$jar" || ! -f "$jar" ]]; then
        echo "ERROR: migration-cli build did not produce a jar under ${libs_dir}/" >&2
        return 1
    fi
    echo "Resolved migration-cli artifact: $jar" >&2
    echo "$jar"
}

rewrite_migration_cli_url() {
    local url="$1"
    echo "Rewriting XROAD_MIGRATION_CLI_URL in ${DOWNLOAD_MIGRATION_CLI_SCRIPT}..." >&2
    sed -i.bak "s|XROAD_MIGRATION_CLI_URL=\"\${XROAD_MIGRATION_CLI_URL:-.*}\"|XROAD_MIGRATION_CLI_URL=\"\${XROAD_MIGRATION_CLI_URL:-${url}}\"|" "$DOWNLOAD_MIGRATION_CLI_SCRIPT"
}

# Excludes the bootstrap scripts (uploaded separately as plain files), macOS metadata,
# extended attributes, and sed backup files left by any earlier rewrite_* calls.
build_installer_tarball() {
    echo "Creating tarball ${BUILD_DIR}/${PACKAGE_NAME}..." >&2
    COPYFILE_DISABLE=1 tar -C "${BUILD_DIR}" -czf "${BUILD_DIR}/${PACKAGE_NAME}" --no-xattrs \
        --exclude="get-xroad.sh" \
        --exclude="upgrade-xroad.sh" \
        --exclude="._*" \
        --exclude="*.bak" \
        "xroad-installer/"
}

rewrite_installer_url() {
    local url="$1"
    echo "Rewriting INSTALLER_URL in ${GET_XROAD_SCRIPT} and ${UPGRADE_XROAD_SCRIPT}..." >&2
    sed -i.bak "s|INSTALLER_URL=\"\${INSTALLER_URL:-.*}\"|INSTALLER_URL=\"\${INSTALLER_URL:-${url}}\"|" "$GET_XROAD_SCRIPT"
    sed -i.bak "s|INSTALLER_URL=\"\${INSTALLER_URL:-.*}\"|INSTALLER_URL=\"\${INSTALLER_URL:-${url}}\"|" "$UPGRADE_XROAD_SCRIPT"
}

# Usage: print_success_banner <folder_url> <package_url> <install_link> <upgrade_link> [<migration_cli_link>]
print_success_banner() {
    local folder_url="$1"
    local package_url="$2"
    local install_link="$3"
    local upgrade_link="$4"
    local migration_cli_link="${5:-}"

    echo "----------------------------------------------------------"
    echo " SUCCESS!"
    echo "----------------------------------------------------------"
    echo "Published folder:       ${folder_url}"
    echo "The installer package:  ${package_url}"
    echo "Install bootstrap:      ${install_link}"
    echo "Upgrade bootstrap:      ${upgrade_link}"
    if [[ -n "$migration_cli_link" ]]; then
        echo "Migration CLI:          ${migration_cli_link}"
    fi
    echo ""
    echo "Users can install X-Road using:"
    echo "sudo bash -c \"\$(curl -sSfL ${install_link})\" --"
    echo ""
    echo "Users can upgrade X-Road (7.8.x -> 8.0) using:"
    echo "sudo bash -c \"\$(curl -sSfL ${upgrade_link})\" --"
    echo "----------------------------------------------------------"
}

# Usage: publish_installer <base_url> <folder_url>
#   <base_url>    — trailing-slash URL prefix used inside published scripts and the banner.
#   <folder_url>  — human-readable folder identifier shown in the success banner
#                   (e.g. "s3://bucket/path/" or "https://artifactory.../folder/").
#
# Requires the caller to have defined a function `upload <src> <dest_filename>`
# that ships a single file to the chosen destination.
publish_installer() {
    local base_url="$1"
    local folder_url="$2"

    prepare_staging
    local migration_cli_jar
    migration_cli_jar=$(build_migration_cli)
    rewrite_migration_cli_url "${base_url}migration-cli.jar"
    copy_helper_scripts
    build_installer_tarball
    rewrite_installer_url "${base_url}"

    upload "${BUILD_DIR}/${PACKAGE_NAME}" "${PACKAGE_NAME}"
    upload "${GET_XROAD_SCRIPT}"          "get-xroad.sh"
    upload "${UPGRADE_XROAD_SCRIPT}"      "upgrade-xroad.sh"
    upload "${migration_cli_jar}"         "migration-cli.jar"

    print_success_banner \
        "${folder_url}" \
        "${base_url}${PACKAGE_NAME}" \
        "${base_url}get-xroad.sh" \
        "${base_url}upgrade-xroad.sh" \
        "${base_url}migration-cli.jar"
}
