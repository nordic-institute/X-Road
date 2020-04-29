#!/bin/bash
sed -i 's;exec /usr/bin/supervisord -n -c /etc/supervisor/supervisord.conf; ;g' /root/entrypoint.sh
source root/entrypoint.sh  
apt-get install xroad-addon-messagelog
exec /usr/bin/supervisord -n -c /etc/supervisor/supervisord.conf