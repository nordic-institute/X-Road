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
${KEYTOOL} -genkey -alias grpc-internal -keyalg RSA -keysize 2048 \
        -storetype PKCS12 \
        -keystore grpc-internal-keystore.jks \
        -dname "CN=127.0.0.1" \
        -ext "SAN:c=DNS:localhost,IP:127.0.0.1" \
        -validity 9999 \
        -storepass 111111 \
        -keypass 111111
