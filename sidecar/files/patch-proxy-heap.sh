#!/bin/bash
# Build-time patch: repoint the packaged proxy heap sizing from a fixed -Xmx to a
# container-relative percentage. Every patch target is grep-guarded so the build
# fails loudly if the packaged files drift, instead of silently no-opping.
set -euo pipefail

HELPER=/usr/share/xroad/scripts/proxy_memory_helper.sh
LOCAL_PROPERTIES=/etc/xroad/services/local.properties

grep -qF 'echo "-Xms$default_xms -Xmx$default_xmx"' "$HELPER" \
  || { echo "ERROR: proxy_memory_helper.sh get-default line not found, refusing to patch proxy heap sizing" >&2; exit 1; }
sed -i 's/echo "-Xms$default_xms -Xmx$default_xmx"/echo "-Xms$default_xms -XX:MaxRAMPercentage=25.0"/' "$HELPER"

# xroad-proxy.postinst bakes a literal -Xmx into local.properties at every
# "configure" run - both the image's own package install and the entrypoint's
# dpkg-reconfigure on first boot - via the helper's apply-default action, which
# still writes $default_xmx verbatim regardless of the get-default patch above;
# strip it there too, or a fresh container's actual launch reverts to a
# hardcoded heap on every first boot.
grep -qF '  apply_memory_config "XROAD_PROXY_PARAMS" "$default_xms" "$default_xmx"' "$HELPER" \
  || { echo "ERROR: proxy_memory_helper.sh apply-default line not found, refusing to patch proxy heap sizing" >&2; exit 1; }
sed -i 's#  apply_memory_config "XROAD_PROXY_PARAMS" "$default_xms" "$default_xmx"#&\n  sed -E -i "/^XROAD_PROXY_PARAMS=/ s/ ?-Xmx[0-9]+[kKmMgG]?//g" /etc/xroad/services/local.properties#' "$HELPER"

# The same postinst configure run already baked the literal into
# local.properties during package install, before the apply-default patch above
# existed; strip it so both the live file and the config-backup seed stay clean.
if [ -f "$LOCAL_PROPERTIES" ]; then
  sed -E -i '/^XROAD_PROXY_PARAMS=/ s/ ?-Xmx[0-9]+[kKmMgG]?//g' "$LOCAL_PROPERTIES"
fi
! grep -qE -- '-Xmx[0-9]+[kKmMgG]?' "$LOCAL_PROPERTIES" 2>/dev/null \
  || { echo "ERROR: literal -Xmx still present in $LOCAL_PROPERTIES, refusing to build" >&2; exit 1; }
