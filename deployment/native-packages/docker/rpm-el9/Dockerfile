ARG DOCKER_REGISTRY=""
FROM ${DOCKER_REGISTRY}rockylinux:9

# Configure package mirror for DNF
ARG XROAD_MIRROR_BASE_URL
ARG XROAD_MIRROR_USER

# Configure, install and cleanup stay in one RUN: dnf resolves $mirror_token in the repo
# files from the mounted variable file; mirror_netrc serves the GPG key downloads.
RUN --mount=type=bind,from=mirror-scripts,source=configure-mirror-dnf.sh,target=/tmp/configure-mirror-dnf.sh \
    --mount=type=bind,from=mirror-scripts,source=cleanup-mirror-dnf.sh,target=/tmp/cleanup-mirror-dnf.sh \
    --mount=type=secret,id=mirror_netrc \
    --mount=type=secret,id=mirror_token,target=/etc/dnf/vars/mirror_token \
    /bin/bash /tmp/configure-mirror-dnf.sh "$XROAD_MIRROR_BASE_URL" "$XROAD_MIRROR_USER" \
    && dnf -y install sudo git rpm-build systemd-rpm-macros systemd \
    && dnf clean all \
    && sed -i 's/requiretty/!requiretty/' /etc/sudoers \
    && /bin/bash /tmp/cleanup-mirror-dnf.sh

WORKDIR /workspace
