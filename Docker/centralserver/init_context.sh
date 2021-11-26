#!/bin/bash

mkdir -p build

cp -r ../../ansible/roles/xroad-ca/files/etc ./build/
cp -r ../../ansible/roles/xroad-ca/files/home ./build/
cp -r ./files ./build/
