#!/bin/bash

wireless_ip=$(ifconfig en0 | grep "inet " | cut -d' ' -f2)
echo "wireless_ip = $wireless_ip"

temp_host_file=$(mktemp -t changed_etc_hosts)
cat /etc/hosts | grep -v "rain\(-admin\)\?.okta1.com" > $temp_host_file
echo $wireless_ip rain.okta1.com >> $temp_host_file
echo $wireless_ip rain-admin.okta1.com >> $temp_host_file

echo $temp_host_file

sudo rm /etc/hosts.old
sudo cp /etc/hosts /etc/hosts.old
sudo mv $temp_host_file /etc/hosts

# # change /etc/hosts to make rain.okta1.com and rain-admin.okta1.com point to the non-loopback IP
