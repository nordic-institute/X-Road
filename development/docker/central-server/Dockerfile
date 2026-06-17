# syntax=docker/dockerfile:1

# internal or external
ARG PACKAGE_SOURCE=internal

ARG DOCKER_REGISTRY=""
FROM ${DOCKER_REGISTRY}ubuntu:26.04 AS base
ENV DEBIAN_FRONTEND=noninteractive

ARG XROAD_MIRROR_URL
ARG XROAD_MIRROR_USER

RUN --mount=type=bind,from=mirror-scripts,source=configure-mirror-apt.sh,target=/tmp/configure-mirror-apt.sh \
    --mount=type=secret,id=mirror_token \
    /bin/bash /tmp/configure-mirror-apt.sh "$XROAD_MIRROR_URL" "$XROAD_MIRROR_USER"

RUN apt-get -qq update \
  && apt-get -qq install --no-install-recommends sudo ca-certificates gnupg supervisor net-tools locales openjdk-25-jre-headless rlwrap ca-certificates-java crudini adduser expect curl rsyslog dpkg-dev \
  && echo "LC_ALL=en_US.UTF-8" >>/etc/environment \
  && locale-gen en_US.UTF-8 \
  && adduser --quiet --system --uid 998 --home /var/lib/postgresql --no-create-home --shell /bin/bash --group postgres \
  && adduser --quiet --system --uid 999 --home /var/lib/xroad --no-create-home --shell /bin/bash --group xroad \
  && useradd -m xrd -s /usr/sbin/nologin -p '$6$AIURgrss2iB0xp/l$hz1/VmkIUoPEi..nyU9r.3Y4VCgGmWQyLBKCg7yRLUW9hCgjLOWV7s.6ZtTQ6CzuVw.k0mN4Hig0pkY7FH7Zz0' \
  && echo "xroad-center xroad-common/username string xrd" | debconf-set-selections \
  && apt-get -qq install --no-install-recommends postgresql postgresql-contrib \
  && apt-get -qq clean \
  && rm -rf var/lib/apt/lists/*

# Xroad CS from external packages
FROM base AS centralserver-external
ARG DIST=resolute-snapshot
ARG REPO=https://artifactory.niis.org/xroad-snapshot-deb
ARG REPO_KEY=https://artifactory.niis.org/api/gpg/key/public
ARG COMPONENT=main

# Add OpenBao repo
RUN --mount=type=bind,from=mirror-scripts,source=configure-mirror-openbao-deb.sh,target=/tmp/configure-mirror-openbao-deb.sh \
    --mount=type=secret,id=mirror_token \
    /bin/bash /tmp/configure-mirror-openbao-deb.sh

ADD ["$REPO_KEY","/tmp/repokey.gpg"]
ADD ["${REPO}/dists/${DIST}/Release","/tmp/Release"]
RUN echo "deb $REPO $DIST $COMPONENT" >/etc/apt/sources.list.d/xroad.list \
  && apt-key add '/tmp/repokey.gpg' \
  # Do not exclude man pages & other documentation
  && rm -f /etc/dpkg/dpkg.cfg.d/excludes \
  # Install
  && pg_ctlcluster 18 main start \
  && apt-get -qq update \
  && apt-get -qq install xroad-centralserver \
  && pg_ctlcluster 18 main stop \
  # Clean up
  && rm -rf /tmp/repo \
  && apt-get remove -qq dpkg-dev && apt-get -qq clean && apt-get -qq autoremove \
  && rm -rf var/lib/apt/lists/*

#Xroad CS from internal packages
FROM base AS centralserver-internal

# Add OpenBao repo
RUN --mount=type=bind,from=mirror-scripts,source=configure-mirror-openbao-deb.sh,target=/tmp/configure-mirror-openbao-deb.sh \
    --mount=type=secret,id=mirror_token \
    /bin/bash /tmp/configure-mirror-openbao-deb.sh

RUN --mount=type=bind,from=packages,target=/tmp/packages \
   cp -r /tmp/packages /tmp/repo \
  && cd /tmp/repo && dpkg-scanpackages -m . > Packages \
  && echo "deb [trusted=yes] file:/tmp/repo /" >/etc/apt/sources.list.d/xroad.list \
  # Do not exclude man pages & other documentation
  && rm -f /etc/dpkg/dpkg.cfg.d/excludes \
  # Install
  && pg_ctlcluster 18 main start \
  && apt-get -qq update \
  && apt-get -qq install xroad-centralserver \
  && pg_ctlcluster 18 main stop \
  # Clean up
  && rm -rf /tmp/repo \
  && apt-get remove -qq dpkg-dev && apt-get -qq clean && apt-get -qq autoremove \
  && rm -rf var/lib/apt/lists/*

# Build final image
FROM centralserver-${PACKAGE_SOURCE} AS final

# Cleanup mirror configuration
RUN --mount=type=bind,from=mirror-scripts,source=cleanup-mirror-apt.sh,target=/tmp/cleanup-mirror-apt.sh \
    --mount=type=bind,from=mirror-scripts,source=cleanup-mirror-openbao-deb.sh,target=/tmp/cleanup-mirror-openbao-deb.sh \
    /bin/bash /tmp/cleanup-mirror-apt.sh && \
    /bin/bash /tmp/cleanup-mirror-openbao-deb.sh

RUN { nginx -s stop ||:; } \
  && rm -f /var/run/nginx.pid \
  && rm -rf /tmp/xroad \
  # Back up read-only config (for volume support)
  && mkdir -p /root/etc/xroad \
  && cp -a /etc/xroad /root/etc/ \
  && rm -f /root/etc/xroad/services/local.properties \
     /root/etc/xroad/conf.d/local.ini \
     /root/etc/xroad/devices.ini \
     /root/etc/xroad/db.properties \
  && dpkg-query --showformat='${Version}' --show xroad-center >/root/VERSION \
  && cp /root/VERSION /etc/xroad/VERSION \
  && chmod 0755 /usr/share/xroad/scripts/verify_external_configuration.sh \
  # Clean up
  && rm -rf /tmp/repo \
  && apt-get remove -qq dpkg-dev && apt-get -qq clean && apt-get -qq autoremove \
  && rm -rf var/lib/apt/lists/*

# Setup additional users
# Setup additional users
RUN --mount=type=secret,id=user_passwd \
    useradd xrd-sys && adduser xrd-sys xroad-system-administrator && \
    sh -c "echo \"xrd-sys:\$(cat /run/secrets/user_passwd)\" | chpasswd"

COPY --chown=xroad:xroad files/etc /etc/

# Copy PostgreSQL performance configuration
COPY --from=perf --chown=postgres:postgres postgresql-performance.conf /etc/postgresql/18/main/postgresql-performance.conf

COPY files/cs-entrypoint.sh /root/entrypoint.sh
COPY --chown=root:root files/cs-xroad.conf /etc/supervisor/conf.d/xroad.conf
COPY --chmod=755 files/wait-for-secret-store.sh /usr/share/xroad/scripts/wait-for-secret-store.sh
CMD ["/root/entrypoint.sh"]

VOLUME ["/etc/xroad", "/var/lib/xroad", "/var/lib/postgresql/18/main/"]
EXPOSE 4000
