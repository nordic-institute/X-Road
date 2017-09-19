#!/bin/sh
. /etc/xroad/services/global.conf
WSDLVALIDATOR_HOME=/usr/share/xroad/wsdlvalidator
cxf_home=$WSDLVALIDATOR_HOME
cxf_classpath="${cxf_home}/jlib/*:${CLASSPATH}"

ST=""
if [ $# -lt 1 ]; then
    ST="-st"
fi

exec $JAVA_HOME/bin/java -D"ee.ria.xroad.internalKeyStorePassword=internal" -D"ee.ria.xroad.internalKeyStore=/etc/xroad/ssl/internal.p12" -cp "${cxf_classpath}" ee.ria.xroad.wsdlvalidator.WSDLValidatorLoader -r $WSDLVALIDATOR_HOME/etc/xroad6.properties $ST "$@"
