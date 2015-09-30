FROM centos:7
MAINTAINER "Sami Kallio" <sami.kallio@gofore.com>
ENV RELEASE=0
#ENV SNAPSHOT=.`env TZ=UTC date +'%Y%m%d%H%M'`
ENV ROOT=`pwd`/..

RUN yum -y install epel-release 
RUN yum -y update
RUN yum -y install @development sudo rpmdevtools java-1.8.0-openjdk
RUN sed -i 's/requiretty/!requiretty/' /etc/sudoers

USER jenkins
CMD ["sh", "-c", "/workspace/xtee6/packages/build-xroad-rpm.sh && /workspace/xtee6/packages/build-jetty-rpm.sh" ]
