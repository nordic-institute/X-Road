openssl req -newkey rsa:2048 -sha1 -keyout $1.key -out $1.csr -config openssl.cnf -nodes
openssl x509 -req -in $1.csr -sha1 -extfile openssl.cnf -extensions v3_ca -CA $2.pem -CAkey $2.key -set_serial 03 -out $1.pem -days 3650
./mkp12.sh $1
