# Base image for auxiliary-service with backup/restore tools
ARG REGISTRY=localhost:5555
ARG IMAGE_TAG=latest
ARG BASE_IMAGE=base-images/ss-baseline-runtime
FROM ${REGISTRY}/${BASE_IMAGE}:${IMAGE_TAG}

ENV DEBIAN_FRONTEND=noninteractive

# Configure package mirror for APT
ARG XROAD_MIRROR_URL
ARG XROAD_MIRROR_USER

RUN --mount=type=bind,from=mirror-scripts,source=configure-mirror-apt.sh,target=/tmp/configure-mirror-apt.sh \
    --mount=type=bind,from=mirror-scripts,source=configure-mirror-openbao-deb.sh,target=/tmp/configure-mirror-openbao-deb.sh \
    --mount=type=secret,id=mirror_token \
    /bin/bash /tmp/configure-mirror-apt.sh "$XROAD_MIRROR_URL" "$XROAD_MIRROR_USER" && \
    /bin/bash /tmp/configure-mirror-openbao-deb.sh

# Install backup/restore dependencies
RUN apt-get -qq update && \
    apt-get -qq install --no-install-recommends curl postgresql-client && \
    apt-get -qq clean && \
    rm -rf /var/lib/apt/lists/*

# Install kubectl for Kubernetes backup operations
ARG KUBECTL_DIST_URL=https://dl.k8s.io
ARG KUBECTL_VERSION=v1.35.0
RUN --mount=type=secret,id=mirror_token \
    ARCH=$(dpkg --print-architecture) && \
    CURL_AUTH="" && \
    if [ -n "$XROAD_MIRROR_USER" ] && [ -f /run/secrets/mirror_token ]; then \
      CURL_AUTH="-u ${XROAD_MIRROR_USER}:$(cat /run/secrets/mirror_token)"; \
    fi && \
    curl -fsSLo /tmp/kubectl $CURL_AUTH ${KUBECTL_DIST_URL}/release/${KUBECTL_VERSION}/bin/linux/${ARCH}/kubectl && \
    curl -fsSLo /tmp/kubectl.sha256 $CURL_AUTH ${KUBECTL_DIST_URL}/release/${KUBECTL_VERSION}/bin/linux/${ARCH}/kubectl.sha256 && \
    echo "$(cat /tmp/kubectl.sha256)  /tmp/kubectl" | sha256sum -c - && \
    install -m 0755 /tmp/kubectl /usr/local/bin/kubectl && \
    rm /tmp/kubectl /tmp/kubectl.sha256

# Create backup directory
RUN mkdir -p /var/lib/xroad/backup && \
    chown -R xroad:xroad /var/lib/xroad

# Copy backup/restore scripts and configs
# files/ directory mirrors the container filesystem structure
COPY --chown=xroad:xroad files/ /

RUN chmod 755 /usr/share/xroad/scripts/generate_gpg_keypair.sh

# Cleanup mirror configuration (restore public mirrors for published image)
RUN --mount=type=bind,from=mirror-scripts,source=cleanup-mirror-apt.sh,target=/tmp/cleanup-mirror-apt.sh \
    --mount=type=bind,from=mirror-scripts,source=cleanup-mirror-openbao-deb.sh,target=/tmp/cleanup-mirror-openbao-deb.sh \
    /bin/bash /tmp/cleanup-mirror-apt.sh && \
    /bin/bash /tmp/cleanup-mirror-openbao-deb.sh
