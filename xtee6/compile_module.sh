#!/bin/bash
source compile_env.sh

DIR=$PWD

pushd $1 
$DIR/gradlew $2
popd
