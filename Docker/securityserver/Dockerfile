# syntax=docker/dockerfile:1

# internal or external
ARG PACKAGE_SOURCE

FROM ubuntu:24.04 AS base
ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get -qq update \
    && apt-get -qq -y upgrade \
    && apt-get -qq -y install --no-install-recommends \
        ca-certificates gnupg supervisor net-tools iproute2 locales \
        openjdk-21-jre-headless rlwrap ca-certificates-java \
        crudini adduser expect curl rsyslog dpkg-dev \
    && echo "LC_ALL=en_US.UTF-8" >>/etc/environment \
    && locale-gen en_US.UTF-8 \
    && adduser --quiet --system --uid 998 --home /var/lib/postgresql --no-create-home --shell /bin/bash --group postgres \
    && adduser --quiet --system --uid 999 --home /var/lib/xroad --no-create-home --shell /bin/bash --group xroad \
    && useradd -m xrd -s /usr/sbin/nologin -p '$6$JeOzaeWnLAQSUVuO$GOJ0wUKSVQnOR4I2JgZxdKr.kMO.YGS21SGaAshaYhayv8kSV9WuIFCZHTGAX8WRRTB/2ojuLnJg4kMoyzpcu1' \
    && echo "xroad-proxy xroad-common/username string xrd" | debconf-set-selections \
    && apt-get -qq -y install --no-install-recommends postgresql postgresql-contrib \
    && apt-get -qq clean \
    && rm -rf var/lib/apt/lists/*

# Xroad SS from external packages
FROM base AS securityserver-external

ARG DIST=jammy-snapshot
ARG REPO=https://artifactory.niis.org/xroad-snapshot-deb
ARG REPO_KEY=https://artifactory.niis.org/api/gpg/key/public
ARG COMPONENT=main

ADD ["$REPO_KEY","/tmp/repokey.gpg"]
ADD ["${REPO}/dists/${DIST}/Release","/tmp/Release"]
RUN echo "deb $REPO $DIST $COMPONENT" >/etc/apt/sources.list.d/xroad.list \
    && apt-key add '/tmp/repokey.gpg' \
    # Install
    && pg_ctlcluster 16 main start \
    && apt-get update \
    && apt-get -y -qq install xroad-securityserver xroad-autologin xroad-addon-opmonitoring \
    && pg_ctlcluster 16 main stop \
    # Clean up
    && rm -rf /tmp/repo \
    && apt-get remove -y -qq dpkg-dev && apt-get -qq clean && apt-get -qq autoremove \
    && rm -rf var/lib/apt/lists/*

# Xroad SS from internal packages
FROM base AS securityserver-internal

RUN --mount=type=bind,source=build/packages,target=/tmp/packages \
       cp -r /tmp/packages /tmp/repo \
    && cd /tmp/repo && dpkg-scanpackages -m . > Packages \
    && echo "deb [trusted=yes] file:/tmp/repo /" >/etc/apt/sources.list.d/xroad.list \
    # Install
    && pg_ctlcluster 16 main start \
    && apt-get update \
    && apt-get -y -qq install xroad-securityserver xroad-autologin xroad-addon-opmonitoring \
    && apt-get -qq clean \
    && pg_ctlcluster 16 main stop \
    # Clean up
    && rm -rf /tmp/repo \
    && apt-get remove -y -qq dpkg-dev && apt-get -qq clean && apt-get -qq autoremove \
    && rm -rf var/lib/apt/lists/*

# Build final image
FROM securityserver-${PACKAGE_SOURCE} AS final
# Back up read-only config (for volume support)
RUN mkdir -p /root/etc/xroad \
    && cp -a /etc/xroad /root/etc/ \
    && rm -f /root/etc/xroad/services/local.properties \
      /root/etc/xroad/conf.d/local.ini \
      /root/etc/xroad/devices.ini \
      /root/etc/xroad/db.properties \
    && rm -rf /root/etc/xroad/signer/softtoken \
    && dpkg-query --showformat='${Version}' --show xroad-proxy >/root/VERSION \
    && cp /root/VERSION /etc/xroad/VERSION

# Setup users
ARG USER_PASSWD=secret
RUN useradd xrd-sec && adduser xrd-sec xroad-security-officer && sh -c "echo 'xrd-sec:$USER_PASSWD' | chpasswd" \
    && useradd xrd-reg && adduser xrd-reg xroad-registration-officer && sh -c "echo 'xrd-reg:$USER_PASSWD' | chpasswd" \
    && useradd xrd-ser && adduser xrd-ser xroad-service-administrator && sh -c "echo 'xrd-ser:$USER_PASSWD' | chpasswd" \
    && useradd xrd-sys && adduser xrd-sys xroad-system-administrator && sh -c "echo 'xrd-sys:$USER_PASSWD' | chpasswd" \
    && useradd xrd-obs && adduser xrd-obs xroad-securityserver-observer && sh -c "echo 'xrd-obs:$USER_PASSWD' | chpasswd"

COPY --chown=xroad:xroad files/etc /etc/
COPY --chown=xroad:xroad build/libs /usr/share/xroad/jlib/

COPY files/ss-entrypoint.sh /root/entrypoint.sh
COPY --chown=xroad:xroad files/override-docker.ini /etc/xroad/conf.d/
COPY --chown=root:root files/ss-xroad.conf /etc/supervisor/conf.d/xroad.conf
CMD ["/root/entrypoint.sh"]

VOLUME ["/etc/xroad", "/var/lib/xroad", "/var/lib/postgresql/16/main/"]
EXPOSE 8080 8443 4000 5432 5500 5577 5558 80
