#!/bin/bash

. /etc/xroad/services/global.conf


CP="/usr/share/xroad/jlib/central-monitor-agent.jar"

XROAD_VERIFY_IDMAP_PARAMS=" -Xmx50m -XX:MaxMetaspaceSize=70m "

java ${XROAD_PARAMS} ${XROAD_VERIFY_IDMAP_PARAMS} -cp "${CP}"  ee.ria.xroad.centralmonitoragent.validator.ValidatorMain "$@"

