FROM localhost:5555/ss-signer

USER root

RUN apt-get -qq update \
  && apt-get -qq install --no-install-recommends curl softhsm2 opensc sudo \
  && apt-get -qq clean

RUN mkdir -p /var/lib/softhsm/tokens/ && \
    adduser xroad softhsm

USER xroad
RUN softhsm2-util --init-token --slot 0 --label 'x-road-softhsm2' --so-pin 1234 --pin 'Secret1234'

USER root

RUN mkdir -p /etc/xroad; \
    slot_id="$(softhsm2-util --show-slots | awk ' \
      /^Slot / { slot=$2 } \
      /Label: *x-road-softhsm2/ { print slot } \
    ')"; \
    cat >> /etc/xroad/devices.ini <<EOF
[softhsm2]
  library = /usr/lib/softhsm/libsofthsm2.so
  slot_ids = ${slot_id}
  os_locking_ok = true
  library_cant_create_os_threads = true
EOF

RUN chown -R xroad:softhsm /var/lib/softhsm/tokens && \
    chown -R xroad:xroad /etc/xroad

VOLUME ["/var/lib/softhsm/tokens", "/etc/xroad"]
