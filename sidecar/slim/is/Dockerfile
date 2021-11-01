ARG VERSION=6.26.0
ARG TAG=niis/xroad-security-server-sidecar
FROM $TAG:$VERSION-slim
ARG VERSION
# Clone the is settings into the docker container
ADD --chown=xroad:xroad https://raw.githubusercontent.com/nordic-institute/X-Road/$VERSION/src/packages/src/xroad/default-configuration/override-securityserver-is.ini /etc/xroad/conf.d/override-securityserver-is.ini

RUN chmod 660 /etc/xroad/conf.d/override-securityserver-is.ini \
  && cp -a /etc/xroad/conf.d/override-securityserver-is.ini /root/etc/xroad/conf.d/
