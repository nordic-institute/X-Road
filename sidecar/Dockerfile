# syntax=docker/dockerfile:1

# internal or external
ARG PACKAGE_SOURCE=external

ARG VERSION
ARG TAG=niis/xroad-security-server-sidecar
FROM $TAG:$VERSION-slim AS base

# Packages beyond slim's own set, from the remote apt repository already
# configured in the slim image (xroad.list + keyring persist from that build)
FROM base AS xroad-external
RUN apt-get -qq update \
  && XROAD_IGNORE_DATABASE_SETUP=1 apt-get -qq install \
    xroad-monitor \
    xroad-opmonitor \
    xroad-auxiliary-service \
    xroad-message-log-archiver \
    xroad-ds-control-plane \
    xroad-ds-identity-hub \
  && apt-get -qq clean

# Packages beyond slim's own set, from a local directory of tree-built DEBs.
# Slim's own xroad.list (if built with PACKAGE_SOURCE=internal too) points at
# a file: repo that no longer exists once that build finished, so the local
# trusted repo is regenerated here from a fresh packages context.
FROM base AS xroad-internal
RUN --mount=type=bind,from=packages,target=/tmp/packages \
    apt-get -qq install dpkg-dev \
  && cp -r /tmp/packages /tmp/repo \
  && cd /tmp/repo && dpkg-scanpackages -m . >Packages \
  && echo "deb [trusted=yes] file:/tmp/repo /" >/etc/apt/sources.list.d/xroad.list \
  && apt-get -qq update \
  && XROAD_IGNORE_DATABASE_SETUP=1 apt-get -qq install \
    xroad-monitor \
    xroad-opmonitor \
    xroad-auxiliary-service \
    xroad-message-log-archiver \
    xroad-ds-control-plane \
    xroad-ds-identity-hub \
  && apt-get -qq remove dpkg-dev \
  && apt-get -qq autoremove \
  && rm -rf /tmp/repo \
  && apt-get -qq clean

FROM xroad-${PACKAGE_SOURCE} AS final

COPY files/ss-entrypoint.sh /root/entrypoint.sh
COPY --chown=root:root files/ss-xroad.conf /etc/supervisor/conf.d/xroad.conf
CMD ["/root/entrypoint.sh"]
