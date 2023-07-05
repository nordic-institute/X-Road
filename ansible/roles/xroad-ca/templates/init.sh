#!/bin/bash
if [ -f .init ]; then
    echo "Already initialized? Remove .init to reset"
    exit 1
fi

# dn parameters
DN_CA_O="{{ xroad_ca_o }}"
DN_CA_CN="{{ xroad_ca_cn }}"
DN_OCSP_O="{{ xroad_ca_ocsp_o }}"
DN_OCSP_CN="{{ xroad_ca_ocsp_cn }}"
DN_TSA_O="{{ xroad_ca_tsa_o }}"
DN_TSA_CN="{{ xroad_ca_tsa_cn }}"

rm -rf private certs newcerts crl csr
rm -f index.* serial
echo 01 > serial
mkdir -p private certs newcerts crl csr
touch index.txt index.txt.attr
set -e

echo "Generating CA Certificate"
openssl genrsa -out private/ca.key.pem 4096
chmod g+r private/ca.key.pem
openssl req -batch -config CA.cnf -subj "/O=$DN_CA_O/CN=$DN_CA_CN" -key private/ca.key.pem -new -x509 -days 7300 -sha256 -extensions v3_ca -out certs/ca.cert.pem

echo "Generating OCSP Certificate for CA"
openssl genrsa -out private/ocsp.key.pem 4096
chmod g+r private/ocsp.key.pem
openssl req -batch -config CA.cnf -subj "/O=$DN_OCSP_O/CN=$DN_OCSP_CN" -key private/ocsp.key.pem -new -sha256 -out csr/ocsp.csr.pem
openssl ca -batch -config CA.cnf -name CA_default -extensions ocsp -days 7300 -notext -md sha256 -in csr/ocsp.csr.pem -out certs/ocsp.cert.pem

echo "Generating TSA Certificate"
openssl genrsa -out private/tsa.key.pem 4096
chmod g+r private/tsa.key.pem
openssl req -batch -config CA.cnf -subj "/O=$DN_TSA_O/CN=$DN_TSA_CN" -key private/tsa.key.pem -new -sha256 -out csr/tsa.csr.pem
openssl ca -batch -config CA.cnf -name CA_default -extensions tsa_ext -days 7300 -notext -md sha256 -in csr/tsa.csr.pem -out certs/tsa.cert.pem

touch .init
exit 0
