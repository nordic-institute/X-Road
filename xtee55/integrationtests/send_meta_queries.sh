#!/bin/bash

function usage {
  echo "usage: $0 <queries directory> [hostname]"
  exit 1
}



if [ ! $1 ]; then
  usage
fi

if [ ! -d $1 ]; then
  echo "'$1' is not directory"
  usage
fi

if [ ! $2 ]; then
  echo $'\nNo host specified. Using default iks2-fed7\n'
  host="iks2-fed7"
else
  host=$2
fi

for query_file in $1/*
do
  echo "Sendind $query_file:"
  curl -d@$query_file -H"Content-Type: text/xml; charset=utf-8" http://$host/cgi-bin/consumer_proxy
  echo $'\n'
done

