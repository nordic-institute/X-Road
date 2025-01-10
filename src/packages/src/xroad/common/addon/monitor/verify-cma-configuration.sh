#!/bin/bash

. /etc/xroad/services/global.conf


CP="/usr/share/xroad/jlib/central-monitor-agent.jar"


XROAD_LOG_LEVEL="INFO"

XROAD_VERIFY_IDMAP_PARAMS=" -Xmx50m -XX:MaxMetaspaceSize=70m \
-Dxroad.appLog.xroad.level=$XROAD_LOG_LEVEL "


java -XX:UseSVE=0 ${XROAD_PARAMS} ${XROAD_VERIFY_IDMAP_PARAMS} -cp "${CP}"  ee.ria.xroad.centralmonitoragent.validator.ValidatorMain "$@"

