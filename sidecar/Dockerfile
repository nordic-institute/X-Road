FROM ubuntu:bionic
ENV DEBIAN_FRONTEND=noninteractive
ARG XROAD_ADMIN_USER
ARG XROAD_ADMIN_PASSWORD

RUN apt-get -qq update \
&& apt-get -qq upgrade \
&& apt-get -qq install sudo ca-certificates gnupg supervisor net-tools iproute2 locales setpriv openjdk-8-jre-headless rlwrap ca-certificates-java crudini adduser expect nginx-light curl rsyslog authbind wget \
&& echo "LC_ALL=en_US.UTF-8" >>/etc/environment \
&& locale-gen en_US.UTF-8 \
&& adduser --quiet --system --uid 998 --home /var/lib/postgresql --no-create-home --shell /bin/bash --group postgres \
&& adduser --quiet --system --uid 999 --home /var/lib/xroad --no-create-home --shell /bin/bash --group xroad \
&& adduser xroad sudo \
&& useradd -m ${XROAD_ADMIN_USER} -s /usr/sbin/nologin \
&& echo "${XROAD_ADMIN_USER}:${XROAD_ADMIN_PASSWORD}" | chpasswd \
&& echo "xroad-proxy xroad-common/username string ${XROAD_ADMIN_USER}" | debconf-set-selections \
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
&& apt-get -qq install xroad-proxy xroad-addon-metaservices xroad-addon-wsdlvalidator xroad-autologin \
&& apt-get -qq clean \
&& pg_ctlcluster 10 main stop \
&& nginx -s stop \
&& rm -f /var/run/nginx.pid \
&& rm -f $CERTS_PATH/internal.crt $CERTS_PATH/internal.key $CERTS_PATH/nginx.crt $CERTS_PATH/nginx.key \
&& sed -i 's/proxy_set_header Host $host:$server_port;/proxy_set_header Host $http_host;/' /etc/xroad/nginx/default-xroad.conf

COPY files/ss-entrypoint.sh /root/entrypoint.sh
COPY --chown=root:root files/ss-xroad.conf /etc/supervisor/conf.d/xroad.conf
CMD ["/root/entrypoint.sh"]

EXPOSE 80 443 4000 5500 5577
