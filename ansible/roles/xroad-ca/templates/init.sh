#!/bin/bash
if [ -f .init ]; then
    echo "Already initialized? Remove .init to reset"
    exit 1
fi

# dn parameters
DN_COUNTRY="{{ xroad_ca_dn_country }}"
DN_CA_O="{{ xroad_ca_o }}"
DN_CA_OU="{{ xroad_ca_ou }}"
DN_CA_CN="{{ xroad_ca_cn }}"
DN_OCSP_O="{{ xroad_ca_ocsp_o }}"
DN_OCSP_OU="{{ xroad_ca_ocsp_ou }}"
DN_OCSP_CN="{{ xroad_ca_ocsp_cn }}"
DN_TSA_O="{{ xroad_ca_tsa_o }}"
DN_TSA_OU="{{ xroad_ca_tsa_ou }}"
DN_TSA_CN="{{ xroad_ca_tsa_cn }}"

rm -rf private certs newcerts crl csr
rm index.* serial
touch index.txt
echo 01 > serial
mkdir -p private certs newcerts crl csr
set -e
echo "temppw" > pw
echo "Generating CA Certificate"
openssl genrsa -aes256 -passout file:pw -out private/tmp.key.pem 4096
openssl rsa -in private/tmp.key.pem -passin file:pw -out private/ca.key.pem
chmod g+r private/ca.key.pem
openssl req -batch -config CA.cnf -subj "/C=$DN_COUNTRY/O=$DN_CA_O/OU=$DN_CA_OU/CN=$DN_CA_CN" -key private/ca.key.pem -new -x509 -days 7300 -sha256 -extensions v3_ca -out certs/ca.cert.pem

echo "Generating OCSP Certificate for CA"
openssl genrsa -aes256 -passout file:pw -out private/tmp.key.pem 4096
openssl rsa -in private/tmp.key.pem -passin file:pw -out private/ocsp.key.pem
chmod g+r private/ocsp.key.pem
openssl req -batch -config CA.cnf -subj "/C=$DN_COUNTRY/O=$DN_OCSP_O/OU=$DN_OCSP_OU/CN=$DN_OCSP_CN" -key private/ocsp.key.pem -new -sha256 -out csr/ocsp.csr.pem
openssl ca -batch -config CA.cnf -name CA_default -extensions ocsp -days 7300 -notext -md sha256 -in csr/ocsp.csr.pem -out certs/ocsp.cert.pem

echo "Generating TSA Certificate"
openssl genrsa -aes256 -passout file:pw -out private/tmp.key.pem 4096
openssl rsa -in private/tmp.key.pem -passin file:pw -out private/tsa.key.pem
chmod g+r private/tsa.key.pem
openssl req -batch -config CA.cnf -subj "/C=$DN_COUNTRY/O=$DN_TSA_O/OU=$DN_TSA_OU/CN=$DN_TSA_CN" -key private/tsa.key.pem -new -sha256 -out csr/tsa.csr.pem
openssl ca -batch -config CA.cnf -name CA_default -extensions tsa_ext -days 7300 -notext -md sha256 -in csr/tsa.csr.pem -out certs/tsa.cert.pem

touch .init
rm pw
exit 0
