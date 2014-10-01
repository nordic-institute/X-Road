#!/bin/sh

MONITOR_AGENT_SERVICE="xroad-monitor"
ADMIN_PORT_PROPERTY="ee.cyber.sdsb.proxyMonitorAgent.adminPort="

PID=`/sbin/status $MONITOR_AGENT_SERVICE | awk '/running, process/{print $4}'`

if [ $PID ]; then
  PORT=`ps -p $PID -o cmd | grep -o "$ADMIN_PORT_PROPERTY\w*" | \
      awk '{ split($0, a, "="); print a[2] }'`

  if [ $PORT ]; then
	curl localhost:$PORT/reload
  else
    echo "ERROR: can not determine monitor agent admin port"
  fi
else
  echo "Cannot reload, monitor agent is not running"
fi

