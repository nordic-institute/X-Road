#!/bin/bash

# Ensure XROAD_HOME is set and not empty
if [ -z "$XROAD_HOME" ]; then
  XROAD_HOME=$(realpath "$(pwd)/../..")
  echo "XROAD_HOME is not set. Setting it to $XROAD_HOME"
fi

rm -rf ./build
mkdir -p ./build

cp -r "$XROAD_HOME"/ansible/roles/xroad-ca/files/etc ./build/
cp -r "$XROAD_HOME"/ansible/roles/xroad-ca/files/home ./build/
cp -r "$XROAD_HOME"/development/acme2certifier ./build/

mkdir -p ./build/usr
