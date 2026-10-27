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

# PostgreSQL 16 binaries for migration mode's database upgrade. Ubuntu 26.04
# has no postgresql-16, so it comes from the PostgreSQL apt repository.
RUN curl -fsSL https://www.postgresql.org/media/keys/ACCC4CF8.asc -o /usr/share/keyrings/postgresql-keyring.asc \
  && . /etc/os-release \
  && echo "deb [signed-by=/usr/share/keyrings/postgresql-keyring.asc] https://apt.postgresql.org/pub/repos/apt $VERSION_CODENAME-pgdg main" \
    >/etc/apt/sources.list.d/pgdg.list \
  && apt-get -qq update \
  && apt-get -qq install postgresql-16 \
  && apt-get -qq clean \
  && rm -f /etc/apt/sources.list.d/pgdg.list /usr/share/keyrings/postgresql-keyring.asc

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

RUN --mount=type=bind,source=files/patch-proxy-heap.sh,target=/tmp/patch-proxy-heap.sh \
    rm -f \
    /etc/xroad/db.properties \
    /etc/xroad.properties \
    /etc/xroad/ssl/*.crt \
    /etc/xroad/ssl/*.key \
    /etc/xroad/ssl/*.p12 \
    /var/log/syslog \
    /var/log/xroad/* \
    /var/log/postgresql/* \
  && /bin/bash /tmp/patch-proxy-heap.sh \
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

# Migration mode, with the native wizard's migration tasks and migration CLI.
COPY --chown=root:root files/migration/ /usr/share/xroad/scripts/sidecar/migration/
COPY --from=migration-tasks --chown=root:root lib/common.sh \
  /usr/share/xroad/scripts/sidecar/migration-tasks/lib/common.sh
COPY --from=migration-tasks --chown=root:root \
  tasks/migration/migrate_db_properties.sh \
  tasks/migration/cleanup_obsolete_files.sh \
  tasks/migration/migrate_tls_to_secret_store.sh \
  tasks/migration/run_migration_cli.sh \
  tasks/migration/fix_serverconf_identifier_duplicates.sql \
  /usr/share/xroad/scripts/sidecar/migration-tasks/tasks/migration/
ARG MIGRATION_CLI_URL=https://artifactory.niis.org/artifactory/xroad-scripts/0.0.1-beta/migration-cli.jar
RUN --mount=type=bind,from=migration-cli-jar,target=/tmp/migration-cli-jar \
  cli_jar=/usr/share/xroad/scripts/sidecar/migration-tasks/migration-cli.jar \
  && if [ -f /tmp/migration-cli-jar/migration-cli.jar ]; then \
    cp /tmp/migration-cli-jar/migration-cli.jar "$cli_jar"; \
  else \
    curl -fsSL "$MIGRATION_CLI_URL" -o "$cli_jar" \
    && echo "$(curl -fsSL "$MIGRATION_CLI_URL.sha256" | awk '{print $1}')  $cli_jar" | sha256sum -c -; \
  fi \
  && ln -sf "$cli_jar" /var/tmp/migration-cli.jar \
  && chmod 0755 /usr/share/xroad/scripts/sidecar/migration/orchestrator.sh \
    /usr/share/xroad/scripts/sidecar/migration/steps/*.sh \
    /usr/share/xroad/scripts/sidecar/migration-tasks/tasks/migration/*.sh

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
