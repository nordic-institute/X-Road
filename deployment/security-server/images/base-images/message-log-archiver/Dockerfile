# Base image for message-log-archiver
ARG REGISTRY=localhost:5555
ARG IMAGE_TAG=latest
ARG BASE_IMAGE=base-images/ss-baseline-runtime
FROM ${REGISTRY}/${BASE_IMAGE}:${IMAGE_TAG}

ENV DEBIAN_FRONTEND=noninteractive

# Configure package mirror for APT
ARG XROAD_MIRROR_URL
ARG XROAD_MIRROR_USER

RUN --mount=type=bind,from=mirror-scripts,source=configure-mirror-apt.sh,target=/tmp/configure-mirror-apt.sh \
    --mount=type=secret,id=mirror_token \
    /bin/bash /tmp/configure-mirror-apt.sh "$XROAD_MIRROR_URL" "$XROAD_MIRROR_USER"

# Install dependencies
RUN apt-get -qq update && \
    apt-get -qq install --no-install-recommends gnupg && \
    apt-get -qq clean && \
    rm -rf /var/lib/apt/lists/*

# Cleanup mirror configuration (restore public mirrors for published image)
RUN --mount=type=bind,from=mirror-scripts,source=cleanup-mirror-apt.sh,target=/tmp/cleanup-mirror-apt.sh \
    /bin/bash /tmp/cleanup-mirror-apt.sh
