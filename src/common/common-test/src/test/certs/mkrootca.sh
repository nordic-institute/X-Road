openssl req -new -newkey rsa:2048 -nodes -out $1.csr -keyout $1.key -config openssl.cnf
openssl x509 -trustout -signkey $1.key -days 3650 -req -in $1.csr -out $1.pem -extfile openssl.cnf -extensions v3_root_ca
./mkp12.sh $1
