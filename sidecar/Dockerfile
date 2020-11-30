FROM niis/xroad-security-server-sidecar:6.24.0-slim
RUN mv /usr/share/xroad/jlib/addon/proxy/messagelog.conf.disabled /usr/share/xroad/jlib/addon/proxy/messagelog.conf  \
&& mv /usr/share/xroad/jlib/addon/proxy/opmonitoring.conf.disabled /usr/share/xroad/jlib/addon/proxy/opmonitoring.conf

RUN  apt-get -qq install  xroad-addon-proxymonitor
COPY files/ss-entrypoint.sh /root/entrypoint.sh
COPY --chown=root:root files/ss-xroad.conf /etc/supervisor/conf.d/xroad.conf
CMD ["/root/entrypoint.sh"]
