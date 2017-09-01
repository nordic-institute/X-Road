#/bin/bash

# This file contains configuration parameters for other installation scripts.

# Get current working directory
PWD=$(pwd)

# PyCharm settings
PYCHARM_DOWNLOAD_PATH="{{ pycharm_download_path }}"
PYCHARM_ARCHIVE_FILENAME="{{ pycharm_archive_filename }}"
PYCHARM_TARGET_PATH="/opt/"

# SoapUI settings
SOAPUI_DOWNLOAD_PATH="{{ soapui_download_path }}"
SOAPUI_ARCHIVE_FILENAME="{{ soapui_archive_filename }}"
SOAPUI_TARGET_PATH="$HOME"

# X-Road EE tests settings
XROAD_TESTS_GIT_PATH="{{ xroad_python_tests_git_path }}"
XROAD_TESTS_TARGET_PATH="$HOME"
XROAD_TESTS_INSTALL_PATH="${XROAD_TESTS_TARGET_PATH}/{{ xroad_python_tests_path }}"
XROAD_TESTS_WSDL_PATH="${XROAD_TESTS_TARGET_PATH}/{{ xroad_mock_wsdl_path }}"

XROAD_MOCK_PATH="${XROAD_TESTS_TARGET_PATH}/{{ xroad_mock_service_path }}"
XROAD_MOCK_RUN_COMMAND="{{ xroad_mock_run_command }}"
