#!/bin/bash
if [ "$EUID" -ne 0 ]
  then echo "Please run as root"
  exit
fi

echo "ADDING REPO"
echo "deb http://ftp.us.debian.org/debian/ stretch main" > /etc/apt/sources.list.d/unison.list
echo "UPDATING REPO"
apt-get update
echo "INSTALLING CORRECT VERSION"
apt-get install unison=2.48.3-1
echo "MARKING AS HOLD"
apt-mark hold unison
echo "MARKING AS HOLD"
echo "REMOVING REPO"
rm /etc/apt/sources.list.d/unison.list


