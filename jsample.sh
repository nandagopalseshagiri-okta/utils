#!/bin/bash

#total=("0")

function checkInterrupt() {
    read -t $1 -n 1 interrupt
    if [ "$interrupt" != "" ]; then
        return 1
    fi
    return 0;
}

# if [ "$1" == "" ]; then
#     echo "$1"
#     echo "Please provide the pid of the process"
#     exit 1
# fi

function periodicRun() {
	breaker=0
    while true; do
    	local t=$1
        checkInterrupt $t
        if [ $? -ne 0 -o $breaker -ne 0 ]; then
            break;
        fi

        $2
    done
}

function waitForProcess() {
	breaker=$(ps | grep [s]urefirebooter | grep -v "\/bin\/sh\s" | cut -d ' ' -f 1)
	re='^[0-9]+$'
	if ! [[ $breaker =~ $re ]] ; then
		breaker=0;   
	fi
}

periodicRun 1 "waitForProcess"

periodicRun 1 "jstack $breaker"
