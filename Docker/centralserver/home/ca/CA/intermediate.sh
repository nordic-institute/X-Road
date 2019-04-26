#!/bin/bash
intermediate() {
    echo $1
name=$1
    echo "Generating Intermediate CA $name"
    openssl genrsa -aes256 -passout file:pw -out private/tmp.key.pem 4096
    openssl rsa -in private/tmp.key.pem -passin file:pw -out private/$name.key.pem
    openssl req -config CA.cnf -key private/$name.key.pem -new -sha256 -out csr/$name.csr.pem
    openssl ca -config CA.cnf -name CA_default -extensions v3_intermediate_ca -days 7300 -notext -md sha256 -in csr/$name.csr.pem -out certs/$name.cert.pem

    mkdir -p $name
    cd $name
    rm -rf private certs newcerts crl csr
    rm index.* serial
    touch index.txt
    echo 01 > serial
    mkdir -p private certs newcerts crl csr
    cd ..

    echo "Generating OCSP Certificate for Intermediate CA"
    openssl genrsa -aes256 -passout file:pw -out $name/private/tmp.key.pem 4096
    openssl rsa -in $name/private/tmp.key.pem -passin file:pw -out $name/private/ocsp.key.pem
    openssl req -config CA.cnf -key $name/private/ocsp.key.pem -new -sha256 -out $name/csr/ocsp.csr.pem
    env INTERMEDIATE=$name openssl ca -config CA.cnf -name CA_intermediate -extensions ocsp -days 7300 -notext -md sha256 -in $name/csr/ocsp.csr.pem -out $name/certs/ocsp.cert.pem
}
[ "$1" == "" ] && exit 1
[ -e $1 ] && exit 1

echo "temppw" > pw
intermediate $1
rm pw
exit 0

