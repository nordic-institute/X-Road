#!/bin/bash
set -e
cd src/packages/
./build-xroad-rpm.sh
./build-jetty-rpm.sh
