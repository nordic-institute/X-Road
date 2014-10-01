
#TODO versioning, append links to packages (now: hardcoded in package)

#SDSB PROXY
mkdir -p packages/xroad-proxy/usr/share/sdsb/jlib/webapps/
cp proxy-ui/build/libs/proxy-ui.war logreader/build/libs/logreader-1.0.war  packages/xroad-proxy/usr/share/sdsb/jlib/webapps/
cp proxy/build/libs/proxy-1.0.jar distributed-files-client/build/libs/distributed-files-client-1.0.jar async-sender/build/libs/async-sender-1.0.jar packages/xroad-proxy/usr/share/sdsb/jlib/

#SDSB COMMON
mkdir -p packages/xroad-common/usr/share/sdsb/jlib/
mkdir -p packages/xroad-common/usr/share/sdsb/lib/
cp signer/build/libs/signer-1.0.jar signer-console/build/libs/signer-console-1.0.jar packages/xroad-common/usr/share/sdsb/jlib/
cp libs/libpkcs11wrapper.so legacy/lib/libpasswordstore.so packages/xroad-common/usr/share/sdsb/lib/


#SDSB CENTER
mkdir -p packages/xroad-center/var/lib/sdsb/public
cp systemtest/src/main/resources/centralservice.wsdl packages/xroad-center/var/lib/sdsb/public

mkdir -p packages/xroad-center/usr/share/sdsb/jlib/webapps/
cp center-service/build/libs/center-service.war center-ui/build/libs/center-ui.war packages/xroad-center/usr/share/sdsb/jlib/webapps/


#MONITOR
mkdir -p packages/xroad-monitor/usr/share/sdsb/jlib/
cp proxy-monitor-agent/build/libs/proxy-monitor-agent-1.0.jar packages/xroad-monitor/usr/share/sdsb/jlib/

#ADDONS
mkdir -p packages/xroad-addon/signer packages/xroad-addon/proxy
cp addons/hwtoken/build/libs/hwtoken-1.0.jar packages/xroad-addon/signer
cp addons/metaservice/build/libs/metaservice-1.0.jar addons/messagelog/build/libs/messagelog-1.0.jar packages/xroad-addon/proxy

