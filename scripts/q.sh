#!/usr/bin/env bash
# q.sh <name> "<cmd>" [wait-seconds]: send a debug command to a sandbox instance and print the reply.
# Focuses the instance's window first: a game window without focus pauses itself.
B=$(cd "$(dirname "$0")" && pwd)
D=$B/$1/dbg; [ -f $B/$1/display ] && DISPLAY=:$(cat $B/$1/display) /nix/store/ya8bbq7j1rqmgpr94908q0h1m87305qw-xdotool-4.20260303.1/bin/xdotool search --onlyvisible --name "Modded" windowfocus >/dev/null 2>&1
rm -f $D/out; printf "%s\n" "$2" > $D/cmd
for i in $(seq 1 30); do [ -f $D/out ] && break; sleep 0.1; done
sleep ${3:-0.2}; grep -v "^>" $D/out 2>/dev/null
