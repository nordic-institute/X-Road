#!/bin/bash
# iptables port redirection for ports 80 and 443
# see also xroad-proxy.service unit file

if [ "$DISABLE_PORT_REDIRECT" != "false" ];
then
    exit 0
fi

# read HTTP and HTTPS port from x-road proxy config and local.ini
declare -A config;

read_config () {
    while IFS='=' read var val 
    do 
        if [[ $var == \[*] ]] 
        then 
            declare section=$var; 
            section=${section#[}; section=${section%]}
        elif [[ $val && ! $var == \;* ]]
        then 
            config["$section.$var"]=$val; 
        fi 
    done < $1
}

read_config "/etc/xroad/conf.d/proxy.ini"
read_config "/etc/xroad/conf.d/local.ini"

HTTP_PORT=${config[proxy.client-http-port]:-0}
HTTPS_PORT=${config[proxy.client-https-port]:-0}

IFACE_PARAM=""
if [ "$NETWORK_INTERFACE" != "" ]
then
    IFACE_PARAM="-i $NETWORK_INTERFACE"
fi

if [ "$1" == "enable" ]
then
    CMD="-I"
    POS=1
else
    CMD="-D"
    POS=
fi

if [ "$HTTPS_PORT" != "0" ]; then
    # redirect 443 to HTTPS_PORT
    iptables $CMD PREROUTING $POS -t nat $IFACE_PARAM -p tcp --dport 443 -j REDIRECT --to-port $HTTPS_PORT
    # mark traffic sent to the orginal port (can be dropped in filter table)
    iptables $CMD PREROUTING $POS -t mangle $IFACE_PARAM -p tcp --dport $HTTPS_PORT -j MARK --set-mark 456
fi

if [ "$HTTP_PORT" != "0" ]; then
    # redirect 80 to HTTP_PORT
    iptables $CMD PREROUTING $POS -t nat $IFACE_PARAM -p tcp --dport 80 -j REDIRECT --to-port $HTTP_PORT
    # mark traffic sent to HTTP_PORT (can be dropped in filter table)
    iptables $CMD PREROUTING $POS -t mangle $IFACE_PARAM -p tcp --dport $HTTP_PORT -j MARK --set-mark 456
fi

