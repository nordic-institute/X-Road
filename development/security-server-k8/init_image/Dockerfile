FROM ubuntu:24.04

RUN apt-get -qq update \
    && apt-get -qq -y install --no-install-recommends curl jq openssl \
    && apt-get -qq clean \
    && rm -rf var/lib/apt/lists/*

