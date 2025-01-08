#!/bin/bash
if [ -f .init_acme ]; then
    echo "Acme already initialized? Remove .init_acme to reset"
    exit 1
fi

# acme ver
A2C_VER="{{ a2c_ver }}"

set -e

echo "Setting up ACME"
apt -y install /tmp/acme2certifier_$A2C_VER-1_all.deb
sed -i "s/run\/uwsgi\/acme.sock/var\/www\/acme2certifier\/acme.sock/g" /var/www/acme2certifier/examples/nginx/nginx_acme_srv.conf
sed -i "s/80/8887/g" /var/www/acme2certifier/examples/nginx/nginx_acme_srv.conf
cp /var/www/acme2certifier/examples/nginx/nginx_acme_srv.conf /etc/nginx/sites-available/acme_srv.conf
ln -s /etc/nginx/sites-available/acme_srv.conf /etc/nginx/sites-enabled/acme_srv.conf
cp /var/www/acme2certifier/examples/nginx/acme2certifier.ini /var/www/acme2certifier
sed -i "s/\/run\/uwsgi\/acme.sock/acme.sock/g" /var/www/acme2certifier/acme2certifier.ini
sed -i "s/nginx/www-data/g" /var/www/acme2certifier/acme2certifier.ini
echo "plugins = python3" >> /var/www/acme2certifier/acme2certifier.ini
sed -i "4i import base64" /var/www/acme2certifier/acme_srv/renewalinfo.py
sed -i s/b64_decode\(self.logger,\ /base64.b64decode\(/g /var/www/acme2certifier/acme_srv/renewalinfo.py
usermod -a -G ca www-data

touch .init_acme
exit 0
