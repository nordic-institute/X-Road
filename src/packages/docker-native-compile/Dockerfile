FROM ubuntu:20.04
ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get -qq update \
  && apt-get -qq upgrade \
  && apt-get -qq install curl software-properties-common gawk \
    openjdk-17-jdk-headless build-essential git unzip debhelper

ARG uid=1000
ARG gid=1000

RUN groupadd -o -g $gid builder && useradd -m -u $uid -g $gid builder \
  && mkdir -p /mnt/gradle-cache && chown -R builder:builder /mnt/gradle-cache

USER builder

ENV GRADLE_USER_HOME /mnt/gradle-cache

RUN mkdir -p /var/tmp/xroad

WORKDIR /mnt

