#!/bin/bash
WSDLVALIDATOR_HOME=/usr/share/xroad/wsdlvalidator
exec java -Dee.ria.xroad.internalKeyStorePassword=internal -Dee.ria.xroad.internalKeyStore=/etc/xroad/ssl/internal.p12 -jar "$WSDLVALIDATOR_HOME/jlib/wsdlvalidator-1.0.jar" "$@"
