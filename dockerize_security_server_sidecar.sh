#!/bin/bash

if [ -z "$XROAD_HOME" ] ; then
  echo "\n
  You must set the following environment variable to use this installation script:

  VARIABLE NAME                   DESCRIPTION                         EXAMPLE VALUE
  1: XROAD_HOME                   The root folder for x-road          ~/code/niis/xroad
  "

  exit 1;
fi

# Create xroad-network to provide container-to-container communication
docker network create -d bridge xroad-network

# Create build image and container to compile xroad packages
docker build -t xroad-sidecar-builder-image -f buildcontainer/Dockerfile buildcontainer/
docker run -v "$XROAD_HOME":/home/builder/xroad:delegated -u builder --name xroad-sidecar-build-container xroad-sidecar-builder-image build_and_deploy_single_module.sh proxy

# Create new image from xroad-sidecar-build-container and compile xroad packages
docker commit xroad-sidecar-build-container sidecar-builder
docker run --detach -t -v "$XROAD_HOME":/home/builder/xroad:delegated -u builder --name builder sidecar-builder bash

# Setup sidecar security server container context
cp -r "$XROAD_HOME/src/packages/build/ubuntu18.04" "sidecar/"

echo "
===> Created image 'sidecar-builder' and started container builder from it.

Now you can setup a sidecar security server by running setup_security_server_sidecar script
"
