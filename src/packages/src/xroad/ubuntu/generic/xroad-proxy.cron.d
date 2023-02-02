#!/bin/bash

# backup retention policy, delete backups older that 30 days
10 * * * * xroad find /var/lib/xroad/backup -type f -name "ss-automatic-backup*.gpg" -mtime 30 -delete
