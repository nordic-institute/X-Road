FROM ubuntu:22.04
ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get -qq update \
  && apt-get -qq upgrade \
  && apt-get -qq install sudo ca-certificates gnupg supervisor net-tools locales openjdk-17-jre-headless rlwrap ca-certificates-java crudini adduser expect nginx-light curl rsyslog wget python3 \
  && echo "LC_ALL=en_US.UTF-8" >>/etc/environment \
  && locale-gen en_US.UTF-8 \
  && adduser --quiet --system --uid 998 --home /var/lib/postgresql --no-create-home --shell /bin/bash --group postgres \
  && adduser --quiet --system --uid 999 --home /var/lib/xroad --no-create-home --shell /bin/bash --group xroad \
  && useradd -m xrd -s /usr/sbin/nologin -p '$6$JeOzaeWnLAQSUVuO$GOJ0wUKSVQnOR4I2JgZxdKr.kMO.YGS21SGaAshaYhayv8kSV9WuIFCZHTGAX8WRRTB/2ojuLnJg4kMoyzpcu1' \
  && echo "xroad-center xroad-common/username string xrd" | debconf-set-selections \
  && apt-get -qq install postgresql postgresql-contrib \
  && apt-get -qq clean

ARG DIST=jammy-current
ARG REPO=https://artifactory.niis.org/xroad-release-deb
ARG REPO_KEY=https://artifactory.niis.org/api/gpg/key/public
ARG COMPONENT=main

ADD ["$REPO_KEY","/tmp/repokey.gpg"]
ADD ["${REPO}/dists/${DIST}/Release","/tmp/Release"]
RUN echo "deb $REPO $DIST $COMPONENT" >/etc/apt/sources.list.d/xroad.list \
  && apt-key add '/tmp/repokey.gpg'

RUN pg_ctlcluster 14 main start \
  && apt-get -qq update \
  && apt-get -qq install xroad-centralserver xroad-autologin \
  && apt-get -qq clean \
  && pg_ctlcluster 14 main stop \
  && { nginx -s stop ||:; } \
  && rm -f /var/run/nginx.pid \
  && rm -rf /tmp/xroad

# back up read-only config (for volume support)
RUN mkdir -p /root/etc/xroad \
  && cp -a /etc/xroad /root/etc/ \
  && rm -f /root/etc/xroad/services/local.properties \
     /root/etc/xroad/conf.d/local.ini \
     /root/etc/xroad/devices.ini \
     /root/etc/xroad/db.properties \
  && dpkg-query --showformat='${Version}' --show xroad-center >/root/VERSION \
  && cp /root/VERSION /etc/xroad/VERSION

#Setup TEST-CA with TSA and OCSP
RUN useradd -m ca -U \
  && useradd -G ca ocsp
COPY home /home
COPY etc /etc
COPY usr /usr

COPY files/init.sh /home/ca/CA/
COPY files/ca.py /home/ca/CA/
RUN chown -R ca:ca /home/ca/CA \
  && find /home/ca/TSA -type f -exec chmod 0664 {} + \
  && find /home/ca/CA -type f -exec chmod 0740 {} + \
  && chmod 0700 /home/ca/CA/init.sh \
  && mkdir -p /var/log/ \
  && touch /var/log/ocsp.log \
  && chown ca:ca /var/log/ocsp.log \
  && chmod 0664 /var/log/ocsp.log \
  && chmod 0754 /home/ca/CA/ca.py \
  && chmod 0754 /home/ca/CA/sign_req.sh \
  && chmod 0755 /usr/share/xroad/scripts/verify_external_configuration.sh

# Setup users
ARG USER_PASSWD=secret
RUN useradd xrd-sys && adduser xrd-sys xroad-system-administrator && sh -c "echo 'xrd-sys:$USER_PASSWD' | chpasswd"

COPY files/cs-entrypoint.sh /root/entrypoint.sh
COPY --chown=root:root files/cs-xroad.conf /etc/supervisor/conf.d/xroad.conf
CMD ["/root/entrypoint.sh"]

EXPOSE 80 4000 2222
