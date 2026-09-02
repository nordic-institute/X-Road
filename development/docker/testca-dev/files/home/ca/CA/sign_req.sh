#!/bin/bash
if [ "$1" = "" ] || [ "$2" = "" ]
then
    echo Usage $0 [auth|sign] [certificate request file] [subjectAltName] >&2
    exit 1
fi

if [ "$1" == "sign" ]
then
    EXT=sign_ext
else
    EXT=auth_ext
fi

size=$(stat --printf="%s" "$2")
if [ $size -gt 10000 ]; then
    echo "Request too large" >&2
    exit 1
fi

if grep -q -- '--BEGIN CERTIFICATE REQUEST--' "$2"; then
    INFORM=PEM
else
    INFORM=DER
fi

while ! mkdir lock &>/dev/null; do
    sleep 1;
done
trap 'status=$?; rm -rf "lock"; exit $status' INT TERM EXIT

set -e
SER=$(cat serial)
openssl req -in $2 -inform $INFORM -out csr/${SER}.csr

function opensslCA() {
  openssl ca -batch -config CA.cnf \
             -extensions $EXT \
             -days 7300 \
             -notext \
             -md sha256 \
             -in csr/${SER}.csr \
             "$@"
}

if [ "$1" == "auth" ]; then
  # An explicit subjectAltName argument wins over whatever the CSR carries: CSRs from
  # the admin API's DS TLS flow are DN-only by design (the private key never leaves the
  # server), so the CA is the only place the SAN list can be attached.
  subjectAltName="${3:-}"
  if [ -z "$subjectAltName" ]; then
    # openssl ca ignores request extensions without copy_extensions; openssl req -text
    # prints IP entries as "IP Address:1.2.3.4" while openssl config syntax wants
    # "IP:1.2.3.4" — translate so re-injection through extfile parses.
    subjectAltName=$(openssl req -in csr/${SER}.csr -text -noout \
      | grep -A1 "Subject Alternative Name" | tail -n1 \
      | sed 's/^[ \t]*//' \
      | sed 's/IP Address:/IP:/g')
  fi
  if [ ! -z "$subjectAltName" ]; then
    extensionsOverride="
[ auth_ext ]
basicConstraints = CA:FALSE
keyUsage = critical, digitalSignature, keyEncipherment, dataEncipherment, keyAgreement
extendedKeyUsage = clientAuth, serverAuth
subjectAltName = ${subjectAltName}
"
  fi
fi

if [ ! -z "${extensionsOverride}" ]; then
  opensslCA -extfile <(echo "$extensionsOverride")
else
  opensslCA
fi

chmod 0664 index.txt
chmod 0664 serial
echo $SER>changed
set +e
exit 0

