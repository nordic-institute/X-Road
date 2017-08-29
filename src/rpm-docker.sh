#!/bin/bash
set -e
cd /workspace/src/packages/
./build-xroad-rpm.sh
./build-jetty-rpm.sh

