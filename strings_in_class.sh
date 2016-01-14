#!/bin/bash

if [ "$1" == "" ]
	then
	echo "strings_in_class FQCN <-cp classpath>"
	exit 1
fi

classpath=

if [ "$2" == "-cp" ]
	then
	classpath="-classpath $3"
fi

javap $classpath -c -v  $1 | grep "\#[0-9]*\s=\sString" | grep -o '\/\/.*'
