#!/bin/bash
. /etc/xroad/services/local.conf
WSDLVALIDATOR_HOME=/usr/share/xroad/wsdlvalidator
if [ -n "$JAVA_HOME" ]; then
  java="$JAVA_HOME/bin/java"
else
  java="java"
fi
exec $java -D"ee.ria.xroad.internalKeyStorePassword=internal" -D"ee.ria.xroad.internalKeyStore=/etc/xroad/ssl/internal.p12" -jar $WSDLVALIDATOR_HOME/jlib/wsdlvalidator-1.0.jar "$@"
