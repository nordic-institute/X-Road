# Explicitly defining linux/amd64 ubuntu:22.04 image
FROM ubuntu@sha256:aa772c98400ef833586d1d517d3e8de670f7e712bf581ce6053165081773259d
ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get clean && apt-get -qq update && apt-get -qq upgrade && apt-get install -qq locales && locale-gen en_US.UTF-8
ENV LANG en_US.UTF-8
ENV LANGUAGE en_US:en
ENV LC_ALL en_US.UTF-8


RUN apt-get -qq update \
  && apt-get -qq install curl software-properties-common gawk \
  openjdk-17-jdk-headless build-essential git unzip debhelper \
  && apt-get -qq install softhsm opensc \
  && apt-get -qq autoremove \
  && apt-get -qq clean


# Create token
RUN mkdir -p /var/lib/softhsm/tokens/ && \
    softhsm2-util --init-token --slot 0 --label 'x-road-softhsm2' --so-pin 1234 --pin 1234

COPY --chown=root:root files/app.jar /root/app.jar
COPY --chown=root:root files/hwtoken.jar /root/lib/hwtoken.jar

COPY --chown=root:root etc /etc
COPY --chown=root:root var /var

EXPOSE 5558 5559 5560
