FROM ubuntu:18.04
RUN apt-get clean && apt-get -y update && apt-get install -y locales && locale-gen en_US.UTF-8
ENV LANG en_US.UTF-8
ENV LANGUAGE en_US:en
ENV LC_ALL en_US.UTF-8

ENV DEBIAN_FRONTEND=noninteractive
ARG NODE_VERSION=14
COPY nodesource.gpg.key /etc/apt/sources.list.d/
RUN apt-get -qq update \
  && apt-get -qq upgrade \
  && apt-get -qq install curl software-properties-common gawk \
  openjdk-11-jdk-headless build-essential git unzip debhelper \
  && echo "deb https://deb.nodesource.com/node_${NODE_VERSION}.x bionic main" >/etc/apt/sources.list.d/nodesource.list \
  && apt-key add /etc/apt/sources.list.d/nodesource.gpg.key \
  && apt-get -qq update && apt-get -qq install nodejs \
  && apt-get -qq autoremove \
&& apt-get -qq clean

ARG uid=1000
ARG gid=1000

RUN groupadd -g $gid builder && useradd -m -u $uid -g $gid builder \
  && mkdir -p /mnt/gradle-cache && chown -R builder:builder /mnt/gradle-cache

USER builder
WORKDIR /mnt

ENV GRADLE_USER_HOME /mnt/gradle-cache
