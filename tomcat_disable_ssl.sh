#!/bin/bash

command=disable_ssl
tomcat_prop_file=$OKTA_HOME/thirdparty/tomcat/6.0.35.java8/shared/classes/env.properties
outfile="$tomcat_prop_file.bk"
tomcat_restart=1

while [[ $# -ge 1 ]]
do
key="$1"
echo $key
case $key in
    -e|--enable)
    command=enable_ssl
    ;;
    -n|--notomcatrestart)
	tomcat_restart=0
    ;;
    -in|--usestdin)
    tomcat_prop_file=
    ;;
    -out|--usestdout)
    outfile="$2"
    shift
    ;;
    *)
            # unknown option
    ;;
esac
shift # past argument or value
done

if [ "$command" == "enable_ssl" ]
	then
	echo "enabling tomcat for ssl" >&2
else
	echo "disabling ssl tomcat config. use -e to enable it" >&2
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

if [ "$tomcat_restart" == "1" ]
	then
	pushd $OKTA_HOME/okta-core
	ant smoke.tomcat >&2
	popd
fi

if [ "$outfile" == "" ]
	then
	$command
else
	$command > $outfile
	mv $outfile $tomcat_prop_file
fi

if [ "$tomcat_restart" == "1" ]
	then
	pushd $OKTA_HOME/okta-core
	ant start.tomcat.debug.async >&2
	popd
fi
