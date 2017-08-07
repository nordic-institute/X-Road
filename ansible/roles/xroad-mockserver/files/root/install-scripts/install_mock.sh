#!/bin/bash

# Get configuration
source install_config.sh

# Get SoapUI installation directory
SOAPUI_DIR=`cat .soapui_install_path 2> /dev/null`
if [ $? -ne 0 ]; then
	SOAPUI_DIR=${HOME}/$(tar tzvf "${SOAPUI_ARCHIVE_FILENAME}" | egrep -o "[^ ]+/$" | head -n 1 )
fi

# Create run_mock.sh script from template
sed -e "s~^SOAPUI_HOME\=.*$~SOAPUI_HOME=${SOAPUI_DIR}~" -e "s~^XROAD_MOCK_PATH\=.*$~XROAD_MOCK_PATH=${XROAD_MOCK_PATH}~" template_run_mock.sh > run_mock.sh
chmod +x run_mock.sh

sudo mv run_mock.sh /etc/init.d/mock-service
sudo update-rc.d mock-service defaults
sudo update-rc.d mock-service enable
sudo /etc/init.d/mock-service start
