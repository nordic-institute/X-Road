FROM ubuntu:22.04

# Configure Artifactory for APT
ARG ARTIFACTORY_URL
ARG ARTIFACTORY_USER
ARG ARTIFACTORY_CA_CERT

RUN --mount=type=bind,from=artifactory-scripts,source=configure-artifactory-apt.sh,target=/tmp/configure-artifactory-apt.sh \
    --mount=type=secret,id=artifactory_token \
    /bin/bash /tmp/configure-artifactory-apt.sh "$ARTIFACTORY_URL" "$ARTIFACTORY_USER" "$ARTIFACTORY_CA_CERT"

RUN export DEBIAN_FRONTEND=noninteractive; \
    apt-get -qq update && \
    apt-get -qq install \
    software-properties-common git curl wget debhelper devscripts

ENV DEBEMAIL='info@niis.org'
ENV DEBFULLNAME='NIIS'

WORKDIR /workspace
