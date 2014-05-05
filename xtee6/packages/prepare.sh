
#TODO versioning, append links to packages (now: hardcoded in package)

#SDSB PROXY
mkdir -p packages/sdsb-proxy/usr/share/sdsb/jlib/webapps/
cp proxy-ui/build/libs/proxy-ui.war logreader/build/libs/logreader-1.0.war  packages/sdsb-proxy/usr/share/sdsb/jlib/webapps/
cp proxy/build/libs/proxy-1.0.jar distributed-files-client/build/libs/distributed-files-client-1.0.jar async-sender/build/libs/async-sender-1.0.jar packages/sdsb-proxy/usr/share/sdsb/jlib/

#SDSB COMMON
mkdir -p packages/sdsb-common/usr/share/sdsb/jlib/
mkdir -p packages/sdsb-common/usr/share/sdsb/lib/
cp signer/build/libs/signer-1.0.jar signer-console/build/libs/signer-console-1.0.jar packages/sdsb-common/usr/share/sdsb/jlib/
cp libs/libpkcs11wrapper.so legacy/lib/libpasswordstore.so packages/sdsb-common/usr/share/sdsb/lib/


#SDSB CENTER
mkdir -p packages/sdsb-center/var/lib/sdsb/public
cp systemtest/src/main/resources/centralservice.wsdl packages/sdsb-center/var/lib/sdsb/public

mkdir -p packages/sdsb-center/usr/share/sdsb/jlib/webapps/
cp center-service/build/libs/center-service.war center-ui/build/libs/center-ui.war packages/sdsb-center/usr/share/sdsb/jlib/webapps/

