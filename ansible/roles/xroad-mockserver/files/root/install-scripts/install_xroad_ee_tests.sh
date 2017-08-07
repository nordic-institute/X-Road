#!/bin/bash

# Get configuration
source install_config.sh

cd ${XROAD_TESTS_TARGET_PATH}
git clone ${XROAD_TESTS_GIT_PATH}

# Return to working directory
cd $PWD