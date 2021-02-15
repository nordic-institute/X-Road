FROM ubuntu:18.04

ARG uid
ARG gid

ENV DEBIAN_FRONTEND=noninteractive

RUN groupadd -g $gid builder && useradd -m -u $uid -g $gid builder

RUN mkdir -p /mnt/gradle-cache && chown -R builder:builder /mnt/gradle-cache

RUN apt-get -qq update && \
    apt-get -qq upgrade && \
    apt-get -qq install curl software-properties-common gawk \
    openjdk-8-jdk-headless build-essential git unzip debhelper \
    nodejs npm

USER builder
WORKDIR /mnt

ENV GRADLE_USER_HOME /mnt/gradle-cache
