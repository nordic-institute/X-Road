ARG REGISTRY_URL=localhost:5555
FROM ${REGISTRY_URL}/ss-signer

USER root

RUN apt-get -qq update \
  && apt-get -qq install --no-install-recommends curl softhsm2 opensc sudo \
  && apt-get -qq clean

RUN mkdir -p /var/lib/softhsm/tokens/ && \
    adduser xroad softhsm

USER xroad
RUN softhsm2-util --init-token --slot 0 --label 'x-road-softhsm2' --so-pin 1234 --pin 'Secret1234'

USER root
RUN mkdir -p /etc/xroad

RUN chown -R xroad:softhsm /var/lib/softhsm/tokens && \
    chown -R xroad:xroad /etc/xroad

COPY entrypoint-with-hsm.sh /usr/local/bin/entrypoint.sh
VOLUME ["/var/lib/softhsm/tokens", "/etc/xroad"]

ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]