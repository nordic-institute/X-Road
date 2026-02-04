#!/bin/bash
# Initialize TEST-CA if it has not been initialized yet and the directory exists
if [ -d /home/ca ] && [ ! -f /home/ca/CA/.init ]; then
    logger "Initializing TEST-CA..."
    su ca -c 'cd /home/ca/CA && ./init.sh'

    # Copy the generated certificates to the /home/ca/certs directory for use in HURL

    if [ ! -d /home/ca/certs ]; then
        mkdir -p /home/ca/certs
    fi
    
    cp /home/ca/CA/certs/ca.cert.pem /home/ca/certs/ca.pem
    cp /home/ca/CA/certs/ocsp.cert.pem /home/ca/certs/ocsp.pem
    cp /home/ca/CA/certs/tsa.cert.pem /home/ca/certs/tsa.pem
fi

exec /usr/bin/supervisord -n -c /etc/supervisor/supervisord.conf
