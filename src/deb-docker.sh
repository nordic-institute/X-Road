#!/bin/bash

set -e

export DEB_BUILD_OPTIONS=release

cd /workspace/src/packages/xroad
dpkg-buildpackage -tc -b -us -uc
cd /workspace/src/packages/xroad-jetty9
dpkg-buildpackage -tc -b -us -uc
