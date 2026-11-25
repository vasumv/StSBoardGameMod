#!/usr/bin/env bash
# x.sh <instance name, e.g. p1> shot <file> | click <x> <y> | key <keys> | type <text> | move <x> <y>
N=$1; shift; export DISPLAY=:$(cat "$(cd "$(dirname "$0")" && pwd)/$N/display")
case $1 in
 shot) nix shell nixpkgs#imagemagick -c import -window root "$2";;
 click) nix shell nixpkgs#xdotool -c xdotool mousemove $2 $3 sleep 0.15 mousedown 1 sleep 0.08 mouseup 1;;
 drag) nix shell nixpkgs#xdotool -c xdotool mousemove $2 $3 sleep 0.3 mousedown 1 sleep 0.3 mousemove $(( ($2+$4)/2 )) $(( ($3+$5)/2 )) sleep 0.3 mousemove $4 $5 sleep 0.6 mouseup 1;;
 move) nix shell nixpkgs#xdotool -c xdotool mousemove $2 $3;;
 key) nix shell nixpkgs#xdotool -c xdotool search --onlyvisible --name "Slay|Modded" windowfocus --sync key --delay 80 "${@:2}";;
 type) nix shell nixpkgs#xdotool -c xdotool search --onlyvisible --name "Slay|Modded" windowfocus --sync type --delay 60 "$2";;
esac
