#!/bin/bash

# Get configuration
source install_config.sh

# Download SoapUI archive
wget -O "${SOAPUI_ARCHIVE_FILENAME}" "${SOAPUI_DOWNLOAD_PATH}"
if [[ $? -eq 0 ]]; then
	SOAPUI_DIR=$(tar tzvf "${SOAPUI_ARCHIVE_FILENAME}" | egrep -o "[^ ]+/$" | head -n 1 )
	tar xzf "${SOAPUI_ARCHIVE_FILENAME}" -C "${SOAPUI_TARGET_PATH}"
	if [[ $? -eq 0 ]]; then
		SOAPUI_LOCAL_PATH="${SOAPUI_TARGET_PATH}/${SOAPUI_DIR}"
		echo ${SOAPUI_LOCAL_PATH} > .soapui_install_path
		echo "SoapUI extracted to ${SOAPUI_LOCAL_PATH}"
		echo "SoapUI start: ${SOAPUI_LOCAL_PATH}bin/soapui.sh"
		echo "SoapUI mockrunner: ${SOAPUI_LOCAL_PATH}bin/mockservicerunner.sh"
		echo "[Desktop Entry]
Type=Application
Terminal=false
Exec=${SOAPUI_LOCAL_PATH}bin/soapui.sh
Name=SoapUI" > SoapUI.desktop
	else
		echo "SoapUI extraction failed from archive ${SOAPUI_ARCHIVE_FILENAME}"
		exit 1
	fi
else
	echo "Failed to download SoapUI from ${SOAPUI_DOWNLOAD_PATH}"
	exit 1
fi

