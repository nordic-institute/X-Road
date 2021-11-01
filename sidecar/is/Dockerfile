ARG VERSION=6.26.0
ARG TAG=niis/xroad-security-server-sidecar
FROM $TAG:$VERSION
ARG VERSION
# Clone the is settings into the docker container
ADD https://raw.githubusercontent.com/nordic-institute/X-Road/$VERSION/src/packages/src/xroad/default-configuration/override-securityserver-is.ini  /etc/xroad/conf.d/override-securityserver-is.ini

RUN chown xroad:xroad /etc/xroad/conf.d/override-securityserver-is.ini \
&& chmod 660 /etc/xroad/conf.d/override-securityserver-is.ini \
&& cp -p /etc/xroad/conf.d/override-securityserver-is.ini /root/etc/xroad/conf.d/
