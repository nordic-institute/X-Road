FROM ubuntu:trusty
MAINTAINER "Ilkka Seppälä" <ilkka.seppala@gofore.com>

ARG DEBIAN_FRONTEND=noninteractive
RUN apt-get -qq update && apt-get -qq install -y software-properties-common
RUN add-apt-repository ppa:openjdk-r/ppa
RUN apt-get -qq update && apt-get -qq install git curl wget openjdk-8-jre-headless debhelper -y
RUN update-ca-certificates -f
WORKDIR /workspace
