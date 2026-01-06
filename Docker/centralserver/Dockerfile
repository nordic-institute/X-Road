# syntax=docker/dockerfile:1

# internal or external
ARG PACKAGE_SOURCE

FROM ubuntu:24.04 AS base
ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get -qq update \
  && apt-get -qq install --no-install-recommends sudo ca-certificates gnupg supervisor net-tools locales openjdk-21-jre-headless rlwrap ca-certificates-java crudini adduser expect curl rsyslog dpkg-dev \
  && echo "LC_ALL=en_US.UTF-8" >>/etc/environment \
  && locale-gen en_US.UTF-8 \
  && adduser --quiet --system --uid 998 --home /var/lib/postgresql --no-create-home --shell /bin/bash --group postgres \
  && adduser --quiet --system --uid 999 --home /var/lib/xroad --no-create-home --shell /bin/bash --group xroad \
  && useradd -m xrd -s /usr/sbin/nologin -p '$6$JeOzaeWnLAQSUVuO$GOJ0wUKSVQnOR4I2JgZxdKr.kMO.YGS21SGaAshaYhayv8kSV9WuIFCZHTGAX8WRRTB/2ojuLnJg4kMoyzpcu1' \
  && echo "xroad-center xroad-common/username string xrd" | debconf-set-selections \
  && apt-get -qq install --no-install-recommends postgresql postgresql-contrib \
  && apt-get -qq clean \
  && rm -rf var/lib/apt/lists/*

# Xroad CS from external packages
FROM base AS centralserver-external
ARG DIST=jammy-snapshot
ARG REPO=https://artifactory.niis.org/xroad-snapshot-deb
ARG REPO_KEY=https://artifactory.niis.org/api/gpg/key/public
ARG COMPONENT=main

ADD ["$REPO_KEY","/tmp/repokey.gpg"]
ADD ["${REPO}/dists/${DIST}/Release","/tmp/Release"]
RUN echo "deb $REPO $DIST $COMPONENT" >/etc/apt/sources.list.d/xroad.list \
  && apt-key add '/tmp/repokey.gpg' \
  # Do not exclude man pages & other documentation
  && rm -f /etc/dpkg/dpkg.cfg.d/excludes \
  # Install
  && pg_ctlcluster 16 main start \
  && apt-get -qq update \
  && apt-get -qq install xroad-centralserver xroad-autologin \
  && pg_ctlcluster 16 main stop \
  # Clean up
  && rm -rf /tmp/repo \
  && apt-get remove -qq dpkg-dev && apt-get -qq clean && apt-get -qq autoremove \
  && rm -rf var/lib/apt/lists/*

#Xroad CS from internal packages
FROM base AS centralserver-internal

RUN --mount=type=bind,source=build/packages,target=/tmp/packages \
   cp -r /tmp/packages /tmp/repo \
  && cd /tmp/repo && dpkg-scanpackages -m . > Packages \
  && echo "deb [trusted=yes] file:/tmp/repo /" >/etc/apt/sources.list.d/xroad.list \
  # Do not exclude man pages & other documentation
  && rm -f /etc/dpkg/dpkg.cfg.d/excludes \
  # Install
  && pg_ctlcluster 16 main start \
  && apt-get -qq update \
  && apt-get -qq install xroad-centralserver xroad-autologin \
  && pg_ctlcluster 16 main stop \
  # Clean up
  && rm -rf /tmp/repo \
  && apt-get remove -qq dpkg-dev && apt-get -qq clean && apt-get -qq autoremove \
  && rm -rf var/lib/apt/lists/*

# Build final image
FROM centralserver-${PACKAGE_SOURCE} AS final

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
ARG USER_PASSWD=secret
RUN useradd xrd-sys && adduser xrd-sys xroad-system-administrator && sh -c "echo 'xrd-sys:$USER_PASSWD' | chpasswd"

COPY --chown=xroad:xroad files/etc /etc/

COPY files/cs-entrypoint.sh /root/entrypoint.sh
COPY --chown=root:root files/cs-xroad.conf /etc/supervisor/conf.d/xroad.conf
CMD ["/root/entrypoint.sh"]

VOLUME ["/etc/xroad", "/var/lib/xroad", "/var/lib/postgresql/16/main/"]
EXPOSE 4000