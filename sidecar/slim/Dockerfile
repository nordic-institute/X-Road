FROM ubuntu:bionic
ARG VERSION=6.25.0
ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get -qq update \
  && apt-get -qq upgrade \
  && apt-get -qq install \
    sudo ca-certificates gnupg supervisor net-tools iproute2 locales \
    openjdk-8-jre-headless rlwrap ca-certificates-java \
    crudini adduser expect curl wget \
  && echo "LC_ALL=en_US.UTF-8" >>/etc/environment \
  && locale-gen en_US.UTF-8 \
  && adduser --quiet --system --uid 998 --home /var/lib/postgresql \
    --no-create-home --shell /bin/bash --group postgres \
  && adduser --quiet --system --uid 999 --home /var/lib/xroad \
    --no-create-home --shell /bin/bash --group xroad \
  && adduser xroad sudo \
  && adduser --quiet --disabled-login --no-create-home --shell /usr/sbin/nologin xrd \
  && echo "xroad-proxy xroad-common/username string xrd" | debconf-set-selections \
  && apt-get -qq install postgresql postgresql-contrib \
  && apt-get -qq clean

ARG DIST=bionic-$VERSION
ARG REPO=https://artifactory.niis.org/xroad-release-deb
ARG REPO_KEY=https://artifactory.niis.org/api/gpg/key/public
ARG COMPONENT=main
ARG CERTS_PATH=/etc/xroad/ssl

ADD ["$REPO_KEY","/tmp/repokey.gpg"]
ADD ["${REPO}/dists/${DIST}/Release","/tmp/Release"]
RUN echo "deb $REPO $DIST $COMPONENT" >/etc/apt/sources.list.d/xroad.list \
  && apt-key add '/tmp/repokey.gpg'

RUN pg_ctlcluster 10 main start \
  && apt-get -qq update \
  && apt-get -qq install \
    xroad-proxy \
    xroad-proxy-ui-api \
    xroad-addon-metaservices \
    xroad-addon-wsdlvalidator \
    xroad-addon-messagelog \
    xroad-addon-opmonitoring \
    xroad-addon-proxymonitor \
    xroad-autologin \
  && apt-get -qq clean \
  && su - postgres -c 'psql postgres -tA \
    -c "DROP DATABASE serverconf;" \
    -c "DROP USER serverconf;" \
    -c "DROP USER serverconf_admin;" \
    -c "DROP DATABASE messagelog;" \
    -c "DROP USER messagelog;" \
    -c "DROP USER messagelog_admin;" \
    -c "DROP DATABASE \"op-monitor\";" \
    -c "DROP USER opmonitor;" \
    -c "DROP USER opmonitor_admin;"'\
  && pg_ctlcluster 10 main stop \
  && userdel -r xrd

COPY --chown=xroad:xroad files/override-docker.ini files/*-logback.xml files/variables-logback.properties /etc/xroad/conf.d/
COPY --chown=xroad:xroad files/custom-fetch-pin.sh /usr/share/xroad/autologin/

RUN rm -f /etc/xroad/db.properties \
  && rm -f /etc/xroad.properties \
  && rm -f /etc/xroad/ssl/*.{crt,key} \
  && sed -E -i 's/-Xmx[0-9]+./-XX:MaxRAMPercentage=25.0/' /etc/xroad/services/proxy.conf \
  && mkdir -p /root/etc/xroad /root/backup \
  && cp -a /etc/xroad/* /root/etc/xroad/ \
  && mv /root/etc/xroad/conf.d/local.ini \
    /root/etc/xroad/services/local.conf \
    /root/etc/xroad/devices.ini /root/backup/ \
  && dpkg-query --showformat='${Version}' --show xroad-proxy >/root/VERSION \
  && cp /root/VERSION /etc/xroad/VERSION \
  && sed -i 's/initctl/supervisorctl/g' /usr/share/xroad/scripts/_restore_xroad.sh \
  && mv /usr/share/xroad/jlib/addon/proxy/messagelog.conf /usr/share/xroad/jlib/addon/proxy/messagelog.conf.disabled \
  && mv /usr/share/xroad/jlib/addon/proxy/opmonitoring.conf /usr/share/xroad/jlib/addon/proxy/opmonitoring.conf.disabled \
  && mv /usr/share/xroad/jlib/addon/proxy/proxymonitor-service.conf /usr/share/xroad/jlib/addon/proxy/proxymonitor-service.conf.disabled

COPY --chown=root:root files/_entrypoint_common.sh /root/
COPY --chown=root:root files/slim-ss-entrypoint.sh /root/entrypoint.sh
COPY --chown=root:root files/slim-ss-xroad.conf /etc/supervisor/conf.d/xroad.conf

CMD ["/root/entrypoint.sh"]

EXPOSE 8080 8443 4000 5500 5577 5588
