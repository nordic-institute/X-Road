#!/bin/bash

# This version number must be increased when we introduce changes that make
# earlier backup files incompatible with the current system.
BACKUP_FORMAT_VERSION_LABEL="v1"

make_tarball_label () {
  TARBALL_LABEL="security_${BACKUP_FORMAT_VERSION_LABEL}_${SECURITY_SERVER_ID}"
}
