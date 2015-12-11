#!/bin/bash

function brew_package_location() {
	echo `brew info $1 | grep -m 1 \/usr | cut -d ' ' -f1`
}

wireless_ip=$(ifconfig en0 | grep "inet " | cut -d' ' -f2)
echo "wireless_ip = $wireless_ip"

temp_host_file=$(mktemp -t changed_etc_hosts)
cat /etc/hosts | grep -v "rain\(-admin\)\?.okta1.com" > $temp_host_file
echo $wireless_ip rain.okta1.com >> $temp_host_file
echo $wireless_ip rain-admin.okta1.com >> $temp_host_file

echo $temp_host_file

dnsmasq_pid=$(sudo ps -A | grep [d]nsmasq | cut -d ' ' -f1)
if [  "$dnsmasq_pid" != "" ]
	then
	echo "dnsmasq is running. pid = $dnsmasq_pid. Stopping it first"
	sudo kill -9 $dnsmasq_pid
fi

sudo rm /etc/hosts.old 2> /dev/null > /dev/null
sudo cp /etc/hosts /etc/hosts.old
sudo mv $temp_host_file /etc/hosts
sudo chown root /etc/hosts
sudo chmod 0644 /etc/hosts

dnsmasq_log=$(mktemp -t dnsmasq_log)
touch $dnsmasq_log
echo "dnsmasq loggin into file $dnsmasq_log"

dnsmasq_loc=$(brew_package_location dnsmasq)
sudo $dnsmasq_loc/sbin/dnsmasq --cache-size=0 -q --log-facility=$dnsmasq_log

echo "Sleeping for few minutes to allow dnsmasq to pick up host file change before reverting to old ..."
sleep 3

sudo mv /etc/hosts.old /etc/hosts

# # change /etc/hosts to make rain.okta1.com and rain-admin.okta1.com point to the non-loopback IP
