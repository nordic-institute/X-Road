#!/bin/bash

. /etc/xroad/services/global.conf


CP="/usr/share/xroad/jlib/central-monitor-agent.jar"


XROAD_LOG_LEVEL="INFO"

VERIFY_IDMAP_PARAMS=" -Xmx50m -XX:MaxMetaspaceSize=70m \
-Dxroad.appLog.xroad.level=$XROAD_LOG_LEVEL "


${JAVA_HOME}/bin/java ${XROAD_PARAMS} ${VERIFY_IDMAP_PARAMS} -cp ${CP}  ee.ria.xroad.centralmonitoragent.validator.ValidatorMain $@

