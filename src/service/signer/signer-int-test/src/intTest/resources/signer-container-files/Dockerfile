ARG SIGNER_IMG

FROM $SIGNER_IMG

USER root

RUN apt-get -qq update \
  && apt-get -qq install --no-install-recommends curl softhsm2 \
  && apt-get -qq clean

RUN mkdir -p /var/lib/softhsm/tokens/ && \
    adduser xroad softhsm

USER xroad
RUN softhsm2-util --init-token --slot 0 --label 'x-road-softhsm2' --so-pin 1234 --pin 1234

COPY etc/xroad/signer/devices.ini /etc/xroad/signer/devices.ini
COPY etc/xroad/conf.d/local.yaml /etc/xroad/conf.d/local.yaml
