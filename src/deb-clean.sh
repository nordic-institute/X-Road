#!/bin/bash

set -e

XROAD=`pwd`

cd $XROAD/packages
rm -vf *.deb
rm -vf *.changes
