FROM centos:7
RUN yum -y install sudo unzip git rpm-build java-1.8.0-openjdk-headless
RUN yum clean all
RUN sed -i 's/requiretty/!requiretty/' /etc/sudoers
WORKDIR /workspace
