FROM ubuntu:trusty
MAINTAINER "Ilkka Seppälä" <ilkka.seppala@gofore.com>

RUN apt-get update && apt-get install -y software-properties-common
RUN add-apt-repository ppa:openjdk-r/ppa
RUN apt-get update
RUN apt-get install git curl wget openjdk-8-jdk build-essential debhelper -y

USER jenkins
CMD ["sh", "-c", "/workspace/xtee6/deb-docker.sh" ]
