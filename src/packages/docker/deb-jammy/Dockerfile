FROM ubuntu:22.04
RUN export DEBIAN_FRONTEND=noninteractive; \
    apt-get -qq update && \
    apt-get -qq install \
    software-properties-common git curl wget debhelper devscripts

ENV DEBEMAIL='info@niis.org'
ENV DEBFULLNAME='NIIS'

WORKDIR /workspace
