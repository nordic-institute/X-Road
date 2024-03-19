FROM alpine:3.19

RUN apk add --no-cache python3 nginx supervisor bash openssl curl \
  # Setup TEST-CA with TSA and OCSP
  && adduser -D ca && adduser -D -H -G ca ocsp

COPY build/home /home
COPY build/etc /etc
COPY build/usr /usr

COPY files/nginx.conf /etc/nginx/nginx.conf

COPY files/init.sh /home/ca/CA/
COPY files/ca.py /home/ca/CA/

RUN chown -R ca:ca /home/ca/CA \
  && find /home/ca/TSA -type f -exec chmod 0664 {} + \
  && find /home/ca/CA -type f -exec chmod 0740 {} + \
  && chmod 0700 /home/ca/CA/init.sh \
  && mkdir -p /var/log/ \
  && touch /var/log/ocsp.log \
  && chown ca:ca /var/log/ocsp.log \
  && chmod 0664 /var/log/ocsp.log \
  && chmod 0754 /home/ca/CA/ca.py \
  && chmod 0754 /home/ca/CA/sign_req.sh

COPY files/ca-entrypoint.sh /root/entrypoint.sh
COPY --chown=root:root files/supervisord.conf /etc/supervisor/supervisord.conf
COPY --chown=root:root files/testca.conf /etc/supervisor/conf.d/testca.conf

CMD ["/root/entrypoint.sh"]

EXPOSE 8888 8899
