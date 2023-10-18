echo "----------------------------------------------------------"
echo "       Generating test private keys and certs for gRPC    "
echo "----------------------------------------------------------"

readonly KEYTOOL=$(which keytool)
if [[ ! ${KEYTOOL} ]]
then
    echo "keytool is not installed. Exiting !"
    exit 1
fi

echo "Generating keystore for grpc-internal.........."
${KEYTOOL} -genkeypair -alias grpc-internal \
        -storetype PKCS12 \
        -keyalg EC -groupname secp256r1 \
        -sigalg SHA256withECDSA \
        -keystore grpc-internal-keystore.p12 \
        -dname "CN=127.0.0.1" \
        -ext "SAN:c=DNS:localhost,IP:127.0.0.1,DNS:host.docker.internal" \
        -validity 3650 \
        -storepass 111111 \
        -keypass 111111
