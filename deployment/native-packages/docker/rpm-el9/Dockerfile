FROM rockylinux:9

# Configure package mirror for DNF
ARG XROAD_MIRROR_BASE_URL
ARG XROAD_MIRROR_USER

RUN --mount=type=bind,from=mirror-scripts,source=configure-mirror-yum.sh,target=/tmp/configure-mirror-yum.sh \
    --mount=type=secret,id=mirror_token \
    /bin/bash /tmp/configure-mirror-yum.sh "$XROAD_MIRROR_BASE_URL" "$XROAD_MIRROR_USER"

RUN dnf -y install sudo git rpm-build systemd-rpm-macros systemd \
    && dnf clean all \
    && sed -i 's/requiretty/!requiretty/' /etc/sudoers
WORKDIR /workspace
