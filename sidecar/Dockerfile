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
# The slim image built the same way keeps no apt source of its own (its file:
# repo is gone once that build finished), so the local trusted repo is
# regenerated here from a fresh packages context.
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
  # the file: repo does not outlive this build stage, so its source list must go
  # with it - a dangling trusted=yes source would fail every later apt-get update
  && rm -rf /tmp/repo /etc/apt/sources.list.d/xroad.list \
  && apt-get -qq clean

FROM xroad-${PACKAGE_SOURCE} AS final

# Refresh the packaged configuration backup the slim stage took: the packages
# installed above ship their own /etc/xroad content (service .conf files,
# backup.d hooks), which an empty mounted /etc/xroad volume is initialized from
# and a version migration copies over. Same shape as the slim capture: the
# files that must survive a migration are held in config/backup instead, the
# generated and host-specific ones are left out entirely.
RUN { test -f /etc/xroad/conf.d/local.ini || touch /etc/xroad/conf.d/local.ini; } \
  && { test -f /etc/xroad/services/local.properties || touch /etc/xroad/services/local.properties; } \
  && rm -rf /usr/share/xroad/config/etc/xroad \
  && mkdir -p /usr/share/xroad/config/etc/xroad /usr/share/xroad/config/backup \
  && cp -a /etc/xroad /usr/share/xroad/config/etc/ \
  && rm -rf /usr/share/xroad/config/etc/xroad/signer/softtoken \
  && rm -f \
    /usr/share/xroad/config/etc/xroad/VERSION \
    /usr/share/xroad/config/etc/xroad/db.properties \
    /usr/share/xroad/config/etc/xroad/xroad.properties \
    /usr/share/xroad/config/etc/xroad/ssl/*.crt \
    /usr/share/xroad/config/etc/xroad/ssl/*.key \
    /usr/share/xroad/config/etc/xroad/ssl/*.p12 \
  && mv -f /usr/share/xroad/config/etc/xroad/conf.d/local.ini \
    /usr/share/xroad/config/etc/xroad/services/local.properties \
    /usr/share/xroad/config/backup/

COPY files/ss-entrypoint.sh /root/entrypoint.sh
COPY --chown=root:root files/ss-xroad.conf /etc/supervisor/conf.d/xroad.conf
CMD ["/root/entrypoint.sh"]
