FROM ubuntu:bionic
RUN export DEBIAN_FRONTEND=noninteractive; \
    apt-get -qq update && \
    apt-get -qq install \
    software-properties-common git curl wget debhelper devscripts openjdk-8-jre-headless
WORKDIR /workspace
