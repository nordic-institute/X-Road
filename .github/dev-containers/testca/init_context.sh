#!/bin/bash

rm -rf ./build
mkdir -p ./build

cp -r ../../../ansible/roles/xroad-ca/files/etc ./build/
cp -r ../../../ansible/roles/xroad-ca/files/home ./build/

mkdir -p ./build/usr
