#!/bin/bash
WSDLVALIDATOR_HOME=/usr/share/xroad/wsdlvalidator
exec java -Dee.ria.xroad.internalKeyStorePassword=proxy-ui-api -Dee.ria.xroad.internalKeyStore=/etc/xroad/ssl/proxy-ui-api.p12 -jar "$WSDLVALIDATOR_HOME/jlib/wsdlvalidator-1.0.jar" "$@"
