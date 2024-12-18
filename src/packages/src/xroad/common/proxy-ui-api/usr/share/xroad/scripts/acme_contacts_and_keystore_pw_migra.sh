#!/bin/bash

echo "Migrating contact information from acme.yml to mail.yml"

awk '/^contacts:/ {contacts=1} {
     if (/^ *#/) {
           commentbuf = commentbuf $0 ORS;
       } else if (/^eab-credentials:/ || !contacts) {
           contacts=0;
           commentbuf="";
       } else if (!/^ *#/) {
           printf "%s", commentbuf;
           commentbuf="";
       }
     } !/^ *#/ && contacts' /etc/xroad/conf.d/acme.yml >> /etc/xroad/conf.d/mail.yml

if test -f /etc/xroad/ssl/acme.p12; then
    echo "Generating new stronger password for ACME account keystore"

    deststorepass=$(head -c 24 /dev/urandom | base64 | tr "/+" "_-")
    echo "account-keystore-password: $deststorepass" >>/etc/xroad/conf.d/acme.yml

    awk '/^contacts:/{contacts=1;next} /^eab-credentials:/{contacts=0}
    /'\'':/ && contacts {print substr($0, index($0, "'\''")+1, index($0, "'\'':")-4)}
    /\":/ && contacts {print substr($0, index($0, "\"")+1, index($0, "\":")-4)}' /etc/xroad/conf.d/acme.yml > /etc/xroad/conf.d/temp_member_codes.txt

    while read membercode; do
        if keytool -list -keystore /etc/xroad/ssl/acme.p12 -alias "$membercode" | grep -q "PrivateKeyEntry"; then
            echo | keytool -importkeystore -srckeystore /etc/xroad/ssl/acme.p12 -srcstoretype PKCS12 -destkeystore /etc/xroad/ssl/acme_tmp.p12 -deststoretype PKCS12 -deststorepass "$deststorepass" -srcalias "$membercode" -srckeypass "$membercode"
        fi
    done </etc/xroad/conf.d/temp_member_codes.txt
    rm /etc/xroad/conf.d/temp_member_codes.txt
    rm /etc/xroad/ssl/acme.p12
    mv /etc/xroad/ssl/acme_tmp.p12 /etc/xroad/ssl/acme.p12
fi
