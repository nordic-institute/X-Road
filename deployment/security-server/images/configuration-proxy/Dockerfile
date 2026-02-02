ARG REGISTRY=localhost:5555
ARG IMAGE_TAG=latest
ARG BASE_IMAGE=base-images/ss-baseline-runtime
FROM ${REGISTRY}/${BASE_IMAGE}:${IMAGE_TAG}

# Create directories for instance configuration and public distribution
RUN mkdir -p /etc/xroad/confproxy \
             /var/lib/xroad/public && \
    chown -R xroad:xroad /etc/xroad/confproxy \
                          /var/lib/xroad/public

# Instance configuration (anchor.xml, conf.ini, certs) and generated signed configs for distribution
VOLUME ["/etc/xroad/confproxy", "/var/lib/xroad/public"]

COPY --from=build-artifacts --chown=xroad:xroad / /opt/app/cli

# files/ directory mirrors the container filesystem structure
COPY --chmod=755 --chown=xroad:xroad files/ /

ENV PATH="/opt/app/scripts:${PATH}"