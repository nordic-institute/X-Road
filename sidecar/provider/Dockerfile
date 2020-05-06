FROM xroad-sidecar-security-server-image:latest

RUN mv /usr/share/xroad/jlib/addon/proxy/messagelog.conf.disabled /usr/share/xroad/jlib/addon/proxy/messagelog.conf
COPY files/mslog-entrypoint.sh /root/entrypoint.sh
CMD ["/root/entrypoint.sh"]
