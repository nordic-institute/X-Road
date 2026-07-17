#!/bin/bash
# The MIT License
#
# Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
# Copyright (c) 2018 Estonian Information System Authority (RIA),
# Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
# Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
#
# Permission is hereby granted, free of charge, to any person obtaining a copy
# of this software and associated documentation files (the "Software"), to deal
# in the Software without restriction, including without limitation the rights
# to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
# copies of the Software, and to permit persons to whom the Software is
# furnished to do so, subject to the following conditions:
#
# The above copyright notice and this permission notice shall be included in
# all copies or substantial portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
# THE SOFTWARE.

# Reads the tar label of the given backup archive and, if its embedded backup-format version
# can be parsed, writes a "<backup-file>.metadata" file recording that version -- regardless
# of whether it matches the version of the current system.
# For GPG-encrypted or GPG-signed backups the file is decrypted first in order to read its tar label.
# Usage: create_backup_metadata.sh <backup-file>
# Exit 0 on success, whether or not the backup turns out to be compatible.
# Exit non-zero only on usage/file errors.

source /usr/share/xroad/scripts/containerised/backup_common.sh

set -e

log() { echo >&2 "$@"; }

# The tar label has the form "<server_type>_<version>_<identity>" (see make_tarball_label()).
# Backup format versions always look like "vN_<flavor>" (e.g. v1_containerized), so once the known
# server-type prefix is stripped, the version is unambiguous even if identity also has underscores.
# Sets BACKUP_LABEL_SERVER_TYPE and BACKUP_LABEL_VERSION; both left empty if unparseable.
parse_backup_label() {
  local label="$1"
  BACKUP_LABEL_SERVER_TYPE=""
  BACKUP_LABEL_VERSION=""
  case "$label" in
    security_*)
      BACKUP_LABEL_SERVER_TYPE="security"
      ;;
    central_*)
      BACKUP_LABEL_SERVER_TYPE="central"
      ;;
    *)
      return
      ;;
  esac
  local remainder="${label#"${BACKUP_LABEL_SERVER_TYPE}"_}"
  if [[ "$remainder" =~ ^(v[0-9]+_[a-z0-9]+)_ ]]; then
    BACKUP_LABEL_VERSION="${BASH_REMATCH[1]}"
  fi
}

BACKUP_FILE="$1"

if [ -z "$BACKUP_FILE" ]; then
  echo "Usage: $0 <backup-file>" >&2
  exit 1
fi

if [ ! -f "$BACKUP_FILE" ]; then
  echo "Backup file not found: $BACKUP_FILE" >&2
  exit 1
fi

log "Reading tar label for backup file: $BACKUP_FILE"

# Clear any stale metadata up front so an inconclusive read below (missing/unparseable
# label) can't leave behind a stale verdict from a previous run against this filename.
rm -f "${BACKUP_FILE}.metadata"

TEMP_GPG_DIR=$(mktemp -d /var/tmp/xroad/gpgtmp.XXXXXX)
TEMP_TAR_FILE=${TEMP_GPG_DIR}/decrypted_temporary.tar
trap 'rm -rf "$TEMP_GPG_DIR"' EXIT

# gpg --decrypt can also handle files that are only signed. Signature validity does not
# matter here -- we only need to peek at the tar label, so unlike restore_backup.sh this
# does not verify the signature.
if gpg --batch --no-tty --homedir /etc/xroad/gpghome --decrypt --output "$TEMP_TAR_FILE" "$BACKUP_FILE" 2>/dev/null; then
  log "Backup is GPG-encrypted/signed, decrypted for label inspection"
  TAR_FILE="$TEMP_TAR_FILE"
else
  log "Backup is not GPG-encrypted, reading tar label directly"
  TAR_FILE="$BACKUP_FILE"
fi

TAR_LABEL=$(tar --test-label --file "$TAR_FILE" 2>/dev/null) || true

if [ -z "$TAR_LABEL" ]; then
  log "No tar label found in backup archive"
  exit 0
fi

log "Parsed tar label: $TAR_LABEL"

parse_backup_label "$TAR_LABEL"

if [ -z "$BACKUP_LABEL_VERSION" ]; then
  log "Could not parse a backup format version from tar label: $TAR_LABEL"
  exit 0
fi

log "Parsed backup format version: $BACKUP_LABEL_VERSION (server type: $BACKUP_LABEL_SERVER_TYPE)"
write_backup_metadata_json "${BACKUP_FILE}.metadata" "${BACKUP_LABEL_VERSION}" "${BACKUP_LABEL_SERVER_TYPE}"

