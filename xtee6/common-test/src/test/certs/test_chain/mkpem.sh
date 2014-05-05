openssl x509 -req -days 365 -in $1.csr -CA $2.pem -CAkey $2.key -set_serial 01 -out $1.pem
