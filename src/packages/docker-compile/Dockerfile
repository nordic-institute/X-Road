FROM ubuntu:16.04

RUN set -e

ENV JRUBY_VERSION="1.7.27"

RUN apt-get update && \
    apt-get install -y curl software-properties-common gawk

RUN apt-get install -y openjdk-8-jdk-headless build-essential git unzip debhelper && \
    update-ca-certificates -f

RUN useradd -m builder
USER builder

RUN cd ~ && \
    gpg --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys 409B6B1796C275462A1703113804BB82D39DC0E3 && \
    curl -L https://get.rvm.io | bash -s stable

RUN ~/.rvm/bin/rvm autolibs read-fail  && \
    ~/.rvm/bin/rvm install jruby-${JRUBY_VERSION} --binary && \
    ~/.rvm/bin/rvm jruby-${JRUBY_VERSION} do jgem install jruby-jars:${JRUBY_VERSION} bundler:1.14.6 warbler:1.4.9 bundler-audit

WORKDIR /mnt
