FROM ubuntu:20.04
RUN export DEBIAN_FRONTEND=noninteractive; \
    apt-get -qq update && \
    apt-get -qq install \
    software-properties-common git curl wget debhelper devscripts
WORKDIR /workspace
