#!/bin/bash

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

HTTP_PORT=${config[proxy.client-http-port]}
HTTPS_PORT=${config[proxy.client-https-port]}

if [ "$1" == "enable" ]
then
    CMD="-I"
    POS=1
else
    CMD="-D"
    POS=
fi

iptables $CMD PREROUTING $POS -t nat -i eth0 -p tcp --dport 443 -j REDIRECT --to-port $HTTPS_PORT
iptables $CMD PREROUTING $POS -t mangle -p tcp --dport $HTTPS_PORT -j MARK --set-mark 456
iptables $CMD PREROUTING $POS -t nat -i eth0 -p tcp --dport 80 -j REDIRECT --to-port $HTTP_PORT
iptables $CMD PREROUTING $POS -t mangle -p tcp --dport $HTTP_PORT -j MARK --set-mark 456
iptables $CMD INPUT $POS -m mark --mark 456 -j DROP

