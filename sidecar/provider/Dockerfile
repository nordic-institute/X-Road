FROM niis/xroad-security-server-sidecar:latest

RUN pg_ctlcluster 10 main start \
&& apt-get -qq update \
&& apt-get -qq install -y xroad-addon-messagelog \
&& dpkg-reconfigure -fnoninteractive xroad-proxy xroad-addon-messagelog \
&& apt-get -qq clean \
&& pg_ctlcluster 10 main stop 
