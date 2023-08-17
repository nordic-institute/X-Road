#!/bin/bash

rm -rf ./build
mkdir -p ./build

cp -r ../../../ansible/roles/xroad-ca/files/etc ./build/
cp -r ../../../ansible/roles/xroad-ca/files/home ./build/
cp -r ../../../Docker/centralserver/files ./build/
mkdir -p ./build/usr
