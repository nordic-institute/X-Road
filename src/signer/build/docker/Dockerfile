FROM ubuntu:20.04 AS ubuntu-base-java
ENV DEBIAN_FRONTEND=noninteractive
SHELL ["/bin/bash", "-c"]

RUN apt-get -qq update \
  && apt-get -qq upgrade \
  && apt-get -qq install openjdk-11-jdk-headless \
  && apt-get -qq autoremove \
  && apt-get -qq clean \
  && echo export JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64 >> /etc/bash.bashrc \
  && echo PATH=\$PATH:\$JAVA_HOME/bin >> /etc/bash.bashrc

FROM ubuntu-base-java

RUN addgroup xroad \
    && adduser --disabled-password --no-create-home --ingroup xroad xroad \
    # Below shall be removed when signer could finally be accessed from other locations than "localhost" \
    && apt-get -qq -y install openssh-server \
    && mkdir -p -m0755 /var/run/sshd \
    && useradd -m -s /bin/bash sshuser \
    && echo "sshuser:secret" | chpasswd


