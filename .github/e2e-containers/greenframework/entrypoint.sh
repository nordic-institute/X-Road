#!/bin/sh

# This is required because otherwise we get an error regarding double extensions,
# unfortunately hurl does not currently support setting a filename with its
# MultipartFormData request. Manual multipart is not possible because it would
# require inlining the certificates in advance but these are generated when the
# test CA is initialised.

cp /hurl-files/ca/CA/certs/ca.cert.pem /hurl-files/ca/CA/certs/ca.pem || :
cp /hurl-files/ca/CA/certs/tsa.cert.pem /hurl-files/ca/CA/certs/tsa.pem || :
cp /hurl-files/ca/CA/certs/ocsp.cert.pem /hurl-files/ca/CA/certs/ocsp.pem || :
hurl "$@"
