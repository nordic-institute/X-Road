#!/bin/bash

usage="
./dev.sh [-m <string>] [-b] [-d] [-h]

This script builds and/or deploys given module to docker container. By default all tests are disabled.

Options:

-m => Module. proxy | proxy-ui-api
-b => Build
-d => Deploy
-h => Help

Examples:

./dev.sh -bdm proxy => Builds proxy.jar and deploys (moves) it to container and restart processes
"

# Set working directory
cd "$XROAD_HOME"/Docker/xrd-dev-stack/tools

BUILD=false
DEPLOY=false

# If s -option is provided find source packages from s3 bucket with given argument
while getopts ":m:bdh" opt; do
  case $opt in
    b) BUILD=true ;;
    d) DEPLOY=true ;;
    m) MODULE=${OPTARG} ;;
    h)
       printf "$usage"
       exit 0
       ;;
    *)
      printf "$usage"
      exit
      ;;
  esac
done
shift $((OPTIND -1))

if [ "$BUILD" = true ] ; then
  echo "Building module $MODULE"
  source "$XROAD_HOME"/development/.scripts/build.sh $MODULE
fi

if [ "$DEPLOY" = true ] ; then
  echo "Deploying module $MODULE"
  source deploy.sh $MODULE
fi
