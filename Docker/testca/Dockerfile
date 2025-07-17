FROM ubuntu:24.04

# ACME
ARG A2C_VER=0.35
ADD https://github.com/grindsa/acme2certifier/releases/download/${A2C_VER}/acme2certifier_${A2C_VER}-1_all.deb /tmp/

ENV DEBIAN_FRONTEND=noninteractive
ENV ORGANIZATION_NAME=Test

RUN apt-get -qq update \
  && apt-get -qq install --no-install-recommends  \
    python3 nginx supervisor bash openssl curl python3-pip uwsgi uwsgi-plugin-python3 \
  && apt -y install --no-install-recommends /tmp/acme2certifier_${A2C_VER}-1_all.deb \
  && apt-get -qq clean \
  #Setup TEST-CA with TSA and OCSP
  && useradd -m ca -U \
  && useradd -G ca ocsp

COPY build/home /home
COPY build/etc /etc
COPY build/usr /usr

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
COPY --chown=root:root files/testca.conf /etc/supervisor/conf.d/testca.conf

RUN sed -i "s/run\/uwsgi\/acme.sock/var\/www\/acme2certifier\/acme.sock/g" /var/www/acme2certifier/examples/nginx/nginx_acme_srv.conf \
    && sed -i "s/80/8887/g" /var/www/acme2certifier/examples/nginx/nginx_acme_srv.conf \
    && cp /var/www/acme2certifier/examples/nginx/nginx_acme_srv.conf /etc/nginx/sites-available/acme_srv.conf \
    && ln -s /etc/nginx/sites-available/acme_srv.conf /etc/nginx/sites-enabled/acme_srv.conf \
    && sed -i "s/\/run\/uwsgi\/acme.sock/acme.sock/g" /var/www/acme2certifier/examples/nginx/acme2certifier.ini \
    && sed -i "s/nginx/www-data/g" /var/www/acme2certifier/examples/nginx/acme2certifier.ini \
    && echo "plugins = python3" >> /var/www/acme2certifier/examples/nginx/acme2certifier.ini \
    && cp /var/www/acme2certifier/examples/nginx/acme2certifier.ini /var/www/acme2certifier \
    && usermod -a -G ca www-data
COPY --chown=www-data:www-data build/acme2certifier/acme_srv.cfg /var/www/acme2certifier/acme_srv/acme_srv.cfg
COPY --chown=www-data:www-data build/acme2certifier/kid_profiles.json /var/www/acme2certifier/examples/eab_handler/kid_profiles.json
COPY --chown=www-data:www-data build/acme2certifier/openssl_ca_handler.py /var/www/acme2certifier/examples/ca_handler/openssl_ca_handler.py

CMD ["/root/entrypoint.sh"]

EXPOSE 8888 8899
