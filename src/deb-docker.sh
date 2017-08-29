#!/bin/bash

set -e

if [[ $1 == "-release" ]] ; then
  export DEB_BUILD_OPTIONS=release
fi

cd /workspace/src/packages/xroad
dpkg-buildpackage -tc -b -us -uc
cd /workspace/src/packages/xroad-jetty9
dpkg-buildpackage -tc -b -us -uc
