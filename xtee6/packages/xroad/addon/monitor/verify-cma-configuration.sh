#!/bin/bash

. /etc/sdsb/services/global.conf


CP="/usr/share/sdsb/jlib/central-monitor-agent.jar"


SDSB_LOG_LEVEL="INFO"

VERIFY_IDMAP_PARAMS=" -Xmx50m -XX:MaxMetaspaceSize=70m \
-Dee.cyber.sdsb.appLog.sdsb.level=$SDSB_LOG_LEVEL "


${JAVA_HOME}/bin/java ${SDSB_PARAMS} ${VERIFY_IDMAP_PARAMS} -cp ${CP}  ee.cyber.sdsb.centralmonitoragent.validator.ValidatorMain $@

