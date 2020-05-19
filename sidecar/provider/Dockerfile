FROM niis/xroad-security-server-sidecar:latest

RUN mv /usr/share/xroad/jlib/addon/proxy/messagelog.conf.disabled /usr/share/xroad/jlib/addon/proxy/messagelog.conf \
&&  apt-get -qq install xroad-addon-proxymonitor 
COPY files/mslog-entrypoint.sh /root/entrypoint.sh
COPY --chown=root:root files/provider-ss-xroad.conf /etc/supervisor/conf.d/xroad.conf
CMD ["/root/entrypoint.sh"]
