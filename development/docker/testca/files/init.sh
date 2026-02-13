#!/bin/bash
if [ -f .init ]; then
    echo "Already initialized? Remove .init to reset"
    exit 1
fi

# dn parameters
ORGANIZATION_NAME=${ORGANIZATION_NAME: -"Test"}
DN_CA_O="$ORGANIZATION_NAME"
DN_CA_CN="$ORGANIZATION_NAME CA"
DN_OCSP_O="$ORGANIZATION_NAME"
DN_OCSP_CN="$ORGANIZATION_NAME OCSP"
DN_TSA_O="$ORGANIZATION_NAME"
DN_TSA_CN="$ORGANIZATION_NAME TSA"

rm -rf private certs newcerts crl csr
rm -f index.* serial
echo 01 > serial
mkdir -p private certs newcerts crl csr
touch index.txt index.txt.attr
set -e
openssl rand -hex -out pw -writerand private/.rnd 16
echo "Generating CA Certificate"
openssl genrsa -aes256 -passout file:pw -out private/tmp.key.pem 4096
openssl rsa -in private/tmp.key.pem -passin file:pw -out private/ca.key.pem
chmod g+r private/ca.key.pem
openssl req -batch -config CA.cnf -subj "/O=$DN_CA_O/CN=$DN_CA_CN" -key private/ca.key.pem -new -x509 -days 7300 -sha256 -extensions v3_ca -out certs/ca.cert.pem

echo "Generating OCSP Certificate for CA"
openssl genrsa -aes256 -passout file:pw -out private/tmp.key.pem 4096
openssl rsa -in private/tmp.key.pem -passin file:pw -out private/ocsp.key.pem
chmod g+r private/ocsp.key.pem
openssl req -batch -config CA.cnf -subj "/O=$DN_OCSP_O/CN=$DN_OCSP_CN" -key private/ocsp.key.pem -new -sha256 -out csr/ocsp.csr.pem
openssl ca -batch -config CA.cnf -name CA_default -extensions ocsp -days 7300 -notext -md sha256 -in csr/ocsp.csr.pem -out certs/ocsp.cert.pem

echo "Generating TSA Certificate"
openssl genrsa -aes256 -passout file:pw -out private/tmp.key.pem 4096
openssl rsa -in private/tmp.key.pem -passin file:pw -out private/tsa.key.pem
chmod g+r private/tsa.key.pem
openssl req -batch -config CA.cnf -subj "/O=$DN_TSA_O/CN=$DN_TSA_CN" -key private/tsa.key.pem -new -sha256 -out csr/tsa.csr.pem
openssl ca -batch -config CA.cnf -name CA_default -extensions tsa_ext -days 7300 -notext -md sha256 -in csr/tsa.csr.pem -out certs/tsa.cert.pem

touch .init
rm pw
exit 0
