FROM openbao/openbao:latest

COPY xrd-dev-stack/openbao/init.sh /root/entrypoint.sh
COPY securityserver/files/edc/etc/certs /certs/ss
COPY centralserver/files/edc/etc/certs /certs/cs

ENV BAO_DEV_ROOT_TOKEN_ID=00000000-0000-0000-0000-000000000001

ENTRYPOINT ["/root/entrypoint.sh"]