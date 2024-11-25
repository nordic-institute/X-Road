FROM ubuntu:24.04
ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get -qq update \
    && apt-get -qq install --no-install-recommends openjdk-21-jre-headless locales adduser supervisor rlwrap rsyslog \
        dpkg-dev less vim \
    && echo "LC_ALL=en_US.UTF-8" >> /etc/environment \
    && locale-gen en_US.UTF-8 \
    && adduser --quiet --system --uid 999 --home /var/lib/xroad --no-create-home --shell /bin/bash --group xroad \
    && apt-get -qq clean

RUN --mount=type=bind,source=build/packages,target=/tmp/packages \
   cp -r /tmp/packages /tmp/repo \
  && cd /tmp/repo && dpkg-scanpackages -m . > Packages \
  && echo "deb [trusted=yes] file:/tmp/repo /" >/etc/apt/sources.list.d/xroad.list \
  && apt-get -qq update \
  && apt-get -qq install xroad-confproxy \
  # Clean up
  && rm -rf /tmp/repo

COPY --chown=xroad:xroad files/etc /etc/
COPY files/cp-entrypoint.sh /root/entrypoint.sh
COPY --chown=root:root files/cp-xroad.conf /etc/supervisor/conf.d/xroad.conf

CMD ["/root/entrypoint.sh"]
EXPOSE 80 443 9999