FROM rockylinux:8
RUN yum -y install sudo git rpm-build java-11-openjdk-headless \
    && yum clean all \
    && sed -i 's/requiretty/!requiretty/' /etc/sudoers
WORKDIR /workspace
