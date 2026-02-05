FROM rockylinux:8

# Configure package mirror for YUM
ARG XROAD_MIRROR_BASE_URL
ARG XROAD_MIRROR_USER

RUN --mount=type=bind,from=mirror-scripts,source=configure-mirror-yum.sh,target=/tmp/configure-mirror-yum.sh \
    --mount=type=secret,id=mirror_token \
    /bin/bash /tmp/configure-mirror-yum.sh "$XROAD_MIRROR_BASE_URL" "$XROAD_MIRROR_USER"

RUN yum -y install sudo git rpm-build \
    && yum clean all \
    && sed -i 's/requiretty/!requiretty/' /etc/sudoers
WORKDIR /workspace
