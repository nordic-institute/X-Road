openssl ocsp -index index.txt -port 8888 -rsigner certs/ocsp.cert.pem -rkey private/ocsp.key.pem -CA certs/ca.cert.pem -text -out log.txt
