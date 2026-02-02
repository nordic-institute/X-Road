FROM rockylinux:9

# Configure Artifactory for DNF
ARG ARTIFACTORY_BASE_URL
ARG ARTIFACTORY_USER
ARG ARTIFACTORY_CA_CERT

RUN --mount=type=bind,from=artifactory-scripts,source=configure-artifactory-yum.sh,target=/tmp/configure-artifactory-yum.sh \
    --mount=type=secret,id=artifactory_token \
    /bin/bash /tmp/configure-artifactory-yum.sh "$ARTIFACTORY_BASE_URL" "$ARTIFACTORY_USER" "$ARTIFACTORY_CA_CERT"

RUN dnf -y install sudo git rpm-build systemd-rpm-macros systemd \
    && dnf clean all \
    && sed -i 's/requiretty/!requiretty/' /etc/sudoers
WORKDIR /workspace
