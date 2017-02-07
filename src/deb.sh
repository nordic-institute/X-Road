#!/bin/bash

set -e

XROAD=`pwd`

cd $XROAD/packages/xroad/
dpkg-buildpackage -tc -b -us -uc
cd $XROAD/packages/xroad-jetty9/ 
dpkg-buildpackage -tc -b -us -uc
