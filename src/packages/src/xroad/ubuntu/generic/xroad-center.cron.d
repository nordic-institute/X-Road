#!/bin/bash

# global configuration generation
* * * * * xroad curl http://127.0.0.1:8084/managementservice/gen_conf  2>&1 >/dev/null;

# automatic backups once a day
15 3 * * * xroad /usr/share/xroad/scripts/autobackup_xroad_center_configuration.sh

# backup retention policy, delete backups older that 30 days
10 * * * * xroad find /var/lib/xroad/backup -type f -name "cs-automatic-backup*.tar" -mtime 30 -delete
