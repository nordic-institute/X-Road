#!/bin/bash

# This script can be called as a cron job to generate a signed GlobalConf.
# Example crontab entry:
#   * * * * * cd /path/to/globalconf-signer; ./signer.sh
curl http://localhost:8080/center-service/gen_conf
