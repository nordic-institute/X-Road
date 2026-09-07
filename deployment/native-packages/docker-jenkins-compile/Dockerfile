FROM ubuntu:26.04

RUN apt-get -y update \
  && apt-get install -y locales \
  && locale-gen en_US.UTF-8 \
  && apt-get clean

ENV LANG=en_US.UTF-8
ENV LANGUAGE=en_US:en
ENV LC_ALL=en_US.UTF-8
ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get -qq update \
  && apt-get -qq upgrade -y \
  && apt-get install -y -qq --no-install-recommends \
    ca-certificates \
    curl \
    gawk \
    openjdk-25-jdk-headless \
    build-essential \
    git \
    unzip \
    debhelper \
  && apt-get -qq autoremove -y \
  && rm -rf /var/lib/apt/lists/*

RUN install -m 0755 -d /etc/apt/keyrings \
  && curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc \
  && chmod a+r /etc/apt/keyrings/docker.asc \
  && tee /etc/apt/sources.list.d/docker.sources > /dev/null <<EOF
Types: deb
URIs: https://download.docker.com/linux/ubuntu
Suites: $(. /etc/os-release && echo "${UBUNTU_CODENAME:-$VERSION_CODENAME}")
Components: stable
Architectures: $(dpkg --print-architecture)
Signed-By: /etc/apt/keyrings/docker.asc
EOF

RUN apt-get -qq update \
  && apt-get -qq install -y \
    docker-ce \
    docker-ce-cli \
    containerd.io \
    docker-buildx-plugin \
    docker-compose-plugin \
  && apt-get -qq autoremove -y \
  && rm -rf /var/lib/apt/lists/*

ARG JENKINSUID
ARG JENKINSGID
ARG DOCKERGID

# Setup users and groups
RUN groupadd -o -g ${DOCKERGID} host-docker \
  && groupadd -o -g ${JENKINSGID} builder \
  && useradd -m -u ${JENKINSUID} -g ${JENKINSGID} -G host-docker builder \
  && mkdir -p /mnt/gradle-cache \
  && chown -R builder:builder /mnt/gradle-cache

USER builder
WORKDIR /mnt

ENV GRADLE_USER_HOME=/mnt/gradle-cache