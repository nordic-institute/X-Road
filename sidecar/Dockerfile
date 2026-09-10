# syntax=docker/dockerfile:1

# internal or external
ARG PACKAGE_SOURCE=external

ARG DOCKER_REGISTRY=""
FROM ${DOCKER_REGISTRY}ubuntu:26.04 AS base
ENV DEBIAN_FRONTEND=noninteractive

# Configure package mirror for APT
ARG XROAD_MIRROR_URL
ARG XROAD_MIRROR_USER

RUN --mount=type=bind,from=mirror-scripts,source=configure-mirror-apt.sh,target=/tmp/configure-mirror-apt.sh \
    --mount=type=secret,id=mirror_token \
    /bin/bash /tmp/configure-mirror-apt.sh "$XROAD_MIRROR_URL" "$XROAD_MIRROR_USER"

RUN apt-get -qq update \
  && apt-get -qq upgrade \
  && apt-get -qq install \
    sudo ca-certificates gnupg supervisor net-tools iproute2 locales \
    openjdk-25-jre-headless rlwrap ca-certificates-java \
    crudini adduser expect curl wget openssl nano-tiny bc jq \
  && echo "LC_ALL=en_US.UTF-8" >>/etc/environment \
  && locale-gen en_US.UTF-8 \
  && adduser --quiet --system --uid 998 --home /var/lib/postgresql \
    --no-create-home --shell /bin/bash --group postgres \
  && adduser --quiet --system --uid 999 --home /var/lib/xroad \
    --no-create-home --shell /bin/bash --group xroad \
  && adduser xroad sudo \
  && apt-get -qq install postgresql-18 \
  && apt-get -qq clean \
  # Do not exclude man pages & other documentation from the X-Road packages
  && rm -f /etc/dpkg/dpkg.cfg.d/excludes

# Add OpenBao repo
RUN --mount=type=bind,from=mirror-scripts,source=configure-mirror-openbao-deb.sh,target=/tmp/configure-mirror-openbao-deb.sh \
    --mount=type=secret,id=mirror_token \
    /bin/bash /tmp/configure-mirror-openbao-deb.sh

# The signed remote apt repository, for external builds. The Release ADD is a
# cache-buster: a changed repository invalidates the install layer below.
FROM base AS repo-external
ARG DIST=resolute
ARG REPO=https://s3-eu-west-1.amazonaws.com/niis-xroad-development/develop/debian
ARG REPO_KEY=https://s3-eu-west-1.amazonaws.com/niis-xroad-development/develop/jenkins@niis.org.gpg
ARG COMPONENT=main

ADD ["$REPO_KEY","/tmp/repokey.gpg"]
ADD ["${REPO}/dists/${DIST}/Release","/tmp/Release"]

RUN gpg --dearmor -o /usr/share/keyrings/xroad-keyring.gpg '/tmp/repokey.gpg' \
  && echo "deb [signed-by=/usr/share/keyrings/xroad-keyring.gpg] $REPO $DIST $COMPONENT" >/etc/apt/sources.list.d/xroad.list

# Internal builds provision a temporary local repository from the bind-mounted
# tree-built DEBs at install time (files/xroad-internal-repo.sh), so no repo
# setup happens ahead of the install layer.
FROM base AS repo-internal

FROM repo-${PACKAGE_SOURCE} AS final
ARG PACKAGE_SOURCE
ARG XROAD_PACKAGES="\
    xroad-proxy \
    xroad-proxy-ui-api \
    xroad-database-remote \
    xroad-secret-store-remote \
    xroad-monitor \
    xroad-opmonitor \
    xroad-auxiliary-service \
    xroad-message-log-archiver \
    xroad-ds-control-plane \
    xroad-ds-identity-hub \
    openbao"

RUN --mount=type=bind,source=files/xroad-internal-repo.sh,target=/tmp/xroad-internal-repo.sh \
    --mount=type=bind,from=packages,target=/tmp/packages \
    /bin/bash /tmp/xroad-internal-repo.sh provision "$PACKAGE_SOURCE" \
  && apt-get -qq update \
  && XROAD_IGNORE_DATABASE_SETUP=1 apt-get -qq install $XROAD_PACKAGES \
  && /bin/bash /tmp/xroad-internal-repo.sh cleanup "$PACKAGE_SOURCE" \
  && apt-get -qq clean

