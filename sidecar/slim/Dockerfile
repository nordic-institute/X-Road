FROM ubuntu:focal
ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get -qq update \
  && apt-get -qq upgrade \
  && apt-get -qq install \
    sudo ca-certificates gnupg supervisor net-tools iproute2 locales \
    openjdk-17-jre-headless rlwrap ca-certificates-java \
    crudini adduser expect curl wget openssl nano-tiny \
  && echo "LC_ALL=en_US.UTF-8" >>/etc/environment \
  && locale-gen en_US.UTF-8 \
  && adduser --quiet --system --uid 998 --home /var/lib/postgresql \
    --no-create-home --shell /bin/bash --group postgres \
  && adduser --quiet --system --uid 999 --home /var/lib/xroad \
    --no-create-home --shell /bin/bash --group xroad \
  && adduser xroad sudo \
  && apt-get -qq install postgresql postgresql-contrib \
  && apt-get -qq clean

ARG VERSION
ARG DIST=focal-$VERSION
ARG REPO=https://artifactory.niis.org/xroad-release-deb
ARG REPO_KEY=https://artifactory.niis.org/api/gpg/key/public
ARG COMPONENT=main
ARG CERTS_PATH=/etc/xroad/ssl

ADD ["$REPO_KEY","/tmp/repokey.gpg"]
ADD ["${REPO}/dists/${DIST}/Release","/tmp/Release"]

RUN echo "deb $REPO $DIST $COMPONENT" >/etc/apt/sources.list.d/xroad.list \
  && apt-key add '/tmp/repokey.gpg' \
  && apt-get -qq update \
  && XROAD_IGNORE_DATABASE_SETUP=1 apt-get -qq install \
    xroad-proxy \
    xroad-proxy-ui-api \
    xroad-addon-metaservices \
    xroad-addon-wsdlvalidator \
    xroad-addon-messagelog \
    xroad-addon-opmonitoring \
    xroad-addon-proxymonitor \
    xroad-autologin \
    xroad-database-remote \
  && apt-get -qq clean

COPY --chown=xroad:xroad files/override-docker.ini files/*-logback*.xml files/variables-logback.properties /etc/xroad/conf.d/
COPY --chown=xroad:xroad files/addons/*-logback*.xml /etc/xroad/conf.d/addons/
COPY --chown=xroad:xroad files/custom-fetch-pin.sh /usr/share/xroad/autologin/

RUN rm -f \
    /etc/xroad/db.properties \
    /etc/xroad.properties \
    /etc/xroad/ssl/*.crt \
    /etc/xroad/ssl/*.key \
    /etc/xroad/ssl/*.p12 \
    /var/log/syslog \
    /var/log/xroad/* \
    /var/log/postgresql/* \
  && sed -E -i 's/-Xmx[0-9]+./-XX:MaxRAMPercentage=25.0/' /etc/xroad/services/proxy.conf \
##
# create a backup of the configuration for version migration and
# for initializing an empty config volume
  && mkdir -p /usr/share/xroad/config/etc/xroad /usr/share/xroad/config/backup \
  && cp -a /etc/xroad /usr/share/xroad/config/etc/ \
# move files that should not be overwritten during migration to another folder
# (these are needed when initializing an empty config volume)
  && rm -rf /usr/share/xroad/config/etc/xroad/signer/softtoken \
  && mv /usr/share/xroad/config/etc/xroad/conf.d/local.ini \
    /usr/share/xroad/config/etc/xroad/services/local.properties \
    /usr/share/xroad/config/etc/xroad/devices.ini \
    /usr/share/xroad/config/backup/ \
##
  && dpkg-query --showformat='${Version}' --show xroad-proxy >/usr/share/xroad/config/VERSION \
  && cp /usr/share/xroad/config/VERSION /etc/xroad/VERSION \
  && sed -i 's/initctl/supervisorctl/g' /usr/share/xroad/scripts/_restore_xroad.sh \
  && mv /usr/share/xroad/jlib/addon/proxy/messagelog.conf /usr/share/xroad/jlib/addon/proxy/messagelog.conf.disabled \
  && mv /usr/share/xroad/jlib/addon/proxy/opmonitoring.conf /usr/share/xroad/jlib/addon/proxy/opmonitoring.conf.disabled \
  && mv /usr/share/xroad/jlib/addon/proxy/proxymonitor-service.conf /usr/share/xroad/jlib/addon/proxy/proxymonitor-service.conf.disabled

COPY --chown=root:root files/_entrypoint_common.sh /root/
COPY --chown=root:root files/slim-ss-entrypoint.sh /root/entrypoint.sh
COPY --chown=root:root files/slim-ss-xroad.conf /etc/supervisor/conf.d/xroad.conf

CMD ["/root/entrypoint.sh"]

EXPOSE 8080 8443 4000 5500 5577 5588
