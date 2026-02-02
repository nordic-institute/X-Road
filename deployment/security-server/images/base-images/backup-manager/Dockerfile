# Base image for backup-manager with backup/restore tools
ARG REGISTRY=localhost:5555
ARG IMAGE_TAG=latest
ARG BASE_IMAGE=base-images/ss-baseline-runtime
FROM ${REGISTRY}/${BASE_IMAGE}:${IMAGE_TAG}

ENV DEBIAN_FRONTEND=noninteractive

# Configure Artifactory for APT
ARG ARTIFACTORY_URL
ARG ARTIFACTORY_USER
ARG ARTIFACTORY_CA_CERT

RUN --mount=type=bind,from=artifactory-scripts,source=configure-artifactory-apt.sh,target=/tmp/configure-artifactory-apt.sh \
    --mount=type=secret,id=artifactory_token \
    /bin/bash /tmp/configure-artifactory-apt.sh "$ARTIFACTORY_URL" "$ARTIFACTORY_USER" "$ARTIFACTORY_CA_CERT"

# Install backup/restore dependencies
RUN apt-get -qq update && \
    apt-get -qq install --no-install-recommends curl postgresql-client && \
    apt-get -qq clean && \
    rm -rf /var/lib/apt/lists/*

# Install kubectl for Kubernetes backup operations
RUN ARCH=$(dpkg --print-architecture) && \
    KUBECTL_VERSION=$(curl -L -s https://dl.k8s.io/release/stable.txt) && \
    curl -sSLo /tmp/kubectl https://dl.k8s.io/release/${KUBECTL_VERSION}/bin/linux/${ARCH}/kubectl && \
    curl -sSLo /tmp/kubectl.sha256 https://dl.k8s.io/release/${KUBECTL_VERSION}/bin/linux/${ARCH}/kubectl.sha256 && \
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

# Cleanup Artifactory configuration (restore public mirrors for published image)
RUN --mount=type=bind,from=artifactory-scripts,source=cleanup-artifactory-apt.sh,target=/tmp/cleanup-artifactory-apt.sh \
    /bin/bash /tmp/cleanup-artifactory-apt.sh

