#!/bin/bash

for_each() {
	local name=$1[@]
	local items=("${!name}")
	for i in ${items[@]}
	do
		$2 $i
	done
}

#dry_run="echo"
take_owner_ship_from_root="$dry_run sudo chown $(whoami)"
return_owner_ship_to_root="$dry_run sudo chown root"
brew_link="$dry_run brew link"

items_to_own=("/usr/local/bin"
"/usr/local/share"
"/usr/local/share/man/man1"
"/usr/local/share/man/man3")

package=$1
shift

while [ "$1" ]
do
    items_to_own+=($1)
    shift
done

for_each items_to_own "$take_owner_ship_from_root"
$brew_link $package
for_each items_to_own "$return_owner_ship_to_root"
