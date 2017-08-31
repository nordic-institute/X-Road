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
