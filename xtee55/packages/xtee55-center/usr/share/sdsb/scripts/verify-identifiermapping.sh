#!/bin/bash

. /etc/sdsb/services/global.conf


CP="/usr/share/sdsb/jlib/validator-identifiermapping.jar"


SDSB_LOG_LEVEL="INFO"

VERIFY_IDMAP_PARAMS=" -Xmx50m -XX:MaxMetaspaceSize=70m \
-Dee.cyber.sdsb.appLog.sdsb.level=$SDSB_LOG_LEVEL \
-Dee.cyber.xroad.validator.identifiermapping.db.host=127.0.0.1 \
-Dee.cyber.xroad.validator.identifiermapping.db.database=centerui_production \
-Dee.cyber.xroad.validator.identifiermapping.db.username=centerui \
-Dee.cyber.xroad.validator.identifiermapping.db.password=centerui "


${JAVA_HOME}/bin/java ${SDSB_PARAMS} ${VERIFY_IDMAP_PARAMS} -cp ${CP}  ee.cyber.xroad.validator.identifiermapping.Main $@
 
