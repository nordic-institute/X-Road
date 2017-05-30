#!/bin/bash

set -e

cd /workspace/xtee6/packages/xroad
dpkg-buildpackage -tc -b -us -uc
cd /workspace/xtee6/packages/xroad-jetty9
dpkg-buildpackage -tc -b -us -uc
