#!/bin/bash

for i in {1..2000}
do
    curl -raw -v http://iks2-v5-test1.cyber.ee:8080/cgi-bin/consumer_proxy --data-binary @request_to_iks2-v5-test1.xml -H "content-type: text/xml;"
done
