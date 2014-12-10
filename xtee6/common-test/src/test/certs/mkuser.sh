./mkkey.sh $1
openssl req -new -key $1.key -out $1.csr -config openssl.cnf
openssl x509 -req -days 3650 -in $1.csr -CA $2.pem -CAkey $2.key -set_serial 04 -out $1.pem -extfile openssl.cnf -extensions usr
./mkp12.sh $1
