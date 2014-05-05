#!/bin/sh

. /etc/sdsb/services/global.conf

${JAVA_HOME}/bin/java -Dee.cyber.sdsb.signer.port=${SIGNER_PORT} ${SDSB_PARAMS} -jar /usr/share/sdsb/jlib/serviceimporter.jar -checksdsb
