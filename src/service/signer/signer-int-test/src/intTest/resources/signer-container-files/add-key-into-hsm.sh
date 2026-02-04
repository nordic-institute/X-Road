#!/bin/bash

cd /tmp
openssl genrsa -out key.pem 2048
openssl req -new -x509 -key key.pem -out cert.pem -subj "/CN=example.com" -days 365
openssl pkcs8 -topk8 -inform PEM -outform DER -in key.pem -out key.der -nocrypt
openssl x509 -in cert.pem -outform DER -out cert.der
openssl rsa -in key.pem -pubout -outform DER -out public.der
pkcs11-tool --module /usr/lib/softhsm/libsofthsm2.so --token x-road-softhsm2 --pin 1234 --write-object key.der --type privkey --label "outsideGeneratedKey" --id $1
pkcs11-tool --module /usr/lib/softhsm/libsofthsm2.so --token x-road-softhsm2 --pin 1234 --write-object public.der --type pubkey --label "outsideGeneratedKey" --id $1
pkcs11-tool --module /usr/lib/softhsm/libsofthsm2.so --token x-road-softhsm2 --pin 1234 --write-object cert.der --type cert --label "outsideGeneratedCert" --id $1
