#!/bin/sh
. /etc/xroad/services/global.conf
WSDLVALIDATOR_HOME=/usr/share/xroad/wsdlvalidator
exec $JAVA_HOME/bin/java -D"ee.ria.xroad.internalKeyStorePassword=internal" -D"ee.ria.xroad.internalKeyStore=/etc/xroad/ssl/internal.p12" -jar $WSDLVALIDATOR_HOME/jlib/wsdlvalidator-1.0.jar "$@"
