FROM ubuntu:20.04
RUN apt-get clean && apt-get -y update && apt-get install -y locales && locale-gen en_US.UTF-8
ENV LANG en_US.UTF-8
ENV LANGUAGE en_US:en
ENV LC_ALL en_US.UTF-8

ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get -y update && \
 apt-get -y install \
   apt-transport-https \
   ca-certificates \
   curl \
   gnupg \
   lsb-release \
   software-properties-common \
   gawk openjdk-11-jdk-headless build-essential git unzip debhelper

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
RUN groupadd -g ${JENKINSGID} jenkins
RUN groupmod -g ${DOCKERGID} docker
RUN useradd -c "Builder user" -g ${JENKINSGID} -G ${DOCKERGID} -M -N -u ${JENKINSUID} builder
RUN mkdir -p /mnt/gradle-cache && chown -R builder:builder /mnt/gradle-cache

USER builder
WORKDIR /mnt

ENV GRADLE_USER_HOME /mnt/gradle-cache
