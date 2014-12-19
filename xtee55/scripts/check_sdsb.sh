#!/bin/sh

. /etc/sdsb/services/xtee55-serviceimporter.conf

${JAVA_HOME}/bin/java ${SDSB_PARAMS} ${X55_IMPORTER_PARAMS} -jar /usr/share/sdsb/jlib/serviceimporter.jar $*

