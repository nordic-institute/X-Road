#!/bin/bash

# Ensure XROAD_HOME is set and not empty
if [ -z "$XROAD_HOME" ]; then
  echo "XROAD_HOME is not set. Exiting."
  exit 1
fi

rm -rf ./build
mkdir -p ./build

cp -r "$XROAD_HOME"/ansible/roles/xroad-ca/files/etc ./build/
cp -r "$XROAD_HOME"/ansible/roles/xroad-ca/files/home ./build/

mkdir -p ./build/usr
