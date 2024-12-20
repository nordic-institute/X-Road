#!/bin/bash

usage()
{
cat << EOF
usage: $0 -n <basename> -s "<certificate DN>" [-a "<subjectAltName>"|-f|-D "<dns1,dns2,dns3>"] [-d <path>] [-p] [-c]

generate ssl certificate.

OPTIONS:
   -h      Show this message
   -n      basename, like 'internal' or 'nginx'
   -d      working/output directory. default is /etc/xroad/ssl
   -f      fill subjectAltName automatically from hostname and IP addresses
   -S      fill Subject with /CN=`hostname -f` value
   -s      subject, optional. Format "/C=EE/O=Company/CN=server.name.tld"
   -a      subjectAltName, optional. Format "DNS:serverAlt.name.tld,IP:1.1.1.1,IP:2.2.2.2>"
   -D      comma-separated list of DNS names to add to subjectAltName
   -p      generate .p12 also, friendly name and password will default to basename value
   -c      configuration directory containing openssl.cnf (optional)
EOF
}

generate_openssl_conf() {
    cat << 'EOF'
default_md  = sha256

[ req ]
default_bits        = 2048
days                = 7300
distinguished_name  = req_distinguished_name
string_mask         = utf8only

[ req_distinguished_name ]
countryName         = Country Name (2 letter code)
countryName_min     = 2
countryName_max     = 2

stateOrProvinceName = State or Province Name (full name)

localityName        = Locality Name (eg, city)

0.organizationName  = Organization Name (eg, company)

organizationalUnitName  = Organizational Unit Name (eg, section)
#organizationalUnitName_default =

commonName          = Common Name (e.g. server FQDN or YOUR name)
commonName_max      = 64

[ tls ]
basicConstraints=critical,CA:TRUE,pathlen:0
keyUsage=nonRepudiation,keyEncipherment,digitalSignature,keyCertSign

[ tls_alt ]
basicConstraints=critical,CA:TRUE,pathlen:0
keyUsage=nonRepudiation,keyEncipherment,digitalSignature,keyCertSign
subjectAltName=$ENV::ALT

####################################################################
EOF
}

NAME=
SUBJECT=
ALT=
DNS_LIST=
FILL=
P12=
CONF_DIR=
OPENSSL_SUBJ=()
OPENSSL_EXT=
DIR=/etc/xroad/ssl
TEMP_CONF=

while getopts "hpd:fn:s:Sa:c:D:" OPTION
do
    case $OPTION in
        h)
            usage
            exit 1
            ;;
        n)
            NAME=$OPTARG
            ;;
        f)
            FILL=1
            ;;
        p)
            P12=1
            ;;
        d)
            DIR=$OPTARG
            ;;
        s)
            SUBJECT=$OPTARG
            ;;
        S)
            SUBJECT="/CN=$(hostname -f)"
            if (( ${#SUBJECT} > 68 )); then
                SUBJECT="/CN=$(hostname -s)"
            fi
            ;;
        a)
            ALT=$OPTARG
            ;;
        D)
            DNS_LIST=$OPTARG
            ;;
        c)
            CONF_DIR=$OPTARG
            ;;
        :)
            echo "Option -$OPTARG requires an argument."
            usage
            exit 1
            ;;
        ?)
            usage
            exit
            ;;
    esac
done

if [[ -z $NAME ]]; then
    usage
    exit 1
fi

# Handle DNS list
if [[ -n $DNS_LIST ]]; then
    DNS_ENTRIES=$(echo "$DNS_LIST" | tr ',' '\n' | sed 's/^/DNS:/' | tr '\n' ',' | sed 's/,$//')
    if [[ -n $ALT ]]; then
        ALT="${ALT},${DNS_ENTRIES}"
    else
        ALT="${DNS_ENTRIES}"
    fi
fi

if [[ -n $FILL ]]; then
    AUTO_ALT="IP:$(hostname -I),DNS:$(hostname -f),DNS:$(hostname -s)"
    if [[ -n $ALT ]]; then
        ALT="${ALT},${AUTO_ALT}"
    else
        ALT="${AUTO_ALT}"
    fi
fi

if [[ -n $ALT ]]; then
    OPENSSL_EXT=(-extensions tls_alt)
else
    OPENSSL_EXT=(-extensions tls)
fi
export ALT

if [[ -n $SUBJECT ]]; then
    OPENSSL_SUBJ=(-subj "${SUBJECT}")
fi

# Create temporary OpenSSL config if not provided
if [[ -z $CONF_DIR ]] || [[ ! -r ${CONF_DIR}/openssl.cnf ]]; then
    TEMP_CONF=$(mktemp)
    generate_openssl_conf > "$TEMP_CONF"
    CONF_DIR=$(dirname "$TEMP_CONF")
    TEMP_CONF_NAME=$(basename "$TEMP_CONF")
else
    TEMP_CONF_NAME="openssl.cnf"
fi

echo "Using subject: $SUBJECT ${OPENSSL_SUBJ[*]}"
if [[ -n $ALT ]]; then
    echo "Using altNames: $ALT"
fi

openssl req -new -x509 -days 7300 -nodes \
    -out "${DIR}/${NAME}.crt" \
    -keyout "${DIR}/${NAME}.key" \
    -config "${CONF_DIR}/${TEMP_CONF_NAME}" \
    "${OPENSSL_SUBJ[@]}" \
    "${OPENSSL_EXT[@]}"

RESULT=$?

# Clean up temporary config if created
if [[ -n $TEMP_CONF ]]; then
    rm -f "$TEMP_CONF"
fi

if [[ $RESULT != 0 ]]; then
    exit 10
fi

if [[ -n $P12 ]]; then
    openssl pkcs12 -export \
        -in "${DIR}/${NAME}.crt" \
        -inkey "${DIR}/${NAME}.key" \
        -name "${NAME}" \
        -out "${DIR}/${NAME}.p12" \
        -passout pass:"${NAME}"
    if [[ $? != 0 ]]; then
        exit 10
    fi
fi

chmod -f 660 "${DIR}"/"${NAME}".*
chown -f xroad:xroad "${DIR}"/"${NAME}".*