COPY --chown=xroad:xroad files/*-logback*.xml files/variables-logback.properties /etc/xroad/conf.d/
COPY --chown=root:root files/secret-store-local.conf /etc/xroad/services/secret-store-local.conf

RUN rm -f \
    /etc/xroad/db.properties \
    /etc/xroad.properties \
    /etc/xroad/ssl/*.crt \
    /etc/xroad/ssl/*.key \
    /etc/xroad/ssl/*.p12 \
    /var/log/syslog \
    /var/log/xroad/* \
    /var/log/postgresql/* \
  && (grep -qF 'echo "-Xms$default_xms -Xmx$default_xmx"' /usr/share/xroad/scripts/proxy_memory_helper.sh \
      || { echo "ERROR: proxy_memory_helper.sh get-default line not found, refusing to patch proxy heap sizing" >&2; exit 1; }) \
  && sed -i 's/echo "-Xms$default_xms -Xmx$default_xmx"/echo "-Xms$default_xms -XX:MaxRAMPercentage=25.0"/' \
    /usr/share/xroad/scripts/proxy_memory_helper.sh \
# xroad-proxy.postinst bakes a literal -Xmx into local.properties at every "configure"
# run - both the package install above and the entrypoint's dpkg-reconfigure on first
# boot - via this script's own apply-default action, which still writes $default_xmx
# verbatim regardless of the get-default patch above; strip it there too, or a fresh
# container's actual launch reverts to a hardcoded heap on every first boot
  && (grep -qF '  apply_memory_config "XROAD_PROXY_PARAMS" "$default_xms" "$default_xmx"' \
      /usr/share/xroad/scripts/proxy_memory_helper.sh \
      || { echo "ERROR: proxy_memory_helper.sh apply-default line not found, refusing to patch proxy heap sizing" >&2; exit 1; }) \
  && sed -i 's#  apply_memory_config "XROAD_PROXY_PARAMS" "$default_xms" "$default_xmx"#&\n  sed -E -i "/^XROAD_PROXY_PARAMS=/ s/ ?-Xmx[0-9]+[kKmMgG]?//g" /etc/xroad/services/local.properties#' \
    /usr/share/xroad/scripts/proxy_memory_helper.sh \
# the same postinst configure run bakes the same literal into local.properties itself
# (before the guards above ever run for the build's own package-install pass); strip it
# here so both the live file and the config-backup seed below are clean
  && (test -f /etc/xroad/services/local.properties \
      && sed -E -i '/^XROAD_PROXY_PARAMS=/ s/ ?-Xmx[0-9]+[kKmMgG]?//g' /etc/xroad/services/local.properties; \
      true) \
  && (! grep -qE -- '-Xmx[0-9]+[kKmMgG]?' /etc/xroad/services/local.properties 2>/dev/null \
      || { echo "ERROR: literal -Xmx still present in /etc/xroad/services/local.properties, refusing to build" >&2; exit 1; }) \
##
# create a backup of the configuration for version migration and
# for initializing an empty config volume
  && { test -f /etc/xroad/services/local.properties || touch /etc/xroad/services/local.properties; } \
  && mkdir -p /etc/xroad/entrypoint.d \
  && mkdir -p /usr/share/xroad/config/etc/xroad /usr/share/xroad/config/backup \
  && cp -a /etc/xroad /usr/share/xroad/config/etc/ \
# move files that should not be overwritten during migration to another folder
# (these are needed when initializing an empty config volume)
  && mv /usr/share/xroad/config/etc/xroad/services/local.properties \
    /usr/share/xroad/config/backup/ \
##
  && dpkg-query --showformat='${Version}' --show xroad-proxy >/usr/share/xroad/config/VERSION \
  && cp /usr/share/xroad/config/VERSION /etc/xroad/VERSION \
  && sed -i 's/initctl/supervisorctl/g' /usr/share/xroad/scripts/_restore_xroad.sh

COPY --chown=root:root files/_entrypoint_common.sh /root/
COPY --chown=root:root files/ss-entrypoint.sh /root/entrypoint.sh
COPY --chown=root:root files/ss-xroad.conf /etc/supervisor/conf.d/xroad.conf
COPY --from=secret-store-scripts --chown=root:root _openbao.sh /usr/share/xroad/scripts/sidecar/
COPY --chown=root:root files/secret-store-init.sh files/xroad-secret-store-gate.sh files/dsp-config-seed.sh files/signer-autologin-config-seed.sh /usr/share/xroad/scripts/sidecar/
RUN chmod 0755 /usr/share/xroad/scripts/sidecar/*.sh

# Cleanup mirror configuration (restore public mirrors for published image)
RUN --mount=type=bind,from=mirror-scripts,source=cleanup-mirror-apt.sh,target=/tmp/cleanup-mirror-apt.sh \
    --mount=type=bind,from=mirror-scripts,source=cleanup-mirror-openbao-deb.sh,target=/tmp/cleanup-mirror-openbao-deb.sh \
    /bin/bash /tmp/cleanup-mirror-apt.sh && \
    /bin/bash /tmp/cleanup-mirror-openbao-deb.sh

CMD ["/root/entrypoint.sh"]

EXPOSE 8080 8443 4000 5500 5577 5588

# Liveness only: readiness includes the globalconf/serverconf checks, which are
# down by design before an anchor is imported, so it must not gate container health.
HEALTHCHECK --interval=15s --timeout=5s --start-period=180s --retries=5 \
  CMD curl -f http://localhost:5588/q/health/live || exit 1
