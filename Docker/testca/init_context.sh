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

echo "Copying initialized state..."
cp -r "$XROAD_HOME"/Docker/testca/files/initialized_state/* ./build/home/CA/

mkdir -p ./build/usr
