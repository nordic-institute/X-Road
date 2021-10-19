ARG VERSION=6.26.0
ARG TAG=niis/xroad-security-server-sidecar
FROM $TAG:$VERSION-slim
ARG VERSION
# Clone the fo settings into the docker container
ADD --chown=xroad:xroad https://raw.githubusercontent.com/nordic-institute/X-Road/$VERSION/src/packages/src/xroad/default-configuration/override-securityserver-fo.ini /etc/xroad/conf.d/override-securityserver-fo.ini

RUN chmod 660 /etc/xroad/conf.d/override-securityserver-fo.ini \
  && cp -a /etc/xroad/conf.d/override-securityserver-fo.ini /root/etc/xroad/conf.d/
