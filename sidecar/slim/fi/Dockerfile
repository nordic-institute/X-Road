ARG VERSION=6.26.0
ARG TAG=niis/xroad-security-server-sidecar
FROM $TAG:$VERSION-slim
ARG VERSION
# Clone the fi settings into the docker container
ADD --chown=xroad:xroad https://raw.githubusercontent.com/nordic-institute/X-Road/$VERSION/src/packages/src/xroad/default-configuration/override-securityserver-fi.ini /etc/xroad/conf.d/override-securityserver-fi.ini

RUN chmod 660 /etc/xroad/conf.d/override-securityserver-fi.ini \
  && cp -a /etc/xroad/conf.d/override-securityserver-fi.ini /root/etc/xroad/conf.d/
