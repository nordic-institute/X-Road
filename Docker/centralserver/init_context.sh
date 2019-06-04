#!/bin/bash

mkdir -p build

cp -r ../../ansible/roles/xroad-ca/common-files/etc ./build/
cp -r ../../ansible/roles/xroad-ca/common-files/home ./build/
cp -r ./files ./build/
