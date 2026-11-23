ARG DOCKER_REGISTRY=""
FROM ${DOCKER_REGISTRY}rockylinux/rockylinux:10

ARG XROAD_MIRROR_BASE_URL
ARG XROAD_MIRROR_USER

# Configure the mirror, install and clean up in one RUN: the credential comes from the
# mirror_token secret and is removed before the step ends, so no image layer holds it.
RUN --mount=type=bind,from=mirror-scripts,source=configure-mirror-dnf.sh,target=/tmp/configure-mirror-dnf.sh \
    --mount=type=bind,from=mirror-scripts,source=cleanup-mirror-dnf.sh,target=/tmp/cleanup-mirror-dnf.sh \
    --mount=type=secret,id=mirror_token \
    /bin/bash /tmp/configure-mirror-dnf.sh "$XROAD_MIRROR_BASE_URL" "$XROAD_MIRROR_USER" \
    && dnf -y install sudo git rpm-build systemd-rpm-macros systemd \
    && dnf clean all \
    && sed -i 's/requiretty/!requiretty/' /etc/sudoers \
    && /bin/bash /tmp/cleanup-mirror-dnf.sh

WORKDIR /workspace
