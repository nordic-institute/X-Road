#!/usr/bin/env bash
set -euo pipefail

# Helper script to publish X-Road installer (plus migration-cli) to an Artifactory repository.
# Requirements: curl, tar, md5sum, sha1sum, sha256sum, JDK + gradlew (for migration-cli build)
#
# All staging (helper-file copies, sed rewrites, tar creation) happens under build/ so the
# tracked xroad-installer/ source tree stays clean — `git status` is empty after a run.

: "${ARTIFACTORY_BASE_URL:=https://artifactory.niis.org}"
: "${ARTIFACTORY_REPO:=xroad-scripts}"

usage() {
    echo "Usage: $0 <folder-name>"
    echo ""
    echo "Uploads the X-Road installer archive, get-xroad.sh, upgrade-xroad.sh and"
    echo "migration-cli.jar to ${ARTIFACTORY_BASE_URL}/${ARTIFACTORY_REPO}/<folder-name>/."
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

# shellcheck source=_publish-installer-common.sh
source "$(dirname "${BASH_SOURCE[0]}")/_publish-installer-common.sh"

FOLDER="$1"
TARGET_BASE="${ARTIFACTORY_BASE_URL}/${ARTIFACTORY_REPO}/${FOLDER}"
BASE_URL="${TARGET_BASE}/"

upload() {
    local src="$1" dest="$2"
    local md5 sha1 sha256
    md5=$(md5sum "$src" | cut -d' ' -f1)
    sha1=$(sha1sum "$src" | cut -d' ' -f1)
    sha256=$(sha256sum "$src" | cut -d' ' -f1)
    curl -fSL --user "${ARTIFACTORY_USER}:${ARTIFACTORY_PASSWORD}" \
        -XPUT -T "$src" \
        -H "X-Checksum-Md5:${md5}" \
        -H "X-Checksum-Sha1:${sha1}" \
        -H "X-Checksum-Sha256:${sha256}" \
        "${TARGET_BASE}/${dest}"
    echo
}

publish_installer "${BASE_URL}" "${TARGET_BASE}/"
