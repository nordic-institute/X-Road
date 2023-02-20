FROM ubuntu:20.04
ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get -qq update \
  && apt-get -qq upgrade \
  && apt-get -qq install curl software-properties-common gawk \
    openjdk-8-jdk-headless build-essential git unzip debhelper \
  && apt-get -qq autoremove \
  && apt-get -qq clean

ARG uid=1000
ARG gid=1000

RUN groupadd -o -g $gid builder && useradd -m -u $uid -g $gid builder \
  && mkdir -p /mnt/gradle-cache && chown -R builder:builder /mnt/gradle-cache

USER builder

ENV GRADLE_USER_HOME /mnt/gradle-cache
ENV JRUBY_VERSION=9.1.17.0

RUN cd ~ \
  && gpg --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys 409B6B1796C275462A1703113804BB82D39DC0E3 7D2BAF1CF37B13E2069D6956105BD0E739499BDB \
  && curl -L https://get.rvm.io | bash -s stable

RUN ~/.rvm/bin/rvm install jruby-$JRUBY_VERSION --binary --skip-gemsets \
  && ~/.rvm/bin/rvm jruby-$JRUBY_VERSION do jgem install jruby-openssl jruby-launcher \
    gem-wrappers rubygems-bundler rake:13.0.6 rvm jruby-jars:$JRUBY_VERSION bundler:1.14.6 warbler:2.0.4 bundler-audit parallel:1.19 rubocop:0.81 ruby-maven:3.3.11 \
  && mkdir -p /var/tmp/xroad

WORKDIR /mnt

