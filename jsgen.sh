#!/bin/bash

if [ "$1" == "" ]
	then
	echo "No input file was given"
	exit 1
fi

if [ ! -e $1 ]
	then
	echo "The input file does not exist"
	exit 1
fi

cfile=$(mktemp -t jsgen)

echo "#define public" >> $cfile
echo "#define final" >> $cfile
echo "#define class " >> $cfile
echo "#define static" >> $cfile
echo "#define String this." >> $cfile
echo "#define int this." >> $cfile
echo "#define ${1%.*} (new function() " >> $cfile

fullc=$(mktemp -t fullc)

cat $cfile $1 > $fullc
mv $fullc $fullc.c

gcc -E $fullc.c | sed '/^\#/d' | grep -v "package "
echo ")"
