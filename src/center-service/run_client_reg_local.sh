#!/bin/bash
curl -raw -v http://localhost:3333/ --data-binary @$XROAD_HOME/center-service/test/resources/client_reg_request_LOCAL.soap -H "Content-Type: text/xml; charset=UTF-8"
