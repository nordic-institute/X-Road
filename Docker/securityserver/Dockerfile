FROM ubuntu:bionic
ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get -qq update \
    && apt-get -qq upgrade \
    && apt-get -qq install \
        ca-certificates gnupg supervisor net-tools iproute2 locales \
        setpriv openjdk-8-jre-headless rlwrap ca-certificates-java \ 
        crudini adduser expect curl rsyslog \
    && echo "LC_ALL=en_US.UTF-8" >>/etc/environment \
    && locale-gen en_US.UTF-8 \
    && adduser --quiet --system --uid 998 --home /var/lib/postgresql --no-create-home --shell /bin/bash --group postgres \
    && adduser --quiet --system --uid 999 --home /var/lib/xroad --no-create-home --shell /bin/bash --group xroad \
    && useradd -m xrd -s /usr/sbin/nologin -p '$6$JeOzaeWnLAQSUVuO$GOJ0wUKSVQnOR4I2JgZxdKr.kMO.YGS21SGaAshaYhayv8kSV9WuIFCZHTGAX8WRRTB/2ojuLnJg4kMoyzpcu1' \
    && echo "xroad-proxy xroad-common/username string xrd" | debconf-set-selections \
    && apt-get -qq install postgresql postgresql-contrib \
    && apt-get -qq clean

ARG DIST=bionic-current
ARG REPO=https://artifactory.niis.org/xroad-release-deb
ARG REPO_KEY=https://artifactory.niis.org/api/gpg/key/public
ARG COMPONENT=main

ADD ["$REPO_KEY","/tmp/repokey.gpg"]
ADD ["${REPO}/dists/${DIST}/Release","/tmp/Release"]
RUN echo "deb $REPO $DIST $COMPONENT" >/etc/apt/sources.list.d/xroad.list \
    && apt-key add '/tmp/repokey.gpg'

RUN pg_ctlcluster 10 main start \
    && apt-get update \
    && apt-get -qq install xroad-securityserver xroad-autologin \
    && apt-get -qq clean \
    && pg_ctlcluster 10 main stop

# back up read-only config (for volume support)
RUN mkdir -p /root/etc/xroad \
    && cp -a /etc/xroad/* /root/etc/xroad/ \
    && rm /root/etc/xroad/services/local.conf ||: \
    && rm /root/etc/xroad/conf.d/local.ini \
      /root/etc/xroad/devices.ini \
      /root/etc/xroad/db.properties \
    && dpkg-query --showformat='${Version}' --show xroad-proxy >/root/VERSION \
    && cp /root/VERSION /etc/xroad/VERSION

COPY files/ss-entrypoint.sh /root/entrypoint.sh
COPY --chown=root:root files/ss-xroad.conf /etc/supervisor/conf.d/xroad.conf
CMD ["/root/entrypoint.sh"]

VOLUME /var/lib/postgresql/10/main
VOLUME /etc/xroad
EXPOSE 80 443 4000 5500 5577
