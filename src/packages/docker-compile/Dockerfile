FROM ubuntu:16.04

ARG uid
ARG gid

ENV JRUBY_VERSION="1.7.27"

RUN groupadd -g $gid builder && useradd -m -u $uid -g $gid builder

RUN mkdir -p /mnt/gradle-cache && chown -R builder:builder /mnt/gradle-cache

RUN apt-get update && \
    apt-get install -y curl software-properties-common gawk \
    openjdk-8-jdk-headless build-essential git unzip debhelper

USER builder

RUN cd ~ && \
    gpg --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys 409B6B1796C275462A1703113804BB82D39DC0E3 && \
    curl -L https://get.rvm.io | bash -s stable

RUN ~/.rvm/bin/rvm install jruby-$JRUBY_VERSION --binary --skip-gemsets && \
    ~/.rvm/bin/rvm jruby-$JRUBY_VERSION do jgem install jruby-openssl jruby-launcher \
    gem-wrappers executable-hooks:1.3.2 rubygems-bundler:1.4.4 rake:12.0.0 rvm jruby-jars:$JRUBY_VERSION bundler:1.14.6 warbler:1.4.9 bundler-audit && \
    mkdir -p /var/tmp/xroad

WORKDIR /mnt

ENV GRADLE_USER_HOME /mnt/gradle-cache
