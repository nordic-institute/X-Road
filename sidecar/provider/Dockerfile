FROM niis/xroad-security-server-sidecar:latest
COPY files/provider-ss-entrypoint.sh /root/provider-entrypoint.sh
COPY files/ss-entrypoint.sh /root/entrypoint.sh
CMD ["/root/provider-entrypoint.sh"]