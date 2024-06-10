FROM ubuntu:22.04

RUN apt-get update \
  && apt-get -qq install --no-install-recommends python3 nginx supervisor bash openssl curl python3-pip uwsgi uwsgi-plugin-python3 \
  # Setup TEST-CA with TSA and OCSP
  && adduser --disabled-password ca && adduser --disabled-password --no-create-home --ingroup ca ocsp

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

# ACME
ARG A2C_VER=0.34
ADD https://github.com/grindsa/acme2certifier/releases/download/$A2C_VER/acme2certifier_$A2C_VER-1_all.deb /tmp/
RUN apt update
RUN apt -y install /tmp/acme2certifier_$A2C_VER-1_all.deb
RUN sed -i "s/run\/uwsgi\/acme.sock/var\/www\/acme2certifier\/acme.sock/g" /var/www/acme2certifier/examples/nginx/nginx_acme_srv.conf \
    && sed -i "s/80/8887/g" /var/www/acme2certifier/examples/nginx/nginx_acme_srv.conf \
    && cp /var/www/acme2certifier/examples/nginx/nginx_acme_srv.conf /etc/nginx/sites-available/acme_srv.conf \
    && ln -s /etc/nginx/sites-available/acme_srv.conf /etc/nginx/sites-enabled/acme_srv.conf \
    && sed -i "s/\/run\/uwsgi\/acme.sock/acme.sock/g" /var/www/acme2certifier/examples/nginx/acme2certifier.ini \
    && sed -i "s/nginx/www-data/g" /var/www/acme2certifier/examples/nginx/acme2certifier.ini \
    && echo "plugins = python3" >> /var/www/acme2certifier/examples/nginx/acme2certifier.ini \
    && cp /var/www/acme2certifier/examples/nginx/acme2certifier.ini /var/www/acme2certifier \
    && usermod -a -G ca www-data
COPY --chown=www-data:www-data files/acme_srv.cfg /var/www/acme2certifier/acme_srv/acme_srv.cfg

CMD ["/root/entrypoint.sh"]

EXPOSE 8888 8899
