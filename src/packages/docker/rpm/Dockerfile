FROM centos:7
RUN yum -y install sudo git rpm-build
RUN yum clean all
RUN sed -i 's/requiretty/!requiretty/' /etc/sudoers
WORKDIR /workspace
