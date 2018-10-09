FROM ubuntu:trusty
ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get -qq update && apt-get -qq install -y software-properties-common
RUN add-apt-repository ppa:openjdk-r/ppa
RUN apt-get -qq update && apt-get -qqy install git curl wget openjdk-8-jre-headless debhelper devscripts
RUN update-ca-certificates -f
WORKDIR /workspace
