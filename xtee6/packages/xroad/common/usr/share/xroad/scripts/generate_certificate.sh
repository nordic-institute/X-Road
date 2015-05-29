#!/bin/bash


usage()
{
cat << EOF
usage: $0 -n intenal -s "<certificate DN>" [-a "<subjectAltName>"|-f] [-d <path>] [-p] [-c]

generate ssl certificate.

OPTIONS:
   -h      Show this message
   -n      basename, like 'internal' 
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


while getopts “hpd:fn:s:Sa:c:” OPTION
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
	SUBJECT="/CN=`hostname -f`"
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

if [[ -z $NAME ]]
then
   usage
   exit 1
fi

if [[ -z $CONF_DIR ]]
then
   CONF_DIR=$DIR
fi

if [[ ! -r ${CONF_DIR}/openssl.cnf ]]
then
   echo "cannot read $DIR/openssl.cnf"
   usage
   exit 1
fi


if [[ -n $FILL ]]
then
  LIST=
  for i in `ip addr | grep 'scope global' | tr '/' ' ' | awk '{print $2}'`; do LIST+="IP:$i,"; done;
  ALT=${LIST}DNS:`hostname`,DNS:`hostname -f`
fi

if [[ -n $ALT ]]
then
  OPENSSL_EXT="-extensions ${NAME}_alt"
else
  OPENSSL_EXT="-extensions ${NAME}"
fi
export ALT

if [[ -n $SUBJECT ]]
then
  OPENSSL_SUBJ=(-subj "${SUBJECT}")
fi

echo $SUBJECT  $OPENSSL_SUBJ


openssl req -new -x509 -days 7300 -nodes -out ${DIR}/${NAME}.crt -keyout ${DIR}/${NAME}.key  -config ${CONF_DIR}/openssl.cnf  "${OPENSSL_SUBJ[@]}"  ${OPENSSL_EXT}

if [[ "$?" != 0 ]]
then
  exit 10
fi


if [[ -n $P12 ]]
then
   openssl pkcs12 -export -in ${DIR}/${NAME}.crt -inkey ${DIR}/${NAME}.key -name "${NAME}" -out ${DIR}/${NAME}.p12 -passout pass:${NAME}
   if [[ "$?" != 0 ]]
   then
      exit 10
   fi

fi

chmod -f 660 ${DIR}/${NAME}.*
chown -f xroad:xroad ${DIR}/${NAME}.*
