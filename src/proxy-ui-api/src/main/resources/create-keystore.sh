openssl pkcs12 -export -in nginx.crt -inkey nginx.key -out nginx.p12
chown xroad:xroad nginx.p12
