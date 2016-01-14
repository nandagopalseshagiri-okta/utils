#!/bin/bash

if [ "$1" == "" ]
	then
	echo "enter the host:port to pull the public key from."
	exit 1
fi

openssl s_client -showcerts -connect $1 < /dev/null \
	| awk -v certCount=1 \
	'BEGIN {stop = 1;}
	{
		if (certCount) 
    { 
      if (index($0, "BEGIN CERT")) { print $0; stop = 0;} 
      else if (index($0, "END CERT")) { print $0; stop = 1; --certCount; } 
      else if (!stop) { print $0; }}}' \
  | openssl x509 -pubkey -noout | grep -v "BEGIN\|END" | tr -d '\n'
  
