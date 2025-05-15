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

BUILD=false
DEPLOY=false
INVENTORY_PATH="config/ansible_hosts.txt"

# If s -option is provided find source packages from s3 bucket with given argument
while getopts ":m:bdhi:" opt; do
  case $opt in
    b) BUILD=true ;;
    d) DEPLOY=true ;;
    m) MODULE=${OPTARG} ;;
    i)
        INVENTORY_PATH=${OPTARG}
        if [ ! -f "$INVENTORY_PATH" ]; then
          log_error "Inventory file not found: $INVENTORY_PATH"
          exit 1
        fi
        ;;
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
  source ../.scripts/build.sh $MODULE
fi

if [ "$DEPLOY" = true ] ; then
  echo "Deploying module $MODULE using inventory $INVENTORY_PATH"
  source scripts/deploy-module.sh $(realpath $INVENTORY_PATH) $MODULE
fi
