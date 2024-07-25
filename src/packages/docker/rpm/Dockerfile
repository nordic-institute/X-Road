FROM centos:7
RUN sed -i s/mirror.centos.org/vault.centos.org/g /etc/yum.repos.d/*.repo
RUN sed -i s/^#.*baseurl=http/baseurl=http/g /etc/yum.repos.d/*.repo
RUN sed -i s/^mirrorlist=http/#mirrorlist=http/g /etc/yum.repos.d/*.repo
RUN yum -y install sudo git rpm-build
RUN yum clean all
RUN sed -i 's/requiretty/!requiretty/' /etc/sudoers
WORKDIR /workspace
