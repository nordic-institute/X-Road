FROM openbao/openbao:2

COPY xrd-dev-stack/openbao/init.sh /root/entrypoint.sh

ENV BAO_DEV_ROOT_TOKEN_ID=00000000-0000-0000-0000-000000000001

ENTRYPOINT ["/root/entrypoint.sh"]
