#!/bin/bash

trap 'kill -TERM $PID' TERM INT
echo Options: $WAVES_OPTS
java $Agate_OPTS -jar /opt/Agate/Agate.jar /opt/Agate/template.conf &
PID=$!
wait $PID
trap - TERM INT
wait $PID
EXIT_STATUS=$?
