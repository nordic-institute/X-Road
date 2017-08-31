#!/bin/bash

# Get configuration
source install_config.sh

# Get SoapUI installation directory
SOAPUI_DIR=`cat .soapui_install_path 2> /dev/null`
if [ $? -ne 0 ]; then
	SOAPUI_DIR=${HOME}/$(tar tzvf "${SOAPUI_ARCHIVE_FILENAME}" | egrep -o "[^ ]+/$" | head -n 1 )
fi

# Create run_mock.sh script from template
cp run_mock.sh run_mock.backup.sh
sed -e "s~^SOAPUI_HOME\=.*$~SOAPUI_HOME=${SOAPUI_DIR}~" -e "s~^XROAD_MOCK_PATH\=.*$~XROAD_MOCK_PATH=${XROAD_MOCK_PATH}~" run_mock.sh.template > /etc/init.d/mock-service
chmod +x /etc/init.d/mock-service

sudo update-rc.d mock-service defaults
sudo update-rc.d mock-service enable
sudo /etc/init.d/mock-service start
