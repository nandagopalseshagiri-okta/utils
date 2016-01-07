#!/bin/bash

command=disable_ssl
if [ "$1" == "-e" ]
	then
	echo "enabling tomcat for ssl" >&2
	command=enable_ssl
else
	echo "disabling ssl tomcat config. use -e to enable it" >&2
fi

tomcat_prop_file=$OKTA_HOME/thirdparty/tomcat/6.0.35/shared/classes/env.properties

if [ "$2" == "stdin" ]
	then
	tomcat_prop_file=
fi

outfile="$tomcat_prop_file.bk"

if [ "$3" == "stdout" ]
	then
	outfile=
elif [ "$3" != "" ]
	then
	outfile="$3"
fi

disable_ssl() {	
	cat $tomcat_prop_file | awk \
	'{if (match($0,"^#.*$")) 
		print $0; 
	  else { 
	  	if (index($0,"serverProtocol") || index($0,"serverPort")) 
	  		printf "#"; 
	  	print $0 
	  }}'
}

serverPort="serverPort"
serverPortVal="=443"

serverProtocol="serverProtocol"
serverProtocolVal="=https"

enable_ssl() {
	cat $tomcat_prop_file | awk -v portKey=$serverPort -v portVal=\"$serverPortVal\" -v protoKey=$serverProtocol -v protoVal=\"$serverProtocolVal\" \
	'BEGIN { portAdded = 0; protocolAdded = 0; }
	{ if (!portAdded || !protocolAdded) {
	   port = 0; i = index($0, protoKey);
	   if (!i) {
	   	port = 1;
	   	i = index($0, portKey);
	   }
	   j = index($0,"#");
	   added = 0;
	   if (i && j && j < i) {
	    print substr($0, j + 1);
	    added = 1;
	   } else if (i) {
	   	print $0;
	   	added = 1;
	   } else {
	   	 print $0;
	   }
	   if (added) {
	   	if (port) {
	   		portAdded = 1;
	   	} else {
	   		protocolAdded = 1;
	   	}
	   }
	  }
	}
	END {
		if (!portAdded) {
			split(portVal, a, "\"");
			printf "%s", portKey; print a[2];
		}
		if (!protocolAdded) {
			split(protoVal, a, "\"");
			printf "%s", protoKey; print a[2];
		}
		if (!portAdded || !protocolAdded) {
			print "";
		}
	}'
}

ant smoke.tomcat >&2
if [ "$outfile" == "" ]
	then
	$command
else
	$command > $outfile
	mv $outfile $tomcat_prop_file
fi

ant start.tomcat.debug.async >&2
