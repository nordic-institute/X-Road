#!/bin/bash
#
# Enables batch signing on this Security Server, matching the compose
# ss-batch-signature-enabled overlay's seed row for the multi-container stack.
set -euo pipefail

/usr/share/xroad/scripts/db_property.sh set xroad.proxy.batch-signing-enabled true -y
