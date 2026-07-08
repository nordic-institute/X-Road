#!/bin/bash
# noop script

usage()
{
cat << EOF
usage: $0 -n <basename> -s "<certificate DN>" [-a "<subjectAltName>"|-f] [-d <path>] [-p] [-c]

generate ssl certificate.

OPTIONS:
   -h      Show this message
   -n      basename, like 'internal' or 'nginx'
   -d      working/output directory. default is /etc/xroad/ssl
   -f      fill subjectAltName automatically from hostname and IP addresses
   -S      fill Subject with /CN=`hostname -f` value
   -s      subject, optional. Format "/C=EE/O=Company/CN=server.name.tld"
   -a      subjectAltName, optional. Format "DNS:serverAlt.name.tld,IP:1.1.1.1,IP:2.2.2.2>"
   -p      generate .p12 also, friendly name and password will default to basename value
   -c      configuration directory containing openssl.cnf
EOF
}

NAME=
SUBJECT=
ALT=
FILL=
P12=
CONF_DIR=
OPENSSL_SUBJ=()
OPENSSL_EXT=
DIR=/etc/xroad/ssl


while getopts "hpd:fn:s:Sa:c:" OPTION
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



