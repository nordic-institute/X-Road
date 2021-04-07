FROM ubuntu:18.04
ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get -qq update \
&& apt-get -qq upgrade \
&& apt-get -qq install curl software-properties-common gawk \
  openjdk-11-jdk-headless build-essential git unzip debhelper \
&& apt-get -qq autoremove \
&& apt-get -qq clean

ARG NODESHASUM=eb89c02153cfa25e40170e5e9b0ab43ad55d456af8b72ad2a8c2a42b7a647432
ARG NODEVERSION=v12.21.0

RUN mkdir -p /usr/local/nodejs \
  && echo "${NODESHASUM}  /tmp/node.tar.xz" >/tmp/shasum \
  && curl -s -o /tmp/node.tar.xz "https://nodejs.org/dist/$NODEVERSION/node-$NODEVERSION-linux-x64.tar.xz" \
  && sha256sum -c /tmp/shasum \
  && tar --strip-components=1 -C /usr/local/nodejs -x -f /tmp/node.tar.xz \
  && rm -f /tmp/shasum /tmp/node.tar.xz

ARG uid
ARG gid

RUN groupadd -g $gid builder && useradd -m -u $uid -g $gid builder
RUN mkdir -p /mnt/gradle-cache && chown -R builder:builder /mnt/gradle-cache

USER builder
WORKDIR /mnt

ENV GRADLE_USER_HOME /mnt/gradle-cache
ENV JAVA_HOME /usr/lib/jvm/java-11-openjdk-amd64
ENV PATH /usr/local/nodejs/bin:$JAVA_HOME/bin:$PATH
