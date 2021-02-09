FROM ubuntu:18.04
ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get -qq update \
    && apt-get -qq upgrade \
    && apt-get -qq install curl software-properties-common gawk \
      openjdk-8-jdk-headless build-essential git unzip debhelper

ARG uid
ARG gid
RUN groupadd -g $gid builder && useradd -m -u $uid -g $gid builder

ENV JRUBY_VERSION="9.1.17.0"
ENV JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64

USER builder
WORKDIR /home/builder

RUN mkdir -p /var/tmp/xroad \
    && gpg --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys 409B6B1796C275462A1703113804BB82D39DC0E3 7D2BAF1CF37B13E2069D6956105BD0E739499BDB \
    && curl -L https://get.rvm.io | bash -s stable \
    && ~/.rvm/bin/rvm install jruby-$JRUBY_VERSION --binary --skip-gemsets

RUN ~/.rvm/bin/rvm jruby-$JRUBY_VERSION do jgem install \
    jruby-openssl jruby-launcher gem-wrappers rubygems-bundler \
    rake:13.0.1 rvm jruby-jars:$JRUBY_VERSION \
    bundler:1.14.6 warbler:2.0.4 bundler-audit \
    "ruby-maven:~>3.3.11" "jar-dependencies:0.4.1" "psych:3.0.3" \
    "parallel:1.19" "rubocop:0.81"
