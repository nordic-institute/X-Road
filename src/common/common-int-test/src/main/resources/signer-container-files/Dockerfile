FROM ubuntu:24.04

RUN apt-get clean && apt-get -y update && apt-get install -qq -y locales && locale-gen en_US.UTF-8
ENV LANG en_US.UTF-8
ENV LANGUAGE en_US:en
ENV LC_ALL en_US.UTF-8

ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get -qq update \
  && apt-get -qq install curl software-properties-common gawk \
  openjdk-21-jdk-headless build-essential git unzip debhelper \
  && apt-get -qq install softhsm2 opensc\
  && apt-get -qq autoremove \
  && apt-get -qq clean


# Create token
RUN mkdir -p /var/lib/softhsm/tokens/ && \
    softhsm2-util --init-token --slot 0 --label 'x-road-softhsm2' --so-pin 1234 --pin 1234

COPY --chown=root:root files/app.jar /root/app.jar
COPY --chown=root:root files/lib /root/lib

COPY --chown=root:root etc /etc
COPY --chown=root:root var /var

EXPOSE 5558 5560
