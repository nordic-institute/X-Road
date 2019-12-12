FROM ubuntu:18.04
RUN export DEBIAN_FRONTEND=noninteractive \
    && apt-get -qq update \
    && apt-get -qq upgrade \
    && apt-get -qq install \
        curl software-properties-common gawk wget devscripts openjdk-8-jdk-headless build-essential git unzip debhelper locales \
    && locale-gen en_US.UTF-8 \
    && update-locale "LANG=en_US.UTF-8"

ARG UID=1000
ARG GID=1000
RUN groupadd -g $GID builder && useradd -m -u $UID -g $GID builder

USER builder
WORKDIR /home/builder
ENV JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64
ENV LANG=en_US.UTF-8

ARG JRUBY_VERSION=9.1.17.0
RUN gpg --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys 409B6B1796C275462A1703113804BB82D39DC0E3 7D2BAF1CF37B13E2069D6956105BD0E739499BDB \
    && curl -L https://get.rvm.io | bash -s stable \
    && ~/.rvm/bin/rvm install jruby-$JRUBY_VERSION --binary --skip-gemsets \
    && ~/.rvm/bin/rvm jruby-$JRUBY_VERSION do \
        jgem install jruby-openssl jruby-launcher gem-wrappers rubygems-bundler \
        rake:12.0.0 rvm jruby-jars:$JRUBY_VERSION bundler:1.14.6 warbler:2.0.4 bundler-audit \
    && mkdir -p /var/tmp/xroad \
    && mkdir /home/builder/bin \
    && mkdir /home/builder/xroad \
    && ln -s /home/builder/xroad/src/gradlew /home/builder/bin/gw

COPY --chown=builder:builder build_and_deploy_all.sh /home/builder/bin/
COPY --chown=builder:builder build_and_deploy_single_module.sh /home/builder/bin/
ENV PATH /home/builder/bin:/home/builder/.rvm/bin:$PATH
CMD bash -l
