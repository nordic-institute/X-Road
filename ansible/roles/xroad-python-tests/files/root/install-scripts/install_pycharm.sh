#!/bin/bash

# Get configuration
source install_config.sh

# Download PyCharm archive
wget -O "${PYCHARM_ARCHIVE_FILENAME}" "${PYCHARM_DOWNLOAD_PATH}"
if [[ $? -eq 0 ]]; then
	sudo tar xzf "${PYCHARM_ARCHIVE_FILENAME}" -C "${PYCHARM_TARGET_PATH}"
	if [[ $? -eq 0 ]]; then
		PYCHARM_INSTALL_DIR=$(ls -d ${PYCHARM_TARGET_PATH}pycharm*)
		PYCHARM_LOCAL_PATH="${PYCHARM_INSTALL_DIR}"
		echo "PyCharm extracted to ${PYCHARM_LOCAL_PATH}"
		echo "[Desktop Entry]
Type=Application
Terminal=false
Exec=${PYCHARM_LOCAL_PATH}/bin/pycharm.sh
Name=PyCharm
Icon=${PYCHARM_LOCAL_PATH}/bin/pycharm.png" > PyCharm.desktop
	else
		echo "PyCharm extraction failed from archive ${PYCHARM_ARCHIVE_FILENAME}"
		exit 1
	fi
else
	echo "Failed to download PyCharm from ${PYCHARM_DOWNLOAD_PATH}"
	exit 1
fi

