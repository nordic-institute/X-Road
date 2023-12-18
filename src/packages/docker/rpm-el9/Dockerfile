FROM rockylinux:9
RUN #dnf -y install sudo git rpm-build systemd-rpm-macros systemd \
RUN dnf -y install git rpm-build systemd-rpm-macros systemd \
    && dnf clean all
#    && sed -i 's/requiretty/!requiretty/' /etc/sudoers
WORKDIR /workspace
