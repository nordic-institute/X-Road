FROM ubuntu:22.04
RUN apt-get clean && apt-get -y update && apt-get install -y locales && locale-gen en_US.UTF-8
ENV LANG en_US.UTF-8
ENV LANGUAGE en_US:en
ENV LC_ALL en_US.UTF-8

ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get -qq update \
  && apt-get -qq upgrade \
  && apt-get -qq install curl software-properties-common gawk \
  openjdk-11-jdk-headless build-essential git unzip debhelper \
  && apt-get -qq install softhsm opensc\
  && apt-get -qq autoremove \
  && apt-get -qq clean

ARG uid=1000
ARG gid=1000

RUN groupadd -o -g $gid xroad && useradd -m -u $uid -g $gid xroad

# Create token
RUN mkdir -p /var/lib/softhsm/tokens/ && \
    softhsm2-util --init-token --slot 0 --label 'X-Road HW' --so-pin 1234 --pin 1234

COPY --chown=xroad:xroad files/app.jar /usr/share/xroad/app.jar
COPY --chown=xroad:xroad etc /etc
COPY --chown=xroad:xroad var /var

USER xroad

EXPOSE 5558 5559 5560
