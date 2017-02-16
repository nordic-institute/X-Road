#!/bin/bash


usage()
{
cat << EOF
Usage: $0 [-s "<certificate DN>"|-S] [-a "<subjectAltName>"|-f]

Generate operational monitoring daemon TLS certificate.

OPTIONS:
   -h      Show this message
   -f      fill subjectAltName automatically from hostname and IP addresses
   -S      fill Subject with /CN=`hostname -f` value
   -s      subject, optional. Format "/C=EE/O=Company/CN=server.name.tld"
   -a      subjectAltName, optional. Format "DNS:serverAlt.name.tld,IP:1.1.1.1,IP:2.2.2.2>"
EOF
}

NAME=opmonitor
DIR=/etc/xroad/ssl
ARGS="-n "$NAME" -p"
XROAD_SCRIPT_LOCATION=/usr/share/xroad/scripts

while getopts “hfSs:a:” OPTION
do
    case $OPTION in
      h)
    usage
    exit 1
    ;;
      s|a)
    ARGS=$ARGS" "-$OPTION" "$OPTARG
    ;;
      f|S)
    ARGS=$ARGS" "-$OPTION
    ;;
      ?)
    usage
    exit 1
    ;;
   esac
done

FILE=$DIR"/"$NAME".p12"
SUCCESS_MSG="Success! Output written to '$FILE'."

if [ -f $FILE ];
then
    read -p "Do you wish to replace the existing TLS key with the generated key? [y/N]" -n 1 -r
    echo
    if ! [[ $REPLY =~ ^[Yy]$ ]]
    then
        SUCCESS_MSG="Success! Output written to '$HOME/$NAME.p12'. Copy this file to '$DIR'."
        DIR=$HOME
    fi
fi

ARGS=$ARGS" -d $DIR -c /etc/xroad/ssl"
set -- $ARGS
$XROAD_SCRIPT_LOCATION/generate_certificate.sh $@

if [ $? -eq 0 ]; then
    echo $SUCCESS_MSG
fi

