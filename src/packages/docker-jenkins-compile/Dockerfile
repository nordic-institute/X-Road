FROM ubuntu:20.04
RUN apt-get clean && apt-get -y update && apt-get install -y locales && locale-gen en_US.UTF-8
ENV LANG en_US.UTF-8
ENV LANGUAGE en_US:en
ENV LC_ALL en_US.UTF-8

ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get -qq update \
  && apt-get -qq upgrade \
  && apt-get -qq install curl software-properties-common gawk \
  openjdk-17-jdk-headless build-essential git unzip debhelper \
  && apt-get -qq autoremove \
  && apt-get -qq clean

RUN curl -fsSL https://download.docker.com/linux/debian/gpg | apt-key add -
RUN add-apt-repository \
   "deb [arch=amd64] https://download.docker.com/linux/ubuntu \
   $(lsb_release -cs) \
   stable"

RUN apt-get -y update && \
 apt-get -y install \
   docker-ce \
   docker-ce-cli \
   containerd.io

ARG JENKINSUID
ARG JENKINSGID
ARG DOCKERGID

# Setup users and groups
RUN groupmod -g ${DOCKERGID} docker
RUN groupadd -o -g ${JENKINSGID} builder && useradd -m -u ${JENKINSUID} -g ${JENKINSGID} -G ${DOCKERGID} builder \
  && mkdir -p /mnt/gradle-cache && chown -R builder:builder /mnt/gradle-cache

USER builder
WORKDIR /mnt

ENV GRADLE_USER_HOME /mnt/gradle-cache
