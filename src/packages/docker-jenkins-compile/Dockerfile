FROM ubuntu:22.04
RUN apt-get clean && apt-get -y update && apt-get install -y locales && locale-gen en_US.UTF-8
ENV LANG en_US.UTF-8
ENV LANGUAGE en_US:en
ENV LC_ALL en_US.UTF-8

ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get -qq install curl

ARG uid=1000
ARG gid=1000
ARG HOST_GID=990

RUN groupadd -o -g $gid jenkins && useradd -m -u $uid -g $gid jenkins \
  && mkdir -p /mnt/gradle-cache && chown -R jenkins:jenkins /mnt/gradle-cache

RUN groupadd -g $HOST_GID docker
RUN usermod -aG docker jenkins

USER jenkins

WORKDIR /mnt

ENV GRADLE_USER_HOME /mnt/gradle-cache
