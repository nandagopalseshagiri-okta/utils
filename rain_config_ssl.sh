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

function pkg_loc() {
	if [ `uname` = "Linux" ]
		then
		echo $(yum_package_location $1)
	elif [ `uname` = "Darwin" ]
		then
		echo $(brew_package_location $1)
	else
		echo "Unsupported operating system"
		exit 1
	fi
}

function yum_package_location() {
	echo `rpm -ql $1`
}

function brew_package_location() {
	echo `brew info $1 | grep -m 1 \/usr | cut -d ' ' -f1`
}

function yum_package_installed() {
	package_installed=$(yum_package_location $1 | awk -F'is ' {'print $2'})
	if [ "$package_installed" = "not installed" ]; then echo false; else echo true; fi
}

function brew_package_installed() {
	package_installed=$(brew_package_location $1)
	if [ "$package_installed" = "" ]; then echo false; else echo true; fi
}

function install_package() {
	if [ `uname` = "Linux" ]
		then
		if [ "$(yum_package_installed $1)" = "false" ]
			then echo "Installing $1"; sudo yum install $1;
			if [ "$(yum_package_installed $1)" = "false" ]; then echo "$1 install failed"; exit 1; fi
		else
			echo "$1 is already installed"
		fi
	elif [ `uname` = "Darwin" ]
		then
		if [ "$(brew_package_installed $1)" = "false" ]
			then echo " Installing $1"; brew install $1;
			if [ "$(brew_package_installed $1)" = "false" ]; then echo "$1 install failed"; exit 1; fi
		else
			echo "$1 is already installed"
		fi
	else
		echo "Unsupported operating system"
		exit 1
	fi
}

packages="haproxy
pcre
dnsmasq"

for package in $packages
do
install_package $package
done

echo
echo

rm -r haproxy*

ha_proxy_config=$(mktemp -t ha_proxy_configXXXXXX)
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

haconfig_dir=$(mktemp -d -t haproxy_configXXXXX)
echo $haconfig_dir
mv $haconfig_dir .
haconfig_dir_name=$(basename "$haconfig_dir")

pushd $haconfig_dir_name
mv $ha_proxy_config .
unzip -q $2
cd STAR*
cat STAR_okta1_com.crt COMODORSADomainValidationSecureServerCA.crt COMODORSAAddTrustCA.crt AddTrustExternalCARoot.crt star.okta1.com.key > ../okta1.pem
popd

haproxy_loc=''
if [ `uname` = "Linux" ]; then haproxy_loc='/usr/sbin/haproxy';
else haproxy_loc="$(brew_package_location haproxy)/bin/haproxy"; fi

 ./tomcat_disable_ssl.sh -e

echo "******************************************************"
echo
echo "Now need sudo permission to run haproxy"
echo

cd $haconfig_dir_name
sudo $haproxy_loc -f ./$(basename "$ha_proxy_config")

if [ "$3" != "-config_push" ]
	then
	exit 0
fi