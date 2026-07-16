#!/bin/bash
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/../scripts/lib/base-script.sh"
resolve_java_home

DIR=$PWD

pushd $1
$DIR/gradlew $2
popd
