#!/bin/sh

. /etc/xroad/services/xtee55-serviceimporter.conf

${JAVA_HOME}/bin/java ${XROAD_PARAMS} ${X55_IMPORTER_PARAMS} -jar /usr/share/xroad/jlib/serviceimporter.jar $*

