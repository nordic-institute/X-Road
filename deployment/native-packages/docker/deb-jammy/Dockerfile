FROM ubuntu:22.04

# Configure package mirror for APT
ARG XROAD_MIRROR_URL
ARG XROAD_MIRROR_USER

RUN --mount=type=bind,from=mirror-scripts,source=configure-mirror-apt.sh,target=/tmp/configure-mirror-apt.sh \
    --mount=type=secret,id=mirror_token \
    /bin/bash /tmp/configure-mirror-apt.sh "$XROAD_MIRROR_URL" "$XROAD_MIRROR_USER"

RUN export DEBIAN_FRONTEND=noninteractive; \
    apt-get -qq update && \
    apt-get -qq install \
    software-properties-common git curl wget debhelper devscripts

ENV DEBEMAIL='info@niis.org'
ENV DEBFULLNAME='NIIS'

WORKDIR /workspace
