#!/usr/bin/env bash
#
# regenerate-verification-metadata.sh
#
# Full refresh of Gradle dependency verification metadata:
#   1. Drop all <component> entries from verification-metadata.xml
#      (keep <configuration> node — trusted-artifacts, verify-* flags).
#   2. Run `./gradlew --write-verification-metadata sha256 <task>` to regenerate
#      entries for every resolved dependency on the host platform.
#   3. Run update-verification-metadata.sh to fill in the OS-classifier gaps
#      for cross-platform native artifacts (protoc, grpc, netty natives).
#
# Usage:
#   regenerate-verification-metadata.sh                 # default: gradle `build`
#   regenerate-verification-metadata.sh dependencies    # resolve only, faster
#   regenerate-verification-metadata.sh dependencies --no-daemon --parallel -q
#
# All args are forwarded to `./gradlew`. Defaults to `build` when no args given.
#
# Exit codes:
#   0 success
#   1 tool missing / gradle failure / update-verification-metadata failure
#
set -eo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=../../scripts/lib/base-script.sh
source "${SCRIPT_DIR}/../../scripts/lib/base-script.sh"
set -u

META="${XROAD_HOME}/src/gradle/verification-metadata.xml"
GRADLE_DIR="${XROAD_HOME}/src"
UPDATER="${SCRIPT_DIR}/update-verification-metadata.sh"

if [[ $# -eq 0 ]]; then
  GRADLE_ARGS=(build)
else
  GRADLE_ARGS=("$@")
fi

die() { log_error "$*"; exit 1; }

command -v python3 >/dev/null || die "python3 not found"
[[ -f "$META" ]]              || die "metadata not found: $META"
[[ -d "$GRADLE_DIR" ]]        || die "gradle dir not found: $GRADLE_DIR"
[[ -x "$UPDATER" ]]           || die "update script not executable: $UPDATER"

log_info "metadata: $META"
log_info "gradle dir: $GRADLE_DIR"

START_TS=$(date +%s)

# Step 1: drop <component> nodes, keep <configuration>.
log_info "Step 1/3: clearing <components> section"
python3 - "$META" <<'PY'
import re, sys
path = sys.argv[1]
with open(path, 'r', encoding='utf-8') as f:
    text = f.read()

# Replace <components>...</components> body with empty, preserving outer tag + indent.
# Look for the opening tag, capture its leading indent, and close on same indent.
m = re.search(r'(\n[ \t]*)<components>.*?</components>', text, re.DOTALL)
if not m:
    sys.stderr.write("ERROR: <components> block not found in metadata\n")
    sys.exit(1)

indent = m.group(1)
replacement = f"{indent}<components>{indent}</components>"
text = text[:m.start()] + replacement + text[m.end():]

with open(path, 'w', encoding='utf-8') as f:
    f.write(text)

print("cleared <components> block")
PY

# Validate XML still well-formed (python3 stdlib).
python3 -c "import xml.etree.ElementTree as E, sys; E.parse(sys.argv[1])" "$META" \
  || die "XML invalid after clearing components"
log_success "<components> cleared"

# Step 2: gradle regeneration.
log_info "Step 2/3: running gradle --write-verification-metadata sha256 ${GRADLE_ARGS[*]}"
pushd "$GRADLE_DIR" >/dev/null
if ! ./gradlew --write-verification-metadata sha256 "${GRADLE_ARGS[@]}"; then
  popd >/dev/null
  die "gradle regeneration failed"
fi
popd >/dev/null
log_success "gradle regeneration complete"

# Step 3: fill in cross-platform classifiers.
log_info "Step 3/3: filling in OS-classifier gaps"
if ! bash "$UPDATER"; then
  die "update-verification-metadata.sh failed"
fi

DURATION=$(( $(date +%s) - START_TS ))
log_success "Regeneration complete in $(format_duration "$DURATION")"
