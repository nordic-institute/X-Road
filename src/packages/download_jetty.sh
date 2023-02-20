#!/bin/bash
# Helper script for downloading Jetty if it is not found locally.
WDIR=$(cd "$(dirname "$0")"; pwd)
cd $WDIR
jetty_url=$(cat src/xroad-jetty9/jetty.url)

REMOTE_JETTY_SHA1SUM="$(curl -ss ${jetty_url}.sha1)"
LOCAL_JETTY_SHA1SUM=$(sha1sum jetty.tgz  | cut -d' ' -f1|| echo "")

if [[ ${REMOTE_JETTY_SHA1SUM} != ${LOCAL_JETTY_SHA1SUM} ]] ; then
  echo "Downloading Jetty"
  wget -O $WDIR/jetty.tgz "$jetty_url"
fi

if [[ ! -d "build/jetty9-$LOCAL_JETTY_SHA1SUM" ]]; then
    cd build
    tar xf "../jetty.tgz" --wildcards "jetty-distribution*"
    mv jetty-distribution* jetty9-$REMOTE_JETTY_SHA1SUM
    ln -sfn jetty9-$REMOTE_JETTY_SHA1SUM jetty9 
    rm -rf jetty9/lib/setuid
    rm -rf jetty9/demo-base
    yes | java -Dslf4j.version=1.7.32 -Dlogback.version=1.2.11 -jar jetty9/start.jar --add-to-start=logback-impl,slf4j-logback jetty.base=jetty9
    rm jetty9/start.ini
    rm jetty9/resources/logback.xml
fi
