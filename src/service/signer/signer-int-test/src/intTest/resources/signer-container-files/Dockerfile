ARG SIGNER_IMG

FROM $SIGNER_IMG

USER root

RUN apt-get -qq update \
  && apt-get -qq install --no-install-recommends curl softhsm2 \
                  opensc \
  && apt-get -qq clean

RUN mkdir -p /var/lib/softhsm/tokens/ && \
    adduser xroad softhsm

COPY add-key-into-hsm.sh /add-key-into-hsm.sh
RUN chmod +x /add-key-into-hsm.sh

USER xroad
RUN softhsm2-util --init-token --slot 0 --label 'x-road-softhsm2' --so-pin 1234 --pin 1234

COPY etc/xroad/signer/devices.ini /etc/xroad/signer/devices.ini
COPY etc/xroad/conf.d/local.yaml /etc/xroad/conf.d/local.yaml

EXPOSE 5559 5560
