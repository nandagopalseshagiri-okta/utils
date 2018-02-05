#!/bin/bash

if [ "$1" != "-cert" ]
	then
	echo "Expecting -cert and okta1 cert package path as argument."
	echo "--------------------------------------------------------"
	echo "If you do not have the certs for okta1.com. Go to https://oktawiki.atlassian.net/wiki/display/eng/How+to+configure+HTTPS+for+okta-core+on+okta1.com"
	echo "and download the cert and key package"
	exit 2
fi

if [ ! -e $2 ]
	then
	echo "$2 okta1 cert package path does not exist"
	exit 2
fi

LOGGING=

if [ "$3" == "-log" ]
   then
   LOGGING="option httplog
   log 127.0.0.1 local5 debug"

fi

function brew_package_location() {
	echo `brew info $1 | grep -m 1 \/usr | cut -d ' ' -f1`
}

packages="haproxy
pcre
dnsmasq"

#brew install the packages

for package in $packages
do
brew install $package
package_loc=$(brew_package_location $package)

if [ $? -ne 0 ]; then echo "$package install failed"; exit 1; fi
echo $package_loc
done

echo
echo 

pcre_loc=$(brew_package_location pcre)

ha_proxy_config=$(mktemp -t ha_proxy_config)
echo $ha_proxy_config is the ha ha_proxy_config

cat > $ha_proxy_config << EOF 
frontend www-http
   bind 0.0.0.0:80 ssl crt okta1.pem
   default_backend www-backend
   $LOGGING
frontend www-https
   bind 0.0.0.0:443 ssl crt okta1.pem
   reqadd X-Forwarded-Proto:\ https
   default_backend www-backend
   $LOGGING
backend www-backend
   server www-1 127.0.0.1:1802
EOF

haconfig_dir=$(mktemp -d -t haproxy_config)
echo $haconfig_dir
mv $haconfig_dir .
haconfig_dir_name=$(basename "$haconfig_dir")

pushd $haconfig_dir_name
mv $ha_proxy_config .
unzip -q $2 -d STAR_extracted
cd STAR_extracted
cat STAR_okta1_com.crt star.okta1.com.chain star.okta1.com.key > ../okta1.pem
popd

haproxy_loc=$(brew_package_location haproxy)

 ./tomcat_disable_ssl.sh -e

echo "******************************************************"
echo
echo "Now need sudo permission to run haproxy"
echo

if [ ! -e /usr/local/lib/libpcre.1.dylib ]
	then
	echo /usr/local/lib/libpcre.1.dylib does not exist. Will link to $pcre_loc/lib/libpcre.1.dylib
	sudo ln -s $pcre_loc/lib/libpcre.1.dylib /usr/local/lib/libpcre.1.dylib
fi

cd $haconfig_dir_name
sudo $haproxy_loc/bin/haproxy -f ./$(basename "$ha_proxy_config")

if [ "$3" != "-config_push" ]
	then
	exit 0
fi

