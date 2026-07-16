#!/usr/bin/env bash
set -euo pipefail

# Helper script to publish X-Road installer (plus migration-cli) to an AWS S3 bucket for testing.
# Requirements: aws cli, tar, JDK + gradlew (for migration-cli build)
#
# All staging (helper-file copies, sed rewrites, tar creation) happens under build/ so the
# tracked xroad-installer/ source tree stays clean — `git status` is empty after a run.

S3_BUCKET="niis-xroad-development"

usage() {
    echo "Usage: $0 <folder-name>"
    echo ""
    echo "Uploads the X-Road installer archive, get-xroad.sh, upgrade-xroad.sh and"
    echo "migration-cli.jar to s3://${S3_BUCKET}/<folder-name>/xroad-installer-<timestamp>/."
    exit 1
}

if [[ $# -lt 1 ]]; then
    usage
fi

# shellcheck source=_installer-common.sh
source "$(dirname "${BASH_SOURCE[0]}")/_installer-common.sh"

TIMESTAMP=$(date '+%Y%m%d-%H%M%S')
S3_FOLDER="$1/xroad-installer-${TIMESTAMP}"
BASE_URL="https://${S3_BUCKET}.s3.amazonaws.com/${S3_FOLDER}/"

upload() {
    aws s3 cp "$1" "s3://${S3_BUCKET}/${S3_FOLDER}/$2"
    # Sibling .sha256 for every uploaded file (Artifactory auto-serves it; S3 does not).
    sha256sum "$1" | awk '{print $1}' | aws s3 cp - "s3://${S3_BUCKET}/${S3_FOLDER}/$2.sha256"
}

publish_installer "${BASE_URL}" "s3://${S3_BUCKET}/${S3_FOLDER}/"
