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
ARG XRD_PASSWORD=secret

RUN apt-get -qq install \
    # To perform Postgres DB init \
    postgresql-client \
    # Workaround to separate signer to a different container by using SSH tunneling \
    sshpass \
    # Introduce `mkpasswd` \
    whois \
    # NB! Needs to be a system user. Otherwise insufficient permissions for PAM \
    && adduser --quiet --system --uid 999 --home /var/lib/xroad --no-create-home --shell /bin/bash --group xroad \
    # Add xroad to shadow group for PAM \
    && usermod --append -G shadow xroad \
    # Add all X-Road groups (https://docs.x-road.global/Manuals/ug-cs_x-road_6_central_server_user_guide.html#21-user-roles) \
    && groupadd --system xroad-registration-officer \
    && groupadd --system xroad-system-administrator \
    && groupadd --system xroad-security-officer
    #&& echo "auth        required          pam_tally2.so deny=3 even_deny_root unlock_time=900 file=/var/lib/xroad/tallylog" >> /etc/pam.d/xroad \
    #&& echo "@include    common-auth"                                                                                        >> /etc/pam.d/xroad \
    #&& echo "account     required          pam_tally2.so"                                                                    >> /etc/pam.d/xroad \
    #&& echo "@include    common-account"                                                                                     >> /etc/pam.d/xroad \
    #&& echo "password    required          pam_deny.so"                                                                      >> /etc/pam.d/xroad \
    #&& echo "session     required          pam_deny.so"                                                                      >> /etc/pam.d/xroad


