FROM ubuntu:18.04
ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get -qq update \
  && apt-get -qq upgrade \
  && apt-get -qq install curl software-properties-common gawk \
    openjdk-8-jdk-headless build-essential git unzip debhelper \
  && apt-get -qq autoremove \
  && apt-get -qq clean

ARG NODESHASUM=dae683f911fe5af11f1dc9d7746d80fccdb1e7b4bef31a570f5daffadd897c42
ARG NODEVERSION=v14.18.2
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

ENV GRADLE_USER_HOME /mnt/gradle-cache
ENV JAVA_HOME /usr/lib/jvm/java-8-openjdk-amd64
ENV PATH /usr/local/nodejs/bin:$JAVA_HOME/bin:$PATH
ENV JRUBY_VERSION=9.1.17.0

RUN cd ~ \
  && gpg --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys 409B6B1796C275462A1703113804BB82D39DC0E3 7D2BAF1CF37B13E2069D6956105BD0E739499BDB \
  && curl -L https://get.rvm.io | bash -s stable

RUN ~/.rvm/bin/rvm install jruby-$JRUBY_VERSION --binary --skip-gemsets \
  && ~/.rvm/bin/rvm jruby-$JRUBY_VERSION do jgem install jruby-openssl jruby-launcher \
    gem-wrappers rubygems-bundler rake:13.0.3 rvm jruby-jars:$JRUBY_VERSION bundler:1.14.6 warbler:2.0.4 bundler-audit parallel:1.19 rubocop:0.81 ruby-maven:3.3.11 \
  && mkdir -p /var/tmp/xroad

WORKDIR /mnt

