#!/bin/bash

set -e

./deb-clean.sh
./deb.sh
./deb-2-vagrant.sh
