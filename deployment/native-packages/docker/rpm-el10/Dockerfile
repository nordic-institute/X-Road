ARG DOCKER_REGISTRY=""
FROM ${DOCKER_REGISTRY}rockylinux/rockylinux:10

# Configure package mirror for DNF
ARG XROAD_MIRROR_BASE_URL
ARG XROAD_MIRROR_USER

RUN --mount=type=bind,from=mirror-scripts,source=configure-mirror-dnf.sh,target=/tmp/configure-mirror-dnf.sh \
    --mount=type=secret,id=mirror_token \
    /bin/bash /tmp/configure-mirror-dnf.sh "$XROAD_MIRROR_BASE_URL" "$XROAD_MIRROR_USER"

RUN dnf -y install sudo git rpm-build systemd-rpm-macros systemd \
    && dnf clean all \
    && sed -i 's/requiretty/!requiretty/' /etc/sudoers

# Cleanup mirror configuration
RUN --mount=type=bind,from=mirror-scripts,source=cleanup-mirror-dnf.sh,target=/tmp/cleanup-mirror-dnf.sh \
    /bin/bash /tmp/cleanup-mirror-dnf.sh

WORKDIR /workspace
