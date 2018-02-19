#!/bin/bash

set -e

if [[ $1 == "-release" ]] ; then
  export DEB_BUILD_OPTIONS=release
fi

cd src/packages/xroad
dpkg-buildpackage -tc -b -us -uc
cd ../xroad-jetty9
dpkg-buildpackage -tc -b -us -uc
