#!/bin/bash
# Script based on https://gist.github.com/tinogomes/447191

# Set environment variables
SOAPUI_HOME=/home/jenkins/SoapUI-5.3.0/
XROAD_MOCK_PATH=/home/jenkins/X-Road-tests/common/xrd-automated-tests/mock/mock_service
COMMAND="${SOAPUI_HOME}bin/mockservicerunner.sh -s soapui-settings.xml testservice-soapui-project.xml"

# Set working directory
PWD=$(pwd)

# Set pid and logfile
PIDFILE=/var/run/mock-service.pid
LOGFILE=/var/log/mock-service.log

# Don't edit any further
PIDFILE_PATH=$PIDFILE
LOGFILE_PATH=$LOGFILE

# Get the status of the service
status() {
	# Check if pid file exists
	if [ -f $PIDFILE_PATH ]
	then
		# Check for stale pidfile (see if the process is really running)
		if ps -p $(cat $PIDFILE_PATH) > /dev/null 2>&1
		then
			# Return information about the process
			echo "pid: $(cat $PIDFILE_PATH) [$PIDFILE_PATH]"
			ps -ef | grep "\s$(cat $PIDFILE_PATH)\s"
		else
			echo "Stale pidfile found, removing. Service not running."
			# Remove pidfile
			rm $PIDFILE_PATH
		fi
	else
		echo "pidfile not found"
	fi
}

# Start the mock service
start() {
	SERVICE_RUNNING=0
	# Check if pid file exists
	if [ -f $PIDFILE_PATH ]
	then
		# Check for stale pidfile (see if the process is really running)
		if ps -p $(cat $PIDFILE_PATH) > /dev/null 2>&1
		then
			# Service is running
			SERVICE_RUNNING=1
		else
			echo "Stale pidfile found, ignoring."
		fi
	fi

	# Do not re-run the service if it's already running
	if [ $SERVICE_RUNNING -eq 1 ];
	then
		echo "Mock service already running. PID: [$(cat $PIDFILE_PATH)]"
	else
		# Creathe the pidfile
		touch $PIDFILE_PATH
		
		# Execute command in the background with nohup
		if cd ${XROAD_MOCK_PATH}; nohup $COMMAND >>$LOGFILE_PATH 2>&1 &
		then
			# Save the pid to pidfile
			echo $! > $PIDFILE_PATH
			echo "Mock service started. PID: $!"
			echo "$(date '+%Y-%m-%d %X'): Mock service started" >>$LOGFILE_PATH
		else
			# Service start failed, remove pidfile
			echo "Failed to start mock service."
			rm $PIDFILE_PATH
		fi
	fi
}

# Stop service
stop() {
	# Check if pidfile exists
	if [ -f $PIDFILE_PATH ]
	then
		# Check if process itself exists
		if ps -p $(cat $PIDFILE_PATH) > /dev/null ;
		then
			# Kill the process using the pid from pidfile
			if kill -- -$(ps -o pgid= $(cat $PIDFILE_PATH) | grep -o [0-9]*) ;
			then
				# Killing successful
				echo "Mock service stopped."
				echo "$(date '+%Y-%m-%d %X'): Mock service stopped" >>$LOGFILE_PATH
			fi
		else
			echo "Stale pidfile found, removing. Service not running."
		fi
		
		# Remove pidfile
		rm $PIDFILE_PATH
	else
		echo "pidfile not found, mock service was not running"
	fi
}

# Check command line parameters and get the function to run
case "$1" in
    'start')
            start
            ;;
    'stop')
            stop
            ;;
    'restart')
            stop
			echo "Restarting mock service"
			# Don't start right away
			sleep 2
            start
            ;;
    'status')
            status
            ;;
    *)
            echo "Usage: $0 { start | stop | restart | status }"
            exit 1
            ;;
esac

exit 0
