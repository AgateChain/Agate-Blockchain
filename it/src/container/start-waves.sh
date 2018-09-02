#!/bin/bash

<<<<<<< HEAD
echo Options: $Agate_OPTS
java $Agate_OPTS -jar /opt/Agate/Agate.jar /opt/Agate/template.conf
=======
trap 'kill -TERM $PID' TERM INT
echo Options: $WAVES_OPTS
java $WAVES_OPTS -jar /opt/waves/waves.jar /opt/waves/template.conf &
PID=$!
wait $PID
trap - TERM INT
wait $PID
EXIT_STATUS=$?
>>>>>>> 4f3106f04982d02459cdc4705ed835b976d02dd9
