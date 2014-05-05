#!/bin/bash

# This script can be called as a cron job to distribute files.
# Example crontab entry: 
#   * * * * * cd /path/to/filedistributor; ./filedistributor.sh
curl http://localhost:8080/center-service/dist_files
