FROM openbao/openbao:2.4.1

RUN apk add --no-progress jq bash > /dev/null 2>&1
# creating xroad user and folder to reuse the secret-store-init.sh script
RUN addgroup -S xroad && \
    adduser -S -G xroad xroad && \
    mkdir -p /etc/xroad/

COPY --from=openbao-init-ctx /usr /usr

COPY files/ /
RUN chmod +x /entrypoint.sh

ENTRYPOINT ["/entrypoint.sh"]