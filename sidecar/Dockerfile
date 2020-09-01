FROM ubuntu:bionic
ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get -qq update \
&& apt-get -qq upgrade \
&& apt-get -qq install sudo ca-certificates gnupg supervisor net-tools iproute2 locales setpriv openjdk-8-jre-headless rlwrap ca-certificates-java crudini adduser expect nginx-light curl rsyslog authbind wget \
&& echo "LC_ALL=en_US.UTF-8" >>/etc/environment \
&& locale-gen en_US.UTF-8 \
&& adduser --quiet --system --uid 998 --home /var/lib/postgresql --no-create-home --shell /bin/bash --group postgres \
&& adduser --quiet --system --uid 999 --home /var/lib/xroad --no-create-home --shell /bin/bash --group xroad \
&& adduser xroad sudo \
&& useradd -m xrd -s /usr/sbin/nologin -p '$6$JeOzaeWnLAQSUVuO$GOJ0wUKSVQnOR4I2JgZxdKr.kMO.YGS21SGaAshaYhayv8kSV9WuIFCZHTGAX8WRRTB/2ojuLnJg4kMoyzpcu1' \
&& echo "xroad-proxy xroad-common/username string xrd" | debconf-set-selections \
&& apt-get -qq install postgresql postgresql-contrib \
&& apt-get -qq clean

ARG DIST=bionic-current
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
&& apt-get -qq install xroad-proxy xroad-addon-metaservices xroad-addon-wsdlvalidator xroad-autologin xroad-addon-messagelog xroad-addon-opmonitoring  xroad-addon-proxymonitor  \
&& su - postgres -c "psql postgres -tAc \"DROP DATABASE serverconf;\"" \
&& su - postgres -c "psql postgres -tAc \"DROP USER serverconf;\"" \
&& su - postgres -c "psql postgres -tAc \"DROP DATABASE messagelog;\"" \
&& su - postgres -c "psql postgres -tAc \"DROP USER messagelog;\"" \
&& su - postgres -c "psql postgres -tAc 'DROP DATABASE \"op-monitor\";'" \
&& su - postgres -c "psql postgres -tAc \"DROP USER opmonitor;\"" \
&& su - postgres -c "psql postgres -tAc \"DROP USER opmonitor_admin; \"" \
&& apt-get -qq clean \
&& pg_ctlcluster 10 main stop \
&& nginx -s stop \
&& userdel -r xrd \
&& rm -f /var/run/nginx.pid \
&& rm -f /etc/xroad/db.properties \
&& rm -f /etc/xroad.properties \
&& rm -f $CERTS_PATH/internal.crt $CERTS_PATH/internal.key $CERTS_PATH/nginx.crt $CERTS_PATH/nginx.key \
&& sed -i 's/proxy_set_header Host $host:$server_port;/proxy_set_header Host $http_host;/' /etc/xroad/nginx/default-xroad.conf

RUN crudini --set /etc/xroad/conf.d/local.ini proxy health-check-interface 0.0.0.0 \
&& crudini --set /etc/xroad/conf.d/local.ini proxy health-check-port 5588

RUN mkdir -p /root/etc/xroad \
&& cp -a /etc/xroad/* /root/etc/xroad/ \
&& rm /root/etc/xroad/services/local.conf \
   /root/etc/xroad/conf.d/local.ini \
   /root/etc/xroad/devices.ini \
&& dpkg-query --showformat='${Version}' --show xroad-proxy >/root/VERSION \
&& cp /root/VERSION /etc/xroad/VERSION

COPY files/ss-entrypoint.sh /root/entrypoint.sh
COPY --chown=root:root files/ss-xroad.conf /etc/supervisor/conf.d/xroad.conf
COPY --chown=xroad:xroad files/local.conf /etc/xroad/services/local.conf
COPY --chown=xroad:xroad files/*logback* /etc/xroad/conf.d/
COPY --chown=xroad:xroad files/local.conf /tmp/local.conf
COPY --chown=xroad:xroad files/*logback* /tmp/
COPY --chown=xroad:xroad files/custom-fetch-pin.sh /usr/share/xroad/autologin/

RUN sed -i 's/initctl/supervisorctl/g' /usr/share/xroad/scripts/_restore_xroad.sh

RUN mv /usr/share/xroad/jlib/addon/proxy/messagelog.conf /usr/share/xroad/jlib/addon/proxy/messagelog.conf.disabled \
&& mv /usr/share/xroad/jlib/addon/proxy/opmonitoring.conf /usr/share/xroad/jlib/addon/proxy/opmonitoring.conf.disabled \
&& mv /usr/share/xroad/jlib/addon/proxy/proxymonitor-service.conf /usr/share/xroad/jlib/addon/proxy/proxymonitor-service.conf.disabled
CMD ["/root/entrypoint.sh"]


EXPOSE 80 443 4000 5500 5577 5588
