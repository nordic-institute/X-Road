#!/bin/bash
# Helper script for downloading Jetty if it is not found locally.

REMOTE_JETTY_SHA1SUM="$(curl -ss http://archive.eclipse.org/jetty/9.3.6.v20151106/dist/jetty-distribution-9.3.6.v20151106.tar.gz.sha1)  $HOME/jetty/jetty.tgz"
LOCAL_JETTY_SHA1SUM=$(sha1sum ~/jetty/jetty.tgz || echo "")
test -d ~/jetty || mkdir ~/jetty
echo "Remote hash: $REMOTE_JETTY_SHA1SUM"
echo "Local hash:  $LOCAL_JETTY_SHA1SUM"
if [[ ${REMOTE_JETTY_SHA1SUM} != ${LOCAL_JETTY_SHA1SUM} ]] ; then
  echo "Downloading Jetty"
  rm -rf ~/jetty/jetty.tgz jetty9
  wget -O ~/jetty/jetty.tgz "http://archive.eclipse.org/jetty/9.3.6.v20151106/dist/jetty-distribution-9.3.6.v20151106.tar.gz"
else
  echo "Found jetty.tgz with the expected checksum"
fi

# vim: ts=2 sw=2 sts=2 et filetype=sh
