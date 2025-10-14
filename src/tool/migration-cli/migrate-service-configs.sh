#!/bin/sh

#./migrate-ini-cfg.sh \
#/Users/ricardasb/work/xroad/repo/x-road/src/packages/src/xroad/default-configuration/proxy.ini \
#/Users/ricardasb/work/xroad/repo/x-road/src/proxy/application/src/main/resources/proxy.yaml
#
#./migrate-ini-cfg.sh \
#/Users/ricardasb/work/xroad/repo/x-road/src/packages/src/xroad/default-configuration/signer.ini \
#/Users/ricardasb/work/xroad/repo/x-road/src/signer/src/main/resources/signer.yaml

./migrate-ini-cfg.sh \
$XROAD_HOME/deployment/native-packages/src/xroad/default-configuration/confproxy.ini \
$XROAD_HOME/src/service/configuration-proxy/configuration-proxy-application/src/main/resources/confproxy.yaml
