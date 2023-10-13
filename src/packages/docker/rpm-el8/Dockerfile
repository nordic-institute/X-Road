FROM rockylinux:8
RUN yum -y install sudo git rpm-build \
    && yum clean all \
    && sed -i 's/requiretty/!requiretty/' /etc/sudoers
WORKDIR /workspace
