#/bin/bash

# This file contains configuration parameters for other installation scripts.

# Get current working directory
PWD=$(pwd)

# PyCharm settings
PYCHARM_DOWNLOAD_PATH="https://download.jetbrains.com/python/pycharm-community-2017.1.4.tar.gz"
PYCHARM_ARCHIVE_FILENAME="pycharm.tar.gz"
PYCHARM_TARGET_PATH="/opt/"

# SoapUI settings
SOAPUI_DOWNLOAD_PATH="https://b537910400b7ceac4df0-22e92613740a7dd1247910134033c0d1.ssl.cf5.rackcdn.com/soapui/5.3.0/SoapUI-5.3.0-linux-bin.tar.gz"
SOAPUI_ARCHIVE_FILENAME="SoapUI-x64.tar.gz"
SOAPUI_TARGET_PATH="$HOME"

# X-Road EE tests settings
XROAD_TESTS_GIT_PATH="https://github.com/sluhtoja/X-Road-tests.git"
XROAD_TESTS_TARGET_PATH="$HOME"
XROAD_TESTS_INSTALL_PATH="${XROAD_TESTS_TARGET_PATH}/X-Road-tests/common/xrd-automated-tests/"
XROAD_TESTS_WSDL_PATH="${XROAD_TESTS_TARGET_PATH}/X-Road-tests/common/xrd-automated-tests/mock/service_wsdl/"

XROAD_MOCK_PATH="${XROAD_TESTS_TARGET_PATH}/X-Road-tests/common/xrd-automated-tests/mock/mock_service/"
XROAD_MOCK_RUN_COMMAND="bin/mockservicerunner.sh -s soapui-settings.xml testservice-soapui-project.xml"
